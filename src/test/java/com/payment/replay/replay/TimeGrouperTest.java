package com.payment.replay.replay;

import com.payment.replay.config.ReplayConfig;
import com.payment.replay.model.ReplayMessage;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class TimeGrouperTest {

    private TimeGrouper grouper;

    @Before
    public void setUp() {
        ReplayConfig config = new ReplayConfig(60, 250, "./output");
        grouper = new TimeGrouper(config);
    }

    @Test
    public void shouldGroupMessagesByMinute() {
        List<ReplayMessage> messages = Arrays.asList(
                createMessage("2026-07-28T10:01:15.000Z"),
                createMessage("2026-07-28T10:01:45.000Z"),
                createMessage("2026-07-28T10:02:10.000Z"),
                createMessage("2026-07-28T10:02:55.000Z"),
                createMessage("2026-07-28T10:03:30.000Z")
        );

        Map<String, List<ReplayMessage>> groups = grouper.groupByTime(messages);

        assertThat(groups).hasSize(3);
        assertThat(groups.get("10:01")).hasSize(2);
        assertThat(groups.get("10:02")).hasSize(2);
        assertThat(groups.get("10:03")).hasSize(1);
    }

    @Test
    public void shouldSortBatchesChronologically() {
        List<ReplayMessage> messages = Arrays.asList(
                createMessage("2026-07-28T10:03:00.000Z"),
                createMessage("2026-07-28T10:01:00.000Z"),
                createMessage("2026-07-28T10:02:00.000Z")
        );

        Map<String, List<ReplayMessage>> groups = grouper.groupByTime(messages);

        List<String> keys = new java.util.ArrayList<>(groups.keySet());
        assertThat(keys).containsExactly("10:01", "10:02", "10:03");
    }

    @Test
    public void shouldHandleEmptyList() {
        Map<String, List<ReplayMessage>> groups = grouper.groupByTime(java.util.Collections.emptyList());
        assertThat(groups).isEmpty();
    }

    @Test
    public void shouldHandleNullList() {
        Map<String, List<ReplayMessage>> groups = grouper.groupByTime(null);
        assertThat(groups).isEmpty();
    }

    @Test
    public void shouldComputeTimeKeyForIsoTimestamp() {
        assertThat(grouper.computeTimeKey("2026-07-28T10:01:23.456Z")).isEqualTo("10:01");
        assertThat(grouper.computeTimeKey("2026-07-28T23:59:59.999Z")).isEqualTo("23:59");
    }

    @Test
    public void shouldComputeTimeKeyForSpaceSeparatedTimestamp() {
        assertThat(grouper.computeTimeKey("2026-07-28 10:01:23.456")).isEqualTo("10:01");
    }

    @Test
    public void shouldGroupWith30SecondInterval() {
        ReplayConfig config30s = new ReplayConfig(30, 250, "./output");
        TimeGrouper grouper30s = new TimeGrouper(config30s);

        List<ReplayMessage> messages = Arrays.asList(
                createMessage("2026-07-28T10:01:10.000Z"),
                createMessage("2026-07-28T10:01:25.000Z"),
                createMessage("2026-07-28T10:01:35.000Z"),
                createMessage("2026-07-28T10:01:55.000Z")
        );

        Map<String, List<ReplayMessage>> groups = grouper30s.groupByTime(messages);

        assertThat(groups).hasSize(2);
        // 10:01:00-10:01:29 -> first bucket
        // 10:01:30-10:01:59 -> second bucket
    }

    private ReplayMessage createMessage(String timestamp) {
        return ReplayMessage.builder()
                .timestamp(timestamp)
                .bankBic("DBSSSGS0")
                .queueName("DBSSSGS0_REQUEST.TO.G3_1")
                .siteNumber("1")
                .xmlPayload("<Doc/>")
                .messageId("MSG-" + timestamp)
                .instructionId("INSTR001")
                .sourceFile("test.log")
                .lineNumber(1)
                .build();
    }
}
