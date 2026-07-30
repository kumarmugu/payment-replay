package com.payment.replay.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for a single mask field definition.
 * Loaded from mask-fields.yaml, this defines which XML path should be masked
 * and which strategy/parameters to apply.
 */
public final class MaskFieldConfig {

    private final String path;
    private final String strategy;
    private final Map<String, String> parameters;

    public MaskFieldConfig(String path, String strategy, Map<String, String> parameters) {
        this.path = path;
        this.strategy = strategy;
        this.parameters = parameters != null
                ? Collections.unmodifiableMap(new HashMap<>(parameters))
                : Collections.<String, String>emptyMap();
    }

    /**
     * XPath-like path to the XML element to mask.
     * Example: "DbtrAcct/Id/Othr/Id"
     */
    public String getPath() {
        return path;
    }

    /**
     * Masking strategy name: FULL_MASK, KEEP_FIRST_N, KEEP_LAST_N, CUSTOM_PATTERN
     */
    public String getStrategy() {
        return strategy;
    }

    /**
     * Strategy-specific parameters (n, maskChar, pattern, replacement).
     */
    public Map<String, String> getParameters() {
        return parameters;
    }

    /**
     * Gets the number of characters to keep, defaulting to 4 if not specified.
     */
    public int getN() {
        String n = parameters.get("n");
        return n != null ? Integer.parseInt(n) : 4;
    }

    /**
     * Gets the mask character, defaulting to '*' if not specified.
     */
    public char getMaskChar() {
        String maskChar = parameters.get("maskChar");
        return (maskChar != null && !maskChar.isEmpty()) ? maskChar.charAt(0) : '*';
    }

    /**
     * Gets the custom regex pattern for CUSTOM_PATTERN strategy.
     */
    public String getPattern() {
        return parameters.get("pattern");
    }

    /**
     * Gets the replacement string for CUSTOM_PATTERN strategy.
     */
    public String getReplacement() {
        return parameters.get("replacement");
    }

    /**
     * Returns the last element in the path, which is the actual XML element name.
     * Example: "DbtrAcct/Id/Othr/Id" returns "Id"
     */
    public String getElementName() {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Returns path segments for matching nested XML structures.
     */
    public String[] getPathSegments() {
        if (path == null || path.isEmpty()) {
            return new String[0];
        }
        return path.split("/");
    }

    @Override
    public String toString() {
        return "MaskFieldConfig{path='" + path + "', strategy='" + strategy + "', params=" + parameters + '}';
    }
}
