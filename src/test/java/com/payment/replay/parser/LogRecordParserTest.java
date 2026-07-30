package com.payment.replay.parser;

import com.payment.replay.config.AppConfig;
import com.payment.replay.config.BankListConfig;
import com.payment.replay.config.MqSiteConfig;
import com.payment.replay.config.ReplayConfig;
import com.payment.replay.model.LogRecord;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
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

    @Before
    public void setUp() {
        List<BankListConfig> bankList = Arrays.asList(
                new BankListConfig("DBSSSGSG", "DBS Bank", "SG"),
                new BankListConfig("OCBCSGSG", "OCBC Bank", "SG")
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

    @Test
    public void shouldParseValidLogLine() {
        String line = "2026-07-28T10:01:23.456Z,mq,OUT,DBSSSGSG,sw1,pacs.008,valid,INSTR001,MSG001...DBSSSGSG_REQUEST.TO.G3_1...<Document><Payment>test</Payment></Document>";

        LogRecord record = parser.parseLine(line, "test.log", 1);

        assertThat(record).isNotNull();
        assertThat(record.getTimestamp()).isEqualTo("2026-07-28T10:01:23.456Z");
        assertThat(record.getDirection()).isEqualTo("OUT");
        assertThat(record.getBankBic()).isEqualTo("DBSSSGSG");
        assertThat(record.getSwitchName()).isEqualTo("sw1");
        assertThat(record.getMessageType()).isEqualTo("pacs.008");
        assertThat(record.getValidFlag()).isEqualTo("valid");
        assertThat(record.getInstructionId()).isEqualTo("INSTR001");
        assertThat(record.getMessageId()).isEqualTo("MSG001");
        assertThat(record.getQueueName()).isEqualTo("DBSSSGSG_REQUEST.TO.G3_1");
        assertThat(record.getXmlPayload()).isEqualTo("<Document><Payment>test</Payment></Document>");
    }

    @Test
    public void shouldReturnNullForNonMqLine() {
        String line = "2026-07-28T10:01:23.456Z,http,GET,/api/status,200";

        LogRecord record = parser.parseLine(line, "test.log", 1);

        assertThat(record).isNull();
    }

    @Test
    public void shouldMatchValidQueuePattern() {
        assertThat(parser.matchesQueuePattern("DBSSSGSG_REQUEST.TO.G3_1")).isTrue();
        assertThat(parser.matchesQueuePattern("OCBCSGSG_REQUEST.TO.G3_2")).isTrue();
    }

    @Test
    public void shouldRejectInvalidQueuePattern() {
        assertThat(parser.matchesQueuePattern("SOME_OTHER_QUEUE")).isFalse();
        assertThat(parser.matchesQueuePattern("RESPONSE.FROM.G3_1")).isFalse();
        assertThat(parser.matchesQueuePattern("")).isFalse();
        assertThat(parser.matchesQueuePattern(null)).isFalse();
    }

    @Test
    public void shouldParseFileAndFilterByBankList() throws IOException {
        Path logFile = tempFolder.newFile("raw-20260728001.log").toPath();
        List<String> lines = Arrays.asList(
                "2026-07-28T10:01:00.000Z,mq,OUT,DBSSSGSG,sw1,pacs.008,valid,I001,M001...DBSSSGSG_REQUEST.TO.G3_1...<Doc>xml1</Doc>",
                "2026-07-28T10:01:01.000Z,mq,OUT,UNKNOWNBIC,sw1,pacs.008,valid,I002,M002...UNKNOWNBIC_REQUEST.TO.G3_1...<Doc>xml2</Doc>",
                "2026-07-28T10:01:02.000Z,mq,OUT,OCBCSGSG,sw1,pacs.008,valid,I003,M003...OCBCSGSG_REQUEST.TO.G3_2...<Doc>xml3</Doc>"
        );
        Files.write(logFile, lines, StandardCharsets.UTF_8);

        List<LogRecord> records = new ArrayList<>();
        parser.parseFile(logFile, records::add, null);

        // UNKNOWNBIC should be filtered out
        assertThat(records).hasSize(2);
        assertThat(records.get(0).getBankBic()).isEqualTo("DBSSSGSG");
        assertThat(records.get(1).getBankBic()).isEqualTo("OCBCSGSG");
    }

    @Test
    public void shouldSkipEmptyLines() throws IOException {
        Path logFile = tempFolder.newFile("raw-20260728002.log").toPath();
        List<String> lines = Arrays.asList(
                "",
                "2026-07-28T10:01:00.000Z,mq,OUT,DBSSSGSG,sw1,pacs.008,valid,I001,M001...DBSSSGSG_REQUEST.TO.G3_1...<Doc>xml</Doc>",
                "",
                ""
        );
        Files.write(logFile, lines, StandardCharsets.UTF_8);

        List<LogRecord> records = new ArrayList<>();
        long linesScanned = parser.parseFile(logFile, records::add, null);

        assertThat(records).hasSize(1);
        assertThat(linesScanned).isEqualTo(4);
    }

    @Test
    public void shouldContinueOnParseErrors() throws IOException {
        Path logFile = tempFolder.newFile("raw-20260728003.log").toPath();
        List<String> lines = Arrays.asList(
                "2026-07-28T10:01:00.000Z,mq,OUT,DBSSSGSG,sw1,pacs.008,valid,I001,M001...DBSSSGSG_REQUEST.TO.G3_1...<Doc>valid</Doc>",
                "2026-07-28T10:01:01.000Z,mq,MALFORMED_LINE_NO_SEPARATOR",
                "2026-07-28T10:01:02.000Z,mq,OUT,DBSSSGSG,sw1,pacs.008,valid,I003,M003...DBSSSGSG_REQUEST.TO.G3_1...<Doc>valid2</Doc>"
        );
        Files.write(logFile, lines, StandardCharsets.UTF_8);

        List<LogRecord> records = new ArrayList<>();
        List<Exception> errors = new ArrayList<>();
        parser.parseFile(logFile, records::add, errors::add);

        // Should still process valid records
        assertThat(records).hasSize(2);
    }
}
