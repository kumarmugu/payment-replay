package com.payment.replay.model;

/**
 * Represents a message prepared for replay to IBM MQ.
 * Contains the XML payload, target queue details, and scheduling metadata.
 *
 * Immutable after construction.
 */
public final class ReplayMessage {

    private final String timestamp;
    private final String bankBic;
    private final String queueName;
    private final String siteNumber;
    private final String xmlPayload;
    private final String messageId;
    private final String instructionId;
    private final String sourceFile;
    private final long lineNumber;

    private ReplayMessage(Builder builder) {
        this.timestamp = builder.timestamp;
        this.bankBic = builder.bankBic;
        this.queueName = builder.queueName;
        this.siteNumber = builder.siteNumber;
        this.xmlPayload = builder.xmlPayload;
        this.messageId = builder.messageId;
        this.instructionId = builder.instructionId;
        this.sourceFile = builder.sourceFile;
        this.lineNumber = builder.lineNumber;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getBankBic() {
        return bankBic;
    }

    public String getQueueName() {
        return queueName;
    }

    public String getSiteNumber() {
        return siteNumber;
    }

    public String getXmlPayload() {
        return xmlPayload;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getInstructionId() {
        return instructionId;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public long getLineNumber() {
        return lineNumber;
    }

    /**
     * Determines which MQ site this message should be sent to based on the site number.
     *
     * @return 1 for site 1, 2 for site 2
     */
    public int getTargetSite() {
        if ("1".equals(siteNumber)) {
            return 1;
        } else if ("2".equals(siteNumber)) {
            return 2;
        }
        // Default to site 1 for unexpected values
        return 1;
    }

    @Override
    public String toString() {
        return "ReplayMessage{" +
                "timestamp='" + timestamp + '\'' +
                ", bankBic='" + bankBic + '\'' +
                ", queueName='" + queueName + '\'' +
                ", siteNumber='" + siteNumber + '\'' +
                ", messageId='" + messageId + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String timestamp;
        private String bankBic;
        private String queueName;
        private String siteNumber;
        private String xmlPayload;
        private String messageId;
        private String instructionId;
        private String sourceFile;
        private long lineNumber;

        private Builder() {
        }

        public Builder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder bankBic(String bankBic) {
            this.bankBic = bankBic;
            return this;
        }

        public Builder queueName(String queueName) {
            this.queueName = queueName;
            return this;
        }

        public Builder siteNumber(String siteNumber) {
            this.siteNumber = siteNumber;
            return this;
        }

        public Builder xmlPayload(String xmlPayload) {
            this.xmlPayload = xmlPayload;
            return this;
        }

        public Builder messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder instructionId(String instructionId) {
            this.instructionId = instructionId;
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

        public ReplayMessage build() {
            return new ReplayMessage(this);
        }
    }
}
