package com.payment.replay.model;

/**
 * Represents a single parsed log record from the production log files.
 *
 * Actual log format (no queue name — queue is derived at processing time):
 * <datetime>,mq,<direction>,<bank_bic>,<switch>-MQ<siteNo>,<msg_type>,valid,<instr_id>,<msg_id>,iso20022,raw,<XML>
 *
 * Examples:
 *   2026-07-30T08:56:02.448660,mq,in,NFCCSGSG,switch2UG3IPSSWITC1-MQ1,pacs.002.001.03,valid,INSTR001,MSG001,iso20022,raw,<?xml ...>
 *   2026-07-30T22:52:02.565271,mq,in,ZYBNSGSG,switch1UG3IPSSWITC1-MQ1,admn.005.001.01,valid,INSTR002,MSG002,iso20022,raw,<?xml ...>
 *
 * Filter criteria: direction == "in" AND msgType in (pacs.008, admn.005)
 * Site number is extracted from the switch name suffix: -MQ1 or -MQ2
 *
 * This class is immutable after construction to ensure thread safety.
 */
public final class LogRecord {

    private final String timestamp;
    private final String direction;
    private final String bankBic;
    private final String switchName;
    private final String siteNo;
    private final String messageType;
    private final String validFlag;
    private final String instructionId;
    private final String messageId;
    private final String xmlPayload;
    private final LegType legType;
    private final String sourceFile;
    private final long lineNumber;

    private LogRecord(Builder builder) {
        this.timestamp = builder.timestamp;
        this.direction = builder.direction;
        this.bankBic = builder.bankBic;
        this.switchName = builder.switchName;
        this.siteNo = builder.siteNo;
        this.messageType = builder.messageType;
        this.validFlag = builder.validFlag;
        this.instructionId = builder.instructionId;
        this.messageId = builder.messageId;
        this.xmlPayload = builder.xmlPayload;
        this.legType = builder.legType != null ? builder.legType : LegType.from(builder.messageType);
        this.sourceFile = builder.sourceFile;
        this.lineNumber = builder.lineNumber;
    }

    public String getTimestamp() { return timestamp; }
    public String getDirection() { return direction; }
    public String getBankBic() { return bankBic; }
    public String getSwitchName() { return switchName; }
    public String getSiteNo() { return siteNo; }
    public String getMessageType() { return messageType; }
    public String getValidFlag() { return validFlag; }
    public String getInstructionId() { return instructionId; }
    public String getMessageId() { return messageId; }
    public String getXmlPayload() { return xmlPayload; }
    public LegType getLegType()   { return legType; }
    public String getSourceFile() { return sourceFile; }
    public long getLineNumber() { return lineNumber; }

    /**
     * Returns the site number (1 or 2) extracted from the switch name suffix (-MQ1 / -MQ2).
     * Defaults to "1" if not determinable.
     */
    public String extractSiteNumber() {
        return siteNo != null && !siteNo.isEmpty() ? siteNo : "1";
    }

    /**
     * Derives the destination MQ queue name from the bank BIC and site number.
     * Leg1 pattern: <bankBic>_REQUEST.TO.G3_<siteNo>
     * Leg3 pattern: <bankBic>_RESPONSE.TO.G3_<siteNo>
     */
    public String deriveQueueName() {
        String middle = (legType == LegType.LEG3) ? "_RESPONSE.TO.G3_" : "_REQUEST.TO.G3_";
        return bankBic + middle + extractSiteNumber();
    }

    /**
     * Derives the destination MQ queue name using the given (mapped) BIC.
     */
    public String deriveQueueName(String bic) {
        String middle = (legType == LegType.LEG3) ? "_RESPONSE.TO.G3_" : "_REQUEST.TO.G3_";
        return bic + middle + extractSiteNumber();
    }

    @Override
    public String toString() {
        return "LogRecord{" +
                "timestamp='" + timestamp + '\'' +
                ", bankBic='" + bankBic + '\'' +
                ", switchName='" + switchName + '\'' +
                ", siteNo='" + siteNo + '\'' +
                ", messageType='" + messageType + '\'' +
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
        private String siteNo;
        private String messageType;
        private String validFlag;
        private String instructionId;
        private String messageId;
        private String xmlPayload;
        private LegType legType;
        private String sourceFile;
        private long lineNumber;

        private Builder() {}

        public Builder timestamp(String v)     { this.timestamp = v;     return this; }
        public Builder direction(String v)     { this.direction = v;     return this; }
        public Builder bankBic(String v)       { this.bankBic = v;       return this; }
        public Builder switchName(String v)    { this.switchName = v;    return this; }
        public Builder siteNo(String v)        { this.siteNo = v;        return this; }
        public Builder messageType(String v)   { this.messageType = v;   return this; }
        public Builder validFlag(String v)     { this.validFlag = v;     return this; }
        public Builder instructionId(String v) { this.instructionId = v; return this; }
        public Builder messageId(String v)     { this.messageId = v;     return this; }
        public Builder xmlPayload(String v)    { this.xmlPayload = v;    return this; }
        public Builder legType(LegType v)      { this.legType = v;       return this; }
        public Builder sourceFile(String v)    { this.sourceFile = v;    return this; }
        public Builder lineNumber(long v)      { this.lineNumber = v;    return this; }

        public LogRecord build() { return new LogRecord(this); }
    }
}
