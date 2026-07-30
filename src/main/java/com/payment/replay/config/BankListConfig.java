package com.payment.replay.config;

/**
 * Configuration for a bank in the allowed bank list.
 * Only records from banks in this list will be processed by the filter-mask command.
 */
public final class BankListConfig {

    private final String bic;
    private final String name;
    private final String country;

    public BankListConfig(String bic, String name, String country) {
        this.bic = bic;
        this.name = name;
        this.country = country;
    }

    /**
     * Bank BIC code used for filtering log records.
     */
    public String getBic() {
        return bic;
    }

    /**
     * Human-readable bank name.
     */
    public String getName() {
        return name;
    }

    /**
     * Country code of the bank.
     */
    public String getCountry() {
        return country;
    }

    @Override
    public String toString() {
        return "BankListConfig{bic='" + bic + "', name='" + name + "'}";
    }
}
