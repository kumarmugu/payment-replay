package com.payment.replay.model;

/**
 * Represents a log record after sensitive data masking and bank mapping has been applied.
 * This is the sanitized output ready for writing to the output file.
 *
 * Output format mirrors the input format exactly, with:
 *   - Production BIC replaced with UAT BIC
 *   - Sensitive XML fields masked
 *   - A derived queue name appended as an extra field for replay convenience
 *
 * Output line format:
 *   <datetime>,mq,in,<uat_bic>,<switchName>,<msgType>,valid,<instrId>,<msgId>,iso20022,raw,<maskedXml>,<derivedQueueName>
 *
 * The derivedQueueName is the last field and is used by SanitizedFileReader during
 * replay to know where to send the message without re-deriving it.
 *
 * Immutable after construction.
 */
public final class MaskedRecord {

    private final String timestamp;
    private final String direction;
    private final String mappedBankBic;
    private final String switchName;
    private final String siteNo;
    private final String messageType;
    private final String validFlag;
    private final String instructionId;
    private final String messageId;
    private final String maskedXmlPayload;
    private final String derivedQueueName;
    private final String sourceFile;
    private final long lineNumber;

    private MaskedRecord(Builder builder) {
        this.timestamp       = builder.timestamp;
        this.direction       = builder.direction;
        this.mappedBankBic   = builder.mappedBankBic;
        this.switchName      = builder.switchName;
        this.siteNo          = builder.siteNo;
        this.messageType     = builder.messageType;
        this.validFlag       = builder.validFlag;
        this.instructionId   = builder.instructionId;
        this.messageId       = builder.messageId;
        this.maskedXmlPayload = builder.maskedXmlPayload;
        this.derivedQueueName = builder.derivedQueueName;
        this.sourceFile      = builder.sourceFile;
        this.lineNumber      = builder.lineNumber;
    }

    public String getTimestamp()        { return timestamp; }
    public String getDirection()        { return direction; }
    public String getMappedBankBic()    { return mappedBankBic; }
    public String getSwitchName()       { return switchName; }
    public String getSiteNo()           { return siteNo; }
    public String getMessageType()      { return messageType; }
    public String getValidFlag()        { return validFlag; }
    public String getInstructionId()    { return instructionId; }
    public String getMessageId()        { return messageId; }
    public String getMaskedXmlPayload() { return maskedXmlPayload; }
    public String getDerivedQueueName() { return derivedQueueName; }
    public String getSourceFile()       { return sourceFile; }
    public long   getLineNumber()       { return lineNumber; }

    /**
     * Serialises the masked record back to a log line for the output file.
     *
     * Format:
     *   <datetime>,mq,in,<uat_bic>,<switchName>,<msgType>,valid,<instrId>,<msgId>,iso20022,raw,<maskedXml>,<derivedQueueName>
     *
     * The derivedQueueName is appended as the 13th field so that the replay
     * command can read it directly without reconstructing it.
     */
    public String toLogLine() {
        return timestamp + ",mq," + direction + "," + mappedBankBic + ","
                + switchName + "," + messageType + "," + validFlag + ","
                + instructionId + "," + messageId + ",iso20022,raw,"
                + maskedXmlPayload + "," + derivedQueueName;
    }

    @Override
    public String toString() {
        return "MaskedRecord{timestamp='" + timestamp + "', mappedBankBic='" + mappedBankBic
                + "', queueName='" + derivedQueueName + "', messageId='" + messageId + "'}";
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder pre-populated from a LogRecord.
     * The caller must still set mappedBankBic, maskedXmlPayload, and derivedQueueName
     * after applying masking and BIC mapping.
     */
    public static Builder fromLogRecord(LogRecord record) {
        return new Builder()
                .timestamp(record.getTimestamp())
                .direction(record.getDirection())
                .mappedBankBic(record.getBankBic())     // overridden after mapping
                .switchName(record.getSwitchName())
                .siteNo(record.getSiteNo())
                .messageType(record.getMessageType())
                .validFlag(record.getValidFlag())
                .instructionId(record.getInstructionId())
                .messageId(record.getMessageId())
                .maskedXmlPayload(record.getXmlPayload()) // overridden after masking
                .derivedQueueName(record.deriveQueueName())
                .sourceFile(record.getSourceFile())
                .lineNumber(record.getLineNumber());
    }

    public static final class Builder {
        private String timestamp;
        private String direction;
        private String mappedBankBic;
        private String switchName;
        private String siteNo;
        private String messageType;
        private String validFlag;
        private String instructionId;
        private String messageId;
        private String maskedXmlPayload;
        private String derivedQueueName;
        private String sourceFile;
        private long   lineNumber;

        private Builder() {}

        public Builder timestamp(String v)        { this.timestamp = v;        return this; }
        public Builder direction(String v)        { this.direction = v;        return this; }
        public Builder mappedBankBic(String v)    { this.mappedBankBic = v;    return this; }
        public Builder switchName(String v)       { this.switchName = v;       return this; }
        public Builder siteNo(String v)           { this.siteNo = v;           return this; }
        public Builder messageType(String v)      { this.messageType = v;      return this; }
        public Builder validFlag(String v)        { this.validFlag = v;        return this; }
        public Builder instructionId(String v)    { this.instructionId = v;    return this; }
        public Builder messageId(String v)        { this.messageId = v;        return this; }
        public Builder maskedXmlPayload(String v) { this.maskedXmlPayload = v; return this; }
        public Builder derivedQueueName(String v) { this.derivedQueueName = v; return this; }
        public Builder sourceFile(String v)       { this.sourceFile = v;       return this; }
        public Builder lineNumber(long v)         { this.lineNumber = v;       return this; }

        public MaskedRecord build() { return new MaskedRecord(this); }
    }
}
