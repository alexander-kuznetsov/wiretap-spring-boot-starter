package io.wiretap.kafka.consumer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wiretap.kafka.KafkaLogSink;
import io.wiretap.kafka.message.settings.KafkaAccessFieldNames;
import io.wiretap.kafka.message.settings.KafkaConsumerLogMessageSettings;
import io.wiretap.kafka.message.settings.KafkaInfoLogMessageSettings.KafkaConfigurableField;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WiretapConsumerInterceptorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private WiretapConsumerInterceptor<String, String> interceptor;
    private ListAppender<ILoggingEvent> appender;
    private Logger sinkLogger;

    @BeforeEach
    void setUp() {
        wireSink(new KafkaConsumerLogMessageSettings());
        interceptor = new WiretapConsumerInterceptor<>();
        interceptor.configure(Map.of(
                ConsumerConfig.CLIENT_ID_CONFIG, "test-consumer",
                ConsumerConfig.GROUP_ID_CONFIG, "checkout-group"));

        sinkLogger = (Logger) LoggerFactory.getLogger(KafkaLogSink.class);
        appender = new ListAppender<>();
        appender.start();
        sinkLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        sinkLogger.detachAppender(appender);
        WiretapConsumerInterceptor.setSink(null);
    }

    private void wireSink(KafkaConsumerLogMessageSettings settings) {
        WiretapConsumerInterceptor.setSink(
                new KafkaLogSink(settings, new KafkaAccessFieldNames(), null, null, null));
    }

    private ConsumerRecords<String, String> records(ConsumerRecord<String, String>... records) {
        TopicPartition tp = records.length > 0
                ? new TopicPartition(records[0].topic(), records[0].partition())
                : new TopicPartition("none", 0);
        return new ConsumerRecords<>(Map.of(tp, List.of(records)));
    }

    @Test
    void onConsume_emitsOneLineForEachRecord_withGroupAndOffset() throws Exception {
        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader("x-trace-id", "abc".getBytes(StandardCharsets.UTF_8)));

        ConsumerRecord<String, String> r1 = new ConsumerRecord<>(
                "orders.events", 3, 18472L, 1700000000000L, TimestampType.CREATE_TIME,
                6, 19, "ord-42", "{\"orderId\":\"ord-42\"}", headers, java.util.Optional.empty());
        ConsumerRecord<String, String> r2 = new ConsumerRecord<>(
                "orders.events", 3, 18473L, 1700000001000L, TimestampType.CREATE_TIME,
                6, 19, "ord-43", "{\"orderId\":\"ord-43\"}", new RecordHeaders(), java.util.Optional.empty());

        interceptor.onConsume(records(r1, r2));

        assertThat(appender.list).hasSize(2);
        Map<String, Object> first = MAPPER.readValue(
                appender.list.get(0).getMDCPropertyMap().get(KafkaLogSink.MDC_KEY), MAP_TYPE);
        assertThat(first)
                .containsEntry("direction", "INCOMING")
                .containsEntry("topic", "orders.events")
                .containsEntry("partition", 3)
                .containsEntry("offset", 18472)
                .containsEntry("client_id", "test-consumer")
                .containsEntry("group_id", "checkout-group")
                .containsEntry("key", "ord-42")
                .containsEntry("value", "{\"orderId\":\"ord-42\"}")
                .containsEntry("timestamp_type", "CREATE_TIME");
        assertThat((Map<String, String>) first.get("headers")).containsEntry("x-trace-id", "abc");

        assertThat(appender.list.get(0).getMessage()).isEqualTo("Captured incoming kafka message {}");
    }

    @Test
    void excludedTopic_skipsLogging() {
        KafkaConsumerLogMessageSettings settings = new KafkaConsumerLogMessageSettings();
        settings.setExcludeTopicPatterns(List.of("__consumer_offsets"));
        wireSink(settings);

        ConsumerRecord<String, String> r = new ConsumerRecord<>(
                "__consumer_offsets", 0, 1L, -1, TimestampType.NO_TIMESTAMP_TYPE,
                0, 0, "k", "v", new RecordHeaders(), java.util.Optional.empty());

        interceptor.onConsume(records(r));

        assertThat(appender.list).isEmpty();
    }

    @Test
    void visibilityOff_omitsKeyAndValue() throws Exception {
        KafkaConsumerLogMessageSettings settings = new KafkaConsumerLogMessageSettings();
        settings.getVisibilitySettings().put(KafkaConfigurableField.KEY, Boolean.FALSE);
        settings.getVisibilitySettings().put(KafkaConfigurableField.VALUE, Boolean.FALSE);
        wireSink(settings);

        ConsumerRecord<String, String> r = new ConsumerRecord<>(
                "orders.events", 0, 1L, 0L, TimestampType.CREATE_TIME,
                0, 0, "k", "v", new RecordHeaders(), java.util.Optional.empty());

        interceptor.onConsume(records(r));

        Map<String, Object> payload = MAPPER.readValue(
                appender.list.get(0).getMDCPropertyMap().get(KafkaLogSink.MDC_KEY), MAP_TYPE);
        assertThat(payload).doesNotContainKeys("key", "value");
    }

    @Test
    void onCommit_doesNothing() {
        interceptor.onCommit(Map.of());
        assertThat(appender.list).isEmpty();
    }

    @Test
    void b3Header_restoresMdcTraceIdForLogLine() {
        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader("b3",
                "0123456789abcdef0123456789abcdef-aaaaaaaaaaaaaaaa-1".getBytes(StandardCharsets.UTF_8)));

        ConsumerRecord<String, String> r = new ConsumerRecord<>(
                "orders.events", 0, 1L, 0L, TimestampType.CREATE_TIME,
                0, 0, "k", "v", headers, java.util.Optional.empty());

        interceptor.onConsume(records(r));

        assertThat(appender.list).hasSize(1);
        Map<String, String> mdc = appender.list.get(0).getMDCPropertyMap();
        assertThat(mdc).containsEntry("traceId", "0123456789abcdef0123456789abcdef");
        assertThat(mdc).containsEntry("spanId", "aaaaaaaaaaaaaaaa");
    }

    @Test
    void traceparentHeader_restoresMdcWhenB3Absent() {
        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader("traceparent",
                "00-00112233445566778899aabbccddeeff-1122334455667788-01".getBytes(StandardCharsets.UTF_8)));

        ConsumerRecord<String, String> r = new ConsumerRecord<>(
                "orders.events", 0, 1L, 0L, TimestampType.CREATE_TIME,
                0, 0, "k", "v", headers, java.util.Optional.empty());

        interceptor.onConsume(records(r));

        Map<String, String> mdc = appender.list.get(0).getMDCPropertyMap();
        assertThat(mdc).containsEntry("traceId", "00112233445566778899aabbccddeeff");
        assertThat(mdc).containsEntry("spanId", "1122334455667788");
    }
}
