package com.payment.replay.parser;

import com.payment.replay.config.AppConfig;
import com.payment.replay.config.BankListConfig;
import com.payment.replay.config.MqSiteConfig;
import com.payment.replay.config.ReplayConfig;
import com.payment.replay.model.LogRecord;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class LogRecordParserTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private LogRecordParser parser;

    // Valid pacs.008 inbound line
    private static final String VALID_PACS008 =
            "2026-07-28T10:01:23.456789,mq,in,DBSSSGSG,switch1UG3IPSSWITC1-MQ1,pacs.008.001.08,valid," +
            "20260728DBSSSGSG0001,20260728NFCCSGSGRTGS0001,iso20022,raw," +
            "<?xml version=\"1.0\"?><Document><Nm>Test</Nm></Document>";

    // Valid admn.005 inbound line
    private static final String VALID_ADMN005 =
            "2026-07-28T10:02:30.000000,mq,in,ZYBNSGSG,switch2UG3IPSSWITCHI-MQ2,admn.005.001.01,valid," +
            "20260728ZYBN0001,20260728ADMN005ZYBN0001,iso20022,raw," +
            "<?xml version=\"1.0\"?><Document><ReqToModfyPmt/></Document>";

    // Outbound — must be filtered
    private static final String OUTBOUND =
            "2026-07-28T10:01:45.000000,mq,out,DBSSSGSG,switch1UG3IPSSWITC1-MQ1,pacs.002.001.10,valid," +
            "20260728DBSSSGSG0001,ACK001,iso20022,raw," +
            "<?xml version=\"1.0\"?><Document><TxSts>ACCP</TxSts></Document>";

    // Non-qualifying message type
    private static final String NON_QUALIFYING =
            "2026-07-28T10:01:50.000000,mq,in,DBSSSGSG,switch1UG3IPSSWITC1-MQ1,pacs.004.001.10,valid," +
            "INSTR001,MSG001,iso20022,raw," +
            "<?xml version=\"1.0\"?><Document/>";

    @Before
    public void setUp() {
        List<BankListConfig> bankList = Arrays.asList(
                new BankListConfig("DBSSSGSG", "DBS Bank", "SG"),
                new BankListConfig("OCBCSGSG", "OCBC Bank", "SG"),
                new BankListConfig("ZYBNSGSG", "Maybank", "SG")
        );
        AppConfig config = AppConfig.builder()
                .bankList(bankList)
                .bankMappings(Collections.emptyList())
                .maskFields(Collections.emptyList())
                .replayConfig(new ReplayConfig(60, 250, "./output"))
                .mqSite1Config(MqSiteConfig.builder().build())
                .mqSite2Config(MqSiteConfig.builder().build())
                .build();
        parser = new LogRecordParser(config);
    }

    // ── parseLine ─────────────────────────────────────────────────────────────

    @Test
    public void shouldParseValidPacs008Line() {
        LogRecord record = parser.parseLine(VALID_PACS008, "test.log", 1);

        assertThat(record).isNotNull();
        assertThat(record.getTimestamp()).isEqualTo("2026-07-28T10:01:23.456789");
        assertThat(record.getDirection()).isEqualTo("in");
        assertThat(record.getBankBic()).isEqualTo("DBSSSGSG");
        assertThat(record.getSwitchName()).isEqualTo("switch1UG3IPSSWITC1-MQ1");
        assertThat(record.getSiteNo()).isEqualTo("1");
        assertThat(record.getMessageType()).isEqualTo("pacs.008.001.08");
        assertThat(record.getValidFlag()).isEqualTo("valid");
        assertThat(record.getInstructionId()).isEqualTo("20260728DBSSSGSG0001");
        assertThat(record.getMessageId()).isEqualTo("20260728NFCCSGSGRTGS0001");
        assertThat(record.getXmlPayload()).startsWith("<?xml");
    }

    @Test
    public void shouldParseValidAdmn005Line() {
        LogRecord record = parser.parseLine(VALID_ADMN005, "test.log", 1);

        assertThat(record).isNotNull();
        assertThat(record.getMessageType()).isEqualTo("admn.005.001.01");
        assertThat(record.getBankBic()).isEqualTo("ZYBNSGSG");
        assertThat(record.getSiteNo()).isEqualTo("2");
    }

    @Test
    public void shouldReturnNullForOutboundRecord() {
        LogRecord record = parser.parseLine(OUTBOUND, "test.log", 1);
        assertThat(record).isNull();
    }

    @Test
    public void shouldReturnNullForNonQualifyingMsgType() {
        LogRecord record = parser.parseLine(NON_QUALIFYING, "test.log", 1);
        assertThat(record).isNull();
    }

    @Test
    public void shouldReturnNullForNonMqLine() {
        String line = "2026-07-28T10:01:00.000000,http,GET,/health,200";
        assertThat(parser.parseLine(line, "test.log", 1)).isNull();
    }

    @Test
    public void shouldPreserveXmlWithCommas() {
        // XML contains commas inside attribute values — must not be split
        String lineWithCommas =
                "2026-07-28T10:01:00.000000,mq,in,DBSSSGSG,switch1UG3IPSSWITC1-MQ1," +
                "pacs.008.001.08,valid,INSTR001,MSG001,iso20022,raw," +
                "<?xml?><Doc><Adr>Line1,Line2</Adr><Nm>Foo,Bar</Nm></Doc>";

        LogRecord record = parser.parseLine(lineWithCommas, "test.log", 1);

        assertThat(record).isNotNull();
        assertThat(record.getXmlPayload()).isEqualTo("<?xml?><Doc><Adr>Line1,Line2</Adr><Nm>Foo,Bar</Nm></Doc>");
    }

    // ── isQualifyingMsgType ───────────────────────────────────────────────────

    @Test
    public void shouldAcceptQualifyingMsgTypes() {
        assertThat(parser.isQualifyingMsgType("pacs.008.001.08")).isTrue();
        assertThat(parser.isQualifyingMsgType("pacs.008.001.02")).isTrue();
        assertThat(parser.isQualifyingMsgType("admn.005.001.01")).isTrue();
        assertThat(parser.isQualifyingMsgType("PACS.008.001.08")).isTrue(); // case-insensitive
    }

    @Test
    public void shouldRejectNonQualifyingMsgTypes() {
        assertThat(parser.isQualifyingMsgType("pacs.002.001.10")).isFalse();
        assertThat(parser.isQualifyingMsgType("pacs.004.001.10")).isFalse();
        assertThat(parser.isQualifyingMsgType("camt.056.001.08")).isFalse();
        assertThat(parser.isQualifyingMsgType("")).isFalse();
        assertThat(parser.isQualifyingMsgType(null)).isFalse();
    }

    // ── extractSiteNo ─────────────────────────────────────────────────────────

    @Test
    public void shouldExtractSiteNumbers() {
        assertThat(parser.extractSiteNo("switch1UG3IPSSWITC1-MQ1")).isEqualTo("1");
        assertThat(parser.extractSiteNo("switch2UG3IPSSWITCHI-MQ2")).isEqualTo("2");
        assertThat(parser.extractSiteNo("switch3UG3IPSSWITC1-MQ1")).isEqualTo("1");
    }

    @Test
    public void shouldDefaultToSite1WhenSuffixMissing() {
        assertThat(parser.extractSiteNo("switchWithNoSuffix")).isEqualTo("1");
        assertThat(parser.extractSiteNo("")).isEqualTo("1");
        assertThat(parser.extractSiteNo(null)).isEqualTo("1");
    }

    // ── parseFile (integration) ────────────────────────────────────────────────

    @Test
    public void shouldFilterByBankListDuringFileProcessing() throws IOException {
        Path logFile = tempFolder.newFile("raw-20260728001.log").toPath();
        Files.write(logFile, Arrays.asList(VALID_PACS008, VALID_ADMN005), StandardCharsets.UTF_8);

        List<LogRecord> records = new ArrayList<>();
        parser.parseFile(logFile, records::add, null);

        // DBSSSGSG is whitelisted, ZYBNSGSG is whitelisted — both should pass
        assertThat(records).hasSize(2);
    }

    @Test
    public void shouldFilterOutOutboundAndNonQualifying() throws IOException {
        Path logFile = tempFolder.newFile("raw-20260728002.log").toPath();
        Files.write(logFile, Arrays.asList(VALID_PACS008, OUTBOUND, NON_QUALIFYING),
                StandardCharsets.UTF_8);

        List<LogRecord> records = new ArrayList<>();
        parser.parseFile(logFile, records::add, null);

        // Only the inbound pacs.008 passes
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getMessageType()).isEqualTo("pacs.008.001.08");
    }

    @Test
    public void shouldSkipNonWhitelistedBank() throws IOException {
        Path logFile = tempFolder.newFile("raw-20260728003.log").toPath();
        String nonListedBank =
                "2026-07-28T10:03:00.000000,mq,in,UNKNOWNBIC,switch1UG3IPSSWITC1-MQ1," +
                "pacs.008.001.08,valid,INSTR999,MSG999,iso20022,raw,<?xml?><Doc/>";
        Files.write(logFile, Collections.singletonList(nonListedBank), StandardCharsets.UTF_8);

        List<LogRecord> records = new ArrayList<>();
        parser.parseFile(logFile, records::add, null);

        assertThat(records).isEmpty();
    }

    @Test
    public void shouldReturnCorrectLineCount() throws IOException {
        Path logFile = tempFolder.newFile("raw-20260728004.log").toPath();
        Files.write(logFile, Arrays.asList(VALID_PACS008, "", OUTBOUND, "   "),
                StandardCharsets.UTF_8);

        List<LogRecord> records = new ArrayList<>();
        long linesScanned = parser.parseFile(logFile, records::add, null);

        assertThat(linesScanned).isEqualTo(4);
        assertThat(records).hasSize(1);
    }

    @Test
    public void shouldDeriveQueueNameFromRecord() {
        LogRecord record = parser.parseLine(VALID_PACS008, "test.log", 1);

        assertThat(record).isNotNull();
        assertThat(record.deriveQueueName()).isEqualTo("DBSSSGSG_REQUEST.TO.G3_1");
        assertThat(record.deriveQueueName("DBSSSGS0")).isEqualTo("DBSSSGS0_REQUEST.TO.G3_1");
    }
}
