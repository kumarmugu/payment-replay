package com.payment.replay.masking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating and caching masking strategy instances.
 * Strategies are registered by name and retrieved on demand.
 *
 * To add a new strategy:
 * 1. Implement MaskingStrategy interface
 * 2. Register it in this factory via registerStrategy() or add to the default set
 */
public final class MaskingStrategyFactory {

    private static final Logger log = LoggerFactory.getLogger(MaskingStrategyFactory.class);

    private final Map<String, MaskingStrategy> strategies = new HashMap<>();

    public MaskingStrategyFactory() {
        // Register all built-in strategies
        registerStrategy(new FullMaskStrategy());
        registerStrategy(new KeepFirstNStrategy());
        registerStrategy(new KeepLastNStrategy());
        registerStrategy(new CustomPatternStrategy());

        // Also register common aliases
        strategies.put("KEEP_LAST_4", new KeepLastNStrategy());
        strategies.put("KEEP_FIRST_4", new KeepFirstNStrategy());

        log.debug("Masking strategy factory initialized with {} strategies", strategies.size());
    }

    /**
     * Registers a masking strategy. If a strategy with the same name exists, it is replaced.
     *
     * @param strategy the strategy to register
     */
    public void registerStrategy(MaskingStrategy strategy) {
        strategies.put(strategy.getStrategyName(), strategy);
        log.trace("Registered masking strategy: {}", strategy.getStrategyName());
    }

    /**
     * Retrieves a masking strategy by name.
     *
     * @param strategyName the strategy identifier (e.g., "FULL_MASK", "KEEP_LAST_N")
     * @return the matching strategy
     * @throws IllegalArgumentException if no strategy is registered for the given name
     */
    public MaskingStrategy getStrategy(String strategyName) {
        if (strategyName == null || strategyName.isEmpty()) {
            throw new IllegalArgumentException("Strategy name cannot be null or empty");
        }

        // Normalize: handle variants like "KEEP_LAST_4" -> "KEEP_LAST_N"
        String normalized = normalizeStrategyName(strategyName);

        MaskingStrategy strategy = strategies.get(normalized);
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown masking strategy: " + strategyName
                    + ". Available strategies: " + strategies.keySet());
        }
        return strategy;
    }

    /**
     * Checks if a strategy name is registered (either directly or via normalization).
     *
     * @param strategyName strategy name to check
     * @return true if the strategy exists
     */
    public boolean hasStrategy(String strategyName) {
        if (strategyName == null) {
            return false;
        }
        String normalized = normalizeStrategyName(strategyName);
        return strategies.containsKey(normalized);
    }

    /**
     * Normalizes strategy names to handle common variants.
     * Example: "KEEP_LAST_4" -> "KEEP_LAST_N"
     */
    private String normalizeStrategyName(String name) {
        // First check for exact match
        if (strategies.containsKey(name)) {
            return name;
        }

        // Try normalizing numbered variants
        if (name.startsWith("KEEP_LAST_")) {
            return KeepLastNStrategy.NAME;
        }
        if (name.startsWith("KEEP_FIRST_")) {
            return KeepFirstNStrategy.NAME;
        }

        return name;
    }
}
