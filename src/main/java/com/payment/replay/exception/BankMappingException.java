package com.payment.replay.exception;

/**
 * Thrown when bank BIC mapping fails.
 * This occurs when a production BIC is not found in the mapping configuration
 * or when the mapping produces an invalid result.
 */
public class BankMappingException extends PaymentReplayException {

    private static final String ERROR_CODE = "PRE-400";

    private final String productionBic;

    public BankMappingException(String message, String productionBic) {
        super(ERROR_CODE, message);
        this.productionBic = productionBic;
    }

    public BankMappingException(String message, String productionBic, Throwable cause) {
        super(ERROR_CODE, message, cause);
        this.productionBic = productionBic;
    }

    public String getProductionBic() {
        return productionBic;
    }
}
