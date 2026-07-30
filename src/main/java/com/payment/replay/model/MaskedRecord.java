package com.payment.replay.model;

/**
 * Represents a log record after sensitive data masking and bank mapping has been applied.
 * This is the sanitized output ready for writing to the output file.
 *
 * Immutable after construction.
 */
public final class MaskedRecord {

    private final String timestamp;
    private final String direction;
    private final String mappedBankBic;
    private final String switchName;
    private final String messageType;
    private final String validFlag;
    private final String instructionId;
    private final String messageId;
    private final String mappedQueueName;
    private final String maskedXmlPayload;
    private final String sourceFile;
    private final long lineNumber;

    private MaskedRecord(Builder builder) {
        this.timestamp = builder.timestamp;
        this.direction = builder.direction;
        this.mappedBankBic = builder.mappedBankBic;
        this.switchName = builder.switchName;
        this.messageType = builder.messageType;
        this.validFlag = builder.validFlag;
        this.instructionId = builder.instructionId;
        this.messageId = builder.messageId;
        this.mappedQueueName = builder.mappedQueueName;
        this.maskedXmlPayload = builder.maskedXmlPayload;
        this.sourceFile = builder.sourceFile;
        this.lineNumber = builder.lineNumber;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getDirection() {
        return direction;
    }

    public String getMappedBankBic() {
        return mappedBankBic;
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

    public String getMappedQueueName() {
        return mappedQueueName;
    }

    public String getMaskedXmlPayload() {
        return maskedXmlPayload;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public long getLineNumber() {
        return lineNumber;
    }

    /**
     * Converts the masked record back to the log line format for output file.
     */
    public String toLogLine() {
        return String.join(",",
                timestamp,
                "mq",
                direction,
                mappedBankBic,
                switchName,
                messageType,
                validFlag,
                instructionId,
                messageId) +
                "..." + mappedQueueName +
                "..." + maskedXmlPayload;
    }

    @Override
    public String toString() {
        return "MaskedRecord{" +
                "timestamp='" + timestamp + '\'' +
                ", mappedBankBic='" + mappedBankBic + '\'' +
                ", mappedQueueName='" + mappedQueueName + '\'' +
                ", messageId='" + messageId + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a MaskedRecord builder pre-populated from a LogRecord.
     */
    public static Builder fromLogRecord(LogRecord record) {
        return new Builder()
                .timestamp(record.getTimestamp())
                .direction(record.getDirection())
                .mappedBankBic(record.getBankBic())
                .switchName(record.getSwitchName())
                .messageType(record.getMessageType())
                .validFlag(record.getValidFlag())
                .instructionId(record.getInstructionId())
                .messageId(record.getMessageId())
                .mappedQueueName(record.getQueueName())
                .maskedXmlPayload(record.getXmlPayload())
                .sourceFile(record.getSourceFile())
                .lineNumber(record.getLineNumber());
    }

    public static final class Builder {
        private String timestamp;
        private String direction;
        private String mappedBankBic;
        private String switchName;
        private String messageType;
        private String validFlag;
        private String instructionId;
        private String messageId;
        private String mappedQueueName;
        private String maskedXmlPayload;
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

        public Builder mappedBankBic(String mappedBankBic) {
            this.mappedBankBic = mappedBankBic;
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

        public Builder mappedQueueName(String mappedQueueName) {
            this.mappedQueueName = mappedQueueName;
            return this;
        }

        public Builder maskedXmlPayload(String maskedXmlPayload) {
            this.maskedXmlPayload = maskedXmlPayload;
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

        public MaskedRecord build() {
            return new MaskedRecord(this);
        }
    }
}
