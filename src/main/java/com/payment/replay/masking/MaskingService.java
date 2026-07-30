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
     * Uses path-based matching to find the target element.
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

        // Use iterative approach to find and mask all occurrences
        StringBuilder result = new StringBuilder(xml.length());
        int pos = 0;

        while (pos < xml.length()) {
            int matchStart = findPathMatch(xml, pos, pathSegments);

            if (matchStart < 0) {
                // No more matches, append rest of XML
                result.append(xml, pos, xml.length());
                break;
            }

            // Append everything before the match
            result.append(xml, pos, matchStart);

            // Find the innermost element (last segment) content
            String targetElement = pathSegments[pathSegments.length - 1];
            int contentStart = findElementContentStart(xml, matchStart, targetElement);

            if (contentStart < 0) {
                // Could not find element content, append as-is and move on
                result.append(xml.charAt(matchStart));
                pos = matchStart + 1;
                continue;
            }

            int contentEnd = findElementContentEnd(xml, contentStart, targetElement);

            if (contentEnd < 0) {
                // Malformed XML, skip this match
                result.append(xml, matchStart, contentStart);
                pos = contentStart;
                continue;
            }

            // Extract original value and apply masking
            String originalValue = xml.substring(contentStart, contentEnd);

            // Only mask text content (skip if it contains child elements)
            if (!originalValue.contains("<")) {
                String maskedValue = strategy.mask(originalValue.trim(), fieldConfig);
                // Append up to content start, then masked value, then continue after content
                result.append(xml, matchStart, contentStart);
                result.append(maskedValue);
                pos = contentEnd;
            } else {
                // Content has nested elements, don't mask at this level
                result.append(xml, matchStart, contentStart);
                pos = contentStart;
            }
        }

        return result.toString();
    }

    /**
     * Finds the start position of a path match in the XML.
     * Looks for the opening tag of the first path segment, then validates
     * that subsequent segments are nested within.
     *
     * @param xml          XML string to search
     * @param startPos     position to start searching from
     * @param pathSegments ordered path segments to match
     * @return position of the outermost matching element's opening '<', or -1
     */
    private int findPathMatch(String xml, int startPos, String[] pathSegments) {
        if (pathSegments.length == 0) {
            return -1;
        }

        // For single-segment paths, just find the element
        if (pathSegments.length == 1) {
            return findOpeningTag(xml, startPos, pathSegments[0]);
        }

        // For multi-segment paths, find parent context first
        int searchFrom = startPos;

        while (searchFrom < xml.length()) {
            // Find the first segment
            int firstSegmentPos = findOpeningTag(xml, searchFrom, pathSegments[0]);
            if (firstSegmentPos < 0) {
                return -1;
            }

            // Verify remaining segments are nested inside
            int closingTagEnd = findClosingTagEnd(xml, firstSegmentPos, pathSegments[0]);
            if (closingTagEnd < 0) {
                searchFrom = firstSegmentPos + 1;
                continue;
            }

            // Extract content between first segment tags
            int firstContentStart = findElementContentStart(xml, firstSegmentPos, pathSegments[0]);
            if (firstContentStart < 0) {
                searchFrom = firstSegmentPos + 1;
                continue;
            }

            String parentContent = xml.substring(firstContentStart, closingTagEnd);

            // Check if remaining path segments exist within this parent
            if (validateNestedPath(parentContent, pathSegments, 1)) {
                return firstSegmentPos;
            }

            searchFrom = firstSegmentPos + 1;
        }

        return -1;
    }

    /**
     * Validates that path segments starting at index are nested within the content.
     */
    private boolean validateNestedPath(String content, String[] segments, int fromIndex) {
        if (fromIndex >= segments.length) {
            return true;
        }

        String segment = segments[fromIndex];
        int tagPos = findOpeningTag(content, 0, segment);

        if (tagPos < 0) {
            return false;
        }

        if (fromIndex == segments.length - 1) {
            return true;
        }

        // Look inside this element for the next segment
        int contentStart = findElementContentStart(content, tagPos, segment);
        if (contentStart < 0) {
            return false;
        }

        int closingEnd = findClosingTagEnd(content, tagPos, segment);
        if (closingEnd < 0) {
            return false;
        }

        String nestedContent = content.substring(contentStart, closingEnd);
        return validateNestedPath(nestedContent, segments, fromIndex + 1);
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
