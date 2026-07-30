package com.payment.replay.model;

/**
 * Represents a single parsed log record from the production log files.
 * 
 * Log format:
 * <datetime in utc>,mq,<direction>,<bank bic>,<switch name>,<Msg type>,valid>,<instra id>,<MsgId>...<MQ name>...<XML Message>
 *
 * This class is immutable after construction to ensure thread safety.
 */
public final class LogRecord {

    private final String timestamp;
    private final String direction;
    private final String bankBic;
    private final String switchName;
    private final String messageType;
    private final String validFlag;
    private final String instructionId;
    private final String messageId;
    private final String queueName;
    private final String xmlPayload;
    private final String sourceFile;
    private final long lineNumber;

    private LogRecord(Builder builder) {
        this.timestamp = builder.timestamp;
        this.direction = builder.direction;
        this.bankBic = builder.bankBic;
        this.switchName = builder.switchName;
        this.messageType = builder.messageType;
        this.validFlag = builder.validFlag;
        this.instructionId = builder.instructionId;
        this.messageId = builder.messageId;
        this.queueName = builder.queueName;
        this.xmlPayload = builder.xmlPayload;
        this.sourceFile = builder.sourceFile;
        this.lineNumber = builder.lineNumber;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getDirection() {
        return direction;
    }

    public String getBankBic() {
        return bankBic;
    }

    public String getSwitchName() {
        return switchName;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getValidFlag() {
        return validFlag;
    }

    public String getInstructionId() {
        return instructionId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getQueueName() {
        return queueName;
    }

    public String getXmlPayload() {
        return xmlPayload;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public long getLineNumber() {
        return lineNumber;
    }

    /**
     * Extracts the site number from the queue name pattern: BANK_REQUEST.TO.G3_<siteNo>
     *
     * @return site number string or empty string if not parseable
     */
    public String extractSiteNumber() {
        if (queueName == null || !queueName.contains("G3_")) {
            return "";
        }
        int idx = queueName.lastIndexOf("G3_");
        return queueName.substring(idx + 3);
    }

    /**
     * Extracts the bank prefix from the queue name pattern: <BANK>_REQUEST.TO.G3_<siteNo>
     *
     * @return bank prefix from queue name
     */
    public String extractBankFromQueue() {
        if (queueName == null || !queueName.contains("_REQUEST")) {
            return "";
        }
        return queueName.substring(0, queueName.indexOf("_REQUEST"));
    }

    @Override
    public String toString() {
        return "LogRecord{" +
                "timestamp='" + timestamp + '\'' +
                ", bankBic='" + bankBic + '\'' +
                ", queueName='" + queueName + '\'' +
                ", messageId='" + messageId + '\'' +
                ", sourceFile='" + sourceFile + '\'' +
                ", lineNumber=" + lineNumber +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String timestamp;
        private String direction;
        private String bankBic;
        private String switchName;
        private String messageType;
        private String validFlag;
        private String instructionId;
        private String messageId;
        private String queueName;
        private String xmlPayload;
        private String sourceFile;
        private long lineNumber;

        private Builder() {
        }

        public Builder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder direction(String direction) {
            this.direction = direction;
            return this;
        }

        public Builder bankBic(String bankBic) {
            this.bankBic = bankBic;
            return this;
        }

        public Builder switchName(String switchName) {
            this.switchName = switchName;
            return this;
        }

        public Builder messageType(String messageType) {
            this.messageType = messageType;
            return this;
        }

        public Builder validFlag(String validFlag) {
            this.validFlag = validFlag;
            return this;
        }

        public Builder instructionId(String instructionId) {
            this.instructionId = instructionId;
            return this;
        }

        public Builder messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder queueName(String queueName) {
            this.queueName = queueName;
            return this;
        }

        public Builder xmlPayload(String xmlPayload) {
            this.xmlPayload = xmlPayload;
            return this;
        }

        public Builder sourceFile(String sourceFile) {
            this.sourceFile = sourceFile;
            return this;
        }

        public Builder lineNumber(long lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }

        public LogRecord build() {
            return new LogRecord(this);
        }
    }
}
