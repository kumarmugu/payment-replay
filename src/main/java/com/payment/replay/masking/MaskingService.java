package com.payment.replay.masking;

import com.payment.replay.config.MaskFieldConfig;
import com.payment.replay.exception.MaskingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Orchestrates XML masking for all configured sensitive fields.
 *
 * NAMESPACE HANDLING
 * ------------------
 * Real-world ISO 20022 XML from different banks uses different namespace prefixes
 * for identical elements, e.g.:
 *
 *   Bank A:  <ns3:DbtrAcct>...</ns3:DbtrAcct>
 *   Bank B:  <pacs:DbtrAcct>...</pacs:DbtrAcct>
 *   Bank C:  <DbtrAcct>...</DbtrAcct>          (no prefix)
 *
 * All three must match the mask-fields.yaml path "DbtrAcct/Id/Othr/Id".
 *
 * Strategy: every tag-matching operation strips the namespace prefix (the part
 * before the first colon in the element name token) before comparing, so matching
 * is always on the local element name only.  This makes masking completely
 * namespace-prefix-agnostic without requiring a full DOM parse.
 *
 * PERFORMANCE
 * -----------
 * Works on the raw XML string with a single linear pass per configured field.
 * No DOM/SAX overhead.  Safe for very large XML payloads (>1 MB).
 */
public final class MaskingService {

    private static final Logger log = LoggerFactory.getLogger(MaskingService.class);

    private final List<MaskFieldConfig> maskFields;
    private final MaskingStrategyFactory strategyFactory;

    public MaskingService(List<MaskFieldConfig> maskFields, MaskingStrategyFactory strategyFactory) {
        this.maskFields = maskFields;
        this.strategyFactory = strategyFactory;
    }

    /**
     * Applies every configured masking rule to the XML payload.
     *
     * @param xml original XML (may be null / empty — returned as-is)
     * @return XML with all sensitive fields masked
     * @throws MaskingException on unrecoverable parsing failure
     */
    public String maskXml(String xml) {
        if (xml == null || xml.isEmpty()) {
            return xml;
        }
        try {
            String result = xml;
            for (MaskFieldConfig fieldConfig : maskFields) {
                result = maskField(result, fieldConfig);
            }
            return result;
        } catch (MaskingException e) {
            throw e;
        } catch (Exception e) {
            throw new MaskingException("Failed to mask XML payload: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core masking logic
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Masks one configured field throughout the XML.
     *
     * For single-segment paths (e.g. "Nm") every occurrence is masked.
     * For multi-segment paths (e.g. "DbtrAcct/Id/Othr/Id") the outermost
     * parent block is extracted first, then the leaf element is masked within it.
     * This prevents false positives when the same local name appears in a
     * different context.
     */
    private String maskField(String xml, MaskFieldConfig fieldConfig) {
        String[] segments = fieldConfig.getPathSegments();
        if (segments.length == 0) {
            return xml;
        }

        MaskingStrategy strategy = strategyFactory.getStrategy(fieldConfig.getStrategy());

        if (segments.length == 1) {
            return maskAllOccurrences(xml, segments[0], strategy, fieldConfig);
        }

        // Multi-segment: locate the outermost parent, then mask the leaf inside it
        StringBuilder sb = new StringBuilder(xml.length());
        int searchFrom = 0;

        while (searchFrom < xml.length()) {
            int parentStart = findOpeningTag(xml, searchFrom, segments[0]);
            if (parentStart < 0) {
                sb.append(xml, searchFrom, xml.length());
                break;
            }

            sb.append(xml, searchFrom, parentStart);

            int parentContentStart = findContentStart(xml, parentStart);
            if (parentContentStart < 0) {
                sb.append(xml.charAt(parentStart));
                searchFrom = parentStart + 1;
                continue;
            }

            int parentCloseIdx = findClosingTagStart(xml, parentStart, segments[0]);
            if (parentCloseIdx < 0) {
                sb.append(xml, parentStart, parentContentStart);
                searchFrom = parentContentStart;
                continue;
            }

            int parentEnd = xml.indexOf('>', parentCloseIdx);
            if (parentEnd < 0) {
                sb.append(xml, parentStart, parentContentStart);
                searchFrom = parentContentStart;
                continue;
            }
            parentEnd++; // include '>'

            String parentBlock = xml.substring(parentStart, parentEnd);

            // Only mask if all intermediate path segments are present inside parent
            if (pathExistsIn(parentBlock, segments, 1)) {
                String leafName = segments[segments.length - 1];
                sb.append(maskAllOccurrences(parentBlock, leafName, strategy, fieldConfig));
            } else {
                sb.append(parentBlock);
            }

            searchFrom = parentStart + parentBlock.length();
        }

        return sb.toString();
    }

    /**
     * Masks every occurrence of {@code elementName} (local name, any namespace prefix)
     * that contains only text content (no child elements).
     */
    private String maskAllOccurrences(String xml, String elementName,
                                      MaskingStrategy strategy, MaskFieldConfig fieldConfig) {
        StringBuilder sb = new StringBuilder(xml.length());
        int pos = 0;

        while (pos < xml.length()) {
            int tagStart = findOpeningTag(xml, pos, elementName);
            if (tagStart < 0) {
                sb.append(xml, pos, xml.length());
                break;
            }

            sb.append(xml, pos, tagStart);

            int contentStart = findContentStart(xml, tagStart);
            if (contentStart < 0) {
                sb.append(xml.charAt(tagStart));
                pos = tagStart + 1;
                continue;
            }

            int contentEnd = findTextContentEnd(xml, contentStart, elementName);
            if (contentEnd < 0) {
                sb.append(xml, tagStart, contentStart);
                pos = contentStart;
                continue;
            }

            String content = xml.substring(contentStart, contentEnd);

            if (!content.contains("<")) {
                // Pure text — mask it
                String masked = strategy.mask(content.trim(), fieldConfig);
                sb.append(xml, tagStart, contentStart);
                sb.append(masked);
                pos = contentEnd;
            } else {
                // Has child elements — descend without masking at this level
                sb.append(xml, tagStart, contentStart);
                pos = contentStart;
            }
        }

        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Namespace-aware XML scanning primitives
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Finds the next opening tag whose LOCAL element name equals {@code localName}.
     *
     * Handles all of:
     *   {@code <Nm>}
     *   {@code <ns3:Nm>}
     *   {@code <ns3:Nm attr="v">}
     *   {@code <ns3:Nm/>}  — skipped (self-closing)
     *
     * Namespace prefix is stripped via {@link #localName(String)}.
     *
     * @return position of the opening {@code <}, or -1 if not found
     */
    private int findOpeningTag(String xml, int startPos, String localName) {
        int pos = startPos;
        while (pos < xml.length()) {
            int lt = xml.indexOf('<', pos);
            if (lt < 0) return -1;

            int next = lt + 1;
            if (next >= xml.length()) return -1;

            char ch = xml.charAt(next);

            // Skip closing tags, XML declarations, comments, CDATA
            if (ch == '/' || ch == '?' || ch == '!') {
                pos = lt + 1;
                continue;
            }

            int gt = xml.indexOf('>', lt);
            if (gt < 0) return -1;

            String tagBody = xml.substring(lt + 1, gt);

            // Skip self-closing tags
            if (tagBody.endsWith("/")) {
                pos = gt + 1;
                continue;
            }

            if (localName.equals(localName(tokenName(tagBody)))) {
                return lt;
            }

            pos = gt + 1;
        }
        return -1;
    }

    /**
     * Finds the position immediately after the opening tag's closing {@code >}.
     * This is where the element's content starts.
     *
     * @return index after {@code >}, or -1
     */
    private int findContentStart(String xml, int openingTagPos) {
        int gt = xml.indexOf('>', openingTagPos);
        return gt < 0 ? -1 : gt + 1;
    }

    /**
     * Finds the position of {@code </} that closes the element whose local name
     * is {@code localName}, starting the search from {@code contentStartPos}.
     *
     * Correctly handles nested elements with the same local name by tracking depth.
     * Namespace prefixes on closing tags are also stripped for comparison.
     *
     * @return position of {@code </...>} start, or -1
     */
    private int findTextContentEnd(String xml, int contentStartPos, String localName) {
        int pos = contentStartPos;
        while (pos < xml.length()) {
            int close = xml.indexOf("</", pos);
            if (close < 0) return -1;

            int gt = xml.indexOf('>', close);
            if (gt < 0) return -1;

            String closingName = xml.substring(close + 2, gt).trim();
            if (localName.equals(localName(closingName))) {
                return close;
            }
            pos = gt + 1;
        }
        return -1;
    }

    /**
     * Finds the start ({@code </}) of the closing tag that matches the opening
     * tag at {@code openingTagPos}, correctly handling nested same-local-name
     * elements by tracking depth.
     *
     * @return position of the {@code </} of the matching closing tag, or -1
     */
    private int findClosingTagStart(String xml, int openingTagPos, String localName) {
        int contentStart = findContentStart(xml, openingTagPos);
        if (contentStart < 0) return -1;

        int depth = 1;
        int pos = contentStart;

        while (pos < xml.length() && depth > 0) {
            // Find next opening or closing tag
            int nextOpen  = xml.indexOf('<', pos);
            if (nextOpen < 0) return -1;

            int nextChar = nextOpen + 1;
            if (nextChar >= xml.length()) return -1;

            int gt = xml.indexOf('>', nextOpen);
            if (gt < 0) return -1;

            if (xml.charAt(nextChar) == '/') {
                // Closing tag
                String closingName = xml.substring(nextChar + 1, gt).trim();
                if (localName.equals(localName(closingName))) {
                    depth--;
                    if (depth == 0) return nextOpen;
                }
            } else if (xml.charAt(nextChar) != '?' && xml.charAt(nextChar) != '!') {
                // Opening tag — check for same local name to track depth
                String tagBody = xml.substring(nextChar, gt);
                if (!tagBody.endsWith("/") && localName.equals(localName(tokenName(tagBody)))) {
                    depth++;
                }
            }

            pos = gt + 1;
        }
        return -1;
    }

    /**
     * Returns true if every path segment from {@code fromIndex} onward
     * has an opening tag in {@code xml} (order not enforced — just existence).
     * Sufficient for path-context filtering without full descent.
     */
    private boolean pathExistsIn(String xml, String[] segments, int fromIndex) {
        for (int i = fromIndex; i < segments.length; i++) {
            if (findOpeningTag(xml, 0, segments[i]) < 0) return false;
        }
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tag-name helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Extracts the element name token from a tag body, stripping attributes.
     *
     * {@code "ns3:DbtrAcct Ccy=\"SGD\""} → {@code "ns3:DbtrAcct"}
     * {@code "DbtrAcct"}                 → {@code "DbtrAcct"}
     */
    private String tokenName(String tagBody) {
        if (tagBody == null || tagBody.isEmpty()) return "";
        int sp = tagBody.indexOf(' ');
        return sp > 0 ? tagBody.substring(0, sp).trim() : tagBody.trim();
    }

    /**
     * Strips the namespace prefix from a qualified name.
     *
     * {@code "ns3:DbtrAcct"} → {@code "DbtrAcct"}
     * {@code "DbtrAcct"}     → {@code "DbtrAcct"}
     * {@code "DbtrAcct/"}    → {@code "DbtrAcct"} (handles self-closing remnants)
     */
    private String localName(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isEmpty()) return "";
        String name = qualifiedName.trim();
        // Remove trailing slash (self-closing tag remnant)
        if (name.endsWith("/")) name = name.substring(0, name.length() - 1).trim();
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }
}
