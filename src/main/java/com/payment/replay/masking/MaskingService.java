package com.payment.replay.masking;

import com.payment.replay.config.MaskFieldConfig;
import com.payment.replay.exception.MaskingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Orchestrates XML masking by applying configured masking strategies to sensitive fields.
 *
 * Uses a lightweight streaming XML approach that processes the XML character by character,
 * identifying elements by their path context and applying the appropriate masking strategy
 * when a configured field is found.
 *
 * This approach avoids full DOM parsing for performance and handles arbitrary namespaces
 * by matching on local element names only (namespace-agnostic matching).
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
     * Applies masking to all configured sensitive fields in the XML payload.
     *
     * Processing approach:
     * 1. Track XML element path using a stack
     * 2. For each element, check if current path matches any configured mask field
     * 3. If match found, apply the configured masking strategy to the element's text content
     *
     * @param xml original XML payload
     * @return XML with sensitive fields masked
     * @throws MaskingException if XML cannot be processed
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

    /**
     * Masks a specific field within the XML based on the field configuration.
     * Finds all occurrences of the leaf element that are nested within the parent path
     * and applies the masking strategy to their text content.
     *
     * @param xml         current XML string
     * @param fieldConfig field to mask
     * @return XML with the specified field masked
     */
    private String maskField(String xml, MaskFieldConfig fieldConfig) {
        String[] pathSegments = fieldConfig.getPathSegments();
        if (pathSegments.length == 0) {
            return xml;
        }

        MaskingStrategy strategy = strategyFactory.getStrategy(fieldConfig.getStrategy());

        // For single segment paths, find all occurrences of the element and mask
        if (pathSegments.length == 1) {
            return maskAllOccurrences(xml, pathSegments[0], strategy, fieldConfig);
        }

        // For multi-segment paths, find the outermost parent context first,
        // then mask the leaf element within that context
        String result = xml;
        String firstSegment = pathSegments[0];
        int searchFrom = 0;

        StringBuilder sb = new StringBuilder(result.length());

        while (searchFrom < result.length()) {
            int parentStart = findOpeningTag(result, searchFrom, firstSegment);
            if (parentStart < 0) {
                sb.append(result, searchFrom, result.length());
                break;
            }

            // Append everything before this parent
            sb.append(result, searchFrom, parentStart);

            // Find closing tag of parent
            int parentContentStart = findElementContentStart(result, parentStart, firstSegment);
            if (parentContentStart < 0) {
                sb.append(result.charAt(parentStart));
                searchFrom = parentStart + 1;
                continue;
            }

            int parentCloseStart = findClosingTagEnd(result, parentStart, firstSegment);
            if (parentCloseStart < 0) {
                sb.append(result, parentStart, parentContentStart);
                searchFrom = parentContentStart;
                continue;
            }

            // Find the actual closing tag > position
            int parentEnd = result.indexOf('>', parentCloseStart);
            if (parentEnd < 0) {
                sb.append(result, parentStart, parentContentStart);
                searchFrom = parentContentStart;
                continue;
            }
            parentEnd++; // include the '>'

            // Extract full parent block
            String parentBlock = result.substring(parentStart, parentEnd);

            // Check if this block contains all intermediate path segments
            if (containsPath(parentBlock, pathSegments, 1)) {
                // Mask the leaf element within this block
                String leafElement = pathSegments[pathSegments.length - 1];
                String maskedBlock = maskAllOccurrences(parentBlock, leafElement, strategy, fieldConfig);
                sb.append(maskedBlock);
            } else {
                sb.append(parentBlock);
            }

            searchFrom = parentStart + parentBlock.length();
        }

        return sb.toString();
    }

    /**
     * Checks if an XML fragment contains all path segments in order (from index onwards).
     */
    private boolean containsPath(String xml, String[] segments, int fromIndex) {
        for (int i = fromIndex; i < segments.length; i++) {
            if (findOpeningTag(xml, 0, segments[i]) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Masks all occurrences of a specific element in the given XML string.
     */
    private String maskAllOccurrences(String xml, String elementName, MaskingStrategy strategy, MaskFieldConfig fieldConfig) {
        StringBuilder sb = new StringBuilder(xml.length());
        int pos = 0;

        while (pos < xml.length()) {
            int tagStart = findOpeningTag(xml, pos, elementName);
            if (tagStart < 0) {
                sb.append(xml, pos, xml.length());
                break;
            }

            sb.append(xml, pos, tagStart);

            int contentStart = findElementContentStart(xml, tagStart, elementName);
            if (contentStart < 0) {
                sb.append(xml.charAt(tagStart));
                pos = tagStart + 1;
                continue;
            }

            int contentEnd = findElementContentEnd(xml, contentStart, elementName);
            if (contentEnd < 0) {
                sb.append(xml, tagStart, contentStart);
                pos = contentStart;
                continue;
            }

            String content = xml.substring(contentStart, contentEnd);

            // Only mask text content (no child elements)
            if (!content.contains("<")) {
                String masked = strategy.mask(content.trim(), fieldConfig);
                // Append opening tag + masked content
                sb.append(xml, tagStart, contentStart);
                sb.append(masked);
                pos = contentEnd;
            } else {
                // Has children, append opening tag and continue inside
                sb.append(xml, tagStart, contentStart);
                pos = contentStart;
            }
        }

        return sb.toString();
    }

    /**
     * Finds the position of an opening tag for the given element name.
     * Handles namespace prefixes by matching on local name.
     *
     * Matches: <ElementName>, <ns:ElementName>, <ns:ElementName attr="val">
     */
    private int findOpeningTag(String xml, int startPos, String elementName) {
        int pos = startPos;

        while (pos < xml.length()) {
            int tagStart = xml.indexOf('<', pos);
            if (tagStart < 0) {
                return -1;
            }

            // Skip closing tags and processing instructions
            if (tagStart + 1 < xml.length() && (xml.charAt(tagStart + 1) == '/'
                    || xml.charAt(tagStart + 1) == '?' || xml.charAt(tagStart + 1) == '!')) {
                pos = tagStart + 1;
                continue;
            }

            // Find end of tag
            int tagEnd = xml.indexOf('>', tagStart);
            if (tagEnd < 0) {
                return -1;
            }

            String tagContent = xml.substring(tagStart + 1, tagEnd);
            // Remove self-closing slash if present
            if (tagContent.endsWith("/")) {
                pos = tagEnd + 1;
                continue;
            }

            // Extract element name (handle namespace prefix and attributes)
            String localName = extractLocalName(tagContent);

            if (elementName.equals(localName)) {
                return tagStart;
            }

            pos = tagEnd + 1;
        }

        return -1;
    }

    /**
     * Finds the position immediately after the opening tag's '>' for element content.
     */
    private int findElementContentStart(String xml, int tagStartPos, String elementName) {
        int tagEnd = xml.indexOf('>', tagStartPos);
        if (tagEnd < 0) {
            return -1;
        }
        return tagEnd + 1;
    }

    /**
     * Finds the start position of the closing tag for the given element.
     * This is the position of the text content end.
     */
    private int findElementContentEnd(String xml, int contentStartPos, String elementName) {
        // Look for closing tag: </elementName> or </ns:elementName>
        int pos = contentStartPos;

        while (pos < xml.length()) {
            int closingStart = xml.indexOf("</", pos);
            if (closingStart < 0) {
                return -1;
            }

            int closingEnd = xml.indexOf('>', closingStart);
            if (closingEnd < 0) {
                return -1;
            }

            String closingContent = xml.substring(closingStart + 2, closingEnd).trim();
            String localName = extractLocalName(closingContent);

            if (elementName.equals(localName)) {
                return closingStart;
            }

            pos = closingEnd + 1;
        }

        return -1;
    }

    /**
     * Finds the position after the closing tag end for a given element.
     */
    private int findClosingTagEnd(String xml, int openingTagPos, String elementName) {
        int contentStart = findElementContentStart(xml, openingTagPos, elementName);
        if (contentStart < 0) {
            return -1;
        }

        // Need to handle nested same-name elements
        int depth = 1;
        int pos = contentStart;

        while (pos < xml.length() && depth > 0) {
            int nextOpen = findOpeningTag(xml, pos, elementName);
            int nextClose = xml.indexOf("</", pos);

            if (nextClose < 0) {
                return -1;
            }

            // Check if we found the closing tag for our element
            int closeEnd = xml.indexOf('>', nextClose);
            if (closeEnd < 0) {
                return -1;
            }

            String closingContent = xml.substring(nextClose + 2, closeEnd).trim();
            String localName = extractLocalName(closingContent);

            if (nextOpen >= 0 && nextOpen < nextClose) {
                // Found nested opening tag first
                depth++;
                int nestedTagEnd = xml.indexOf('>', nextOpen);
                pos = nestedTagEnd + 1;
            } else if (elementName.equals(localName)) {
                depth--;
                if (depth == 0) {
                    return nextClose;
                }
                pos = closeEnd + 1;
            } else {
                pos = closeEnd + 1;
            }
        }

        return -1;
    }

    /**
     * Extracts the local element name from tag content, stripping namespace prefix and attributes.
     *
     * Examples:
     *   "ns:ElementName attr='val'" -> "ElementName"
     *   "ElementName" -> "ElementName"
     *   "ns:ElementName" -> "ElementName"
     */
    private String extractLocalName(String tagContent) {
        String name = tagContent.trim();

        // Remove attributes (split at first space)
        int spacePos = name.indexOf(' ');
        if (spacePos > 0) {
            name = name.substring(0, spacePos);
        }

        // Remove namespace prefix
        int colonPos = name.indexOf(':');
        if (colonPos > 0) {
            name = name.substring(colonPos + 1);
        }

        return name;
    }
}
