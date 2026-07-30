package com.payment.replay.config;

/**
 * Configuration for mapping a production BIC to its UAT equivalent.
 * Loaded from bank-mapping.yaml.
 */
public final class BankMappingConfig {

    private final String productionBic;
    private final String uatBic;

    public BankMappingConfig(String productionBic, String uatBic) {
        this.productionBic = productionBic;
        this.uatBic = uatBic;
    }

    /**
     * The production BIC code as found in production logs.
     */
    public String getProductionBic() {
        return productionBic;
    }

    /**
     * The corresponding UAT BIC code for sanitized replay.
     */
    public String getUatBic() {
        return uatBic;
    }

    @Override
    public String toString() {
        return "BankMappingConfig{" + productionBic + " -> " + uatBic + '}';
    }
}
