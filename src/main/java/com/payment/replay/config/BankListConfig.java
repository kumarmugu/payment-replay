package com.payment.replay.config;

/**
 * Configuration for a bank in the allowed bank list.
 * Only records from banks in this list will be processed.
 * Simplified to BIC-only — no name/country needed.
 */
public final class BankListConfig {

    private final String bic;

    public BankListConfig(String bic) {
        this.bic = bic;
    }

    /** Bank BIC code used for filtering log records. */
    public String getBic() {
        return bic;
    }

    @Override
    public String toString() {
        return "BankListConfig{bic='" + bic + "'}";
    }
}
