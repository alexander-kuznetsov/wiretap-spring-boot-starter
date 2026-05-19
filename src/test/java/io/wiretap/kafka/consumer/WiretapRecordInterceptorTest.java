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
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WiretapRecordInterceptorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private WiretapRecordInterceptor<String, String> interceptor;
    private ListAppender<ILoggingEvent> appender;
    private Logger sinkLogger;
    private Consumer<String, String> consumer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        interceptor = new WiretapRecordInterceptor<>(
                new KafkaLogSink(new KafkaConsumerLogMessageSettings(), new KafkaAccessFieldNames(), null, null, null));

        sinkLogger = (Logger) LoggerFactory.getLogger(KafkaLogSink.class);
        appender = new ListAppender<>();
        appender.start();
        sinkLogger.addAppender(appender);

        consumer = mock(Consumer.class);
        when(consumer.groupMetadata()).thenReturn(new ConsumerGroupMetadata("checkout-group"));
        MetricName clientIdMetric = new MetricName(
                "records-consumed-total", "consumer-metrics", "",
                Map.of("client-id", "test-consumer"));
        java.util.Map<MetricName, org.apache.kafka.common.Metric> metricsMap =
                Map.of(clientIdMetric, mock(org.apache.kafka.common.Metric.class));
        doReturn(metricsMap).when(consumer).metrics();
    }

    @AfterEach
    void tearDown() {
        sinkLogger.detachAppender(appender);
        MDC.clear();
    }

    private ConsumerRecord<String, String> record(String key, String value, RecordHeaders headers) {
        return new ConsumerRecord<>(
                "orders.events", 3, 18472L, 1700000000000L, TimestampType.CREATE_TIME,
                key.getBytes(StandardCharsets.UTF_8).length,
                value.getBytes(StandardCharsets.UTF_8).length,
                key, value, headers, java.util.Optional.empty());
    }

    private Map<String, Object> capturedKafkaInfo() throws Exception {
        return MAPPER.readValue(
                appender.list.get(0).getMDCPropertyMap().get(KafkaLogSink.MDC_KEY), MAP_TYPE);
    }

    @Test
    void intercept_emitsOneLine_withGroupClientPartitionAndOffset() throws Exception {
        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader("x-trace-id", "abc".getBytes(StandardCharsets.UTF_8)));

        interceptor.intercept(record("ord-42", "{\"orderId\":\"ord-42\"}", headers), consumer);

        assertThat(appender.list).hasSize(1);
        Map<String, Object> payload = capturedKafkaInfo();
        assertThat(payload)
                .containsEntry("direction", "INCOMING")
                .containsEntry("topic", "orders.events")
                .containsEntry("partition", 3)
                .containsEntry("offset", 18472)
                .containsEntry("client_id", "test-consumer")
                .containsEntry("group_id", "checkout-group")
                .containsEntry("key", "ord-42")
                .containsEntry("value", "{\"orderId\":\"ord-42\"}")
                .containsEntry("timestamp_type", "CREATE_TIME");
        @SuppressWarnings("unchecked")
        Map<String, String> outHeaders = (Map<String, String>) payload.get("headers");
        assertThat(outHeaders).containsEntry("x-trace-id", "abc");
        assertThat(appender.list.get(0).getMessage()).isEqualTo("Captured incoming kafka message {}");
    }

    @Test
    void intercept_usesMdcTraceId_whenAlreadyPopulated() {
        MDC.put("traceId", "preexisting-trace");
        MDC.put("spanId", "preexisting-span");

        interceptor.intercept(record("k", "v", new RecordHeaders()), consumer);

        Map<String, String> mdc = appender.list.get(0).getMDCPropertyMap();
        assertThat(mdc).containsEntry("traceId", "preexisting-trace");
        assertThat(mdc).containsEntry("spanId", "preexisting-span");
    }

    @Test
    void intercept_fallsBackToB3HeaderTrace_whenMdcEmpty() {
        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader("b3",
                "0123456789abcdef0123456789abcdef-aaaaaaaaaaaaaaaa-1".getBytes(StandardCharsets.UTF_8)));

        interceptor.intercept(record("k", "v", headers), consumer);

        Map<String, String> mdc = appender.list.get(0).getMDCPropertyMap();
        assertThat(mdc).containsEntry("traceId", "0123456789abcdef0123456789abcdef");
        assertThat(mdc).containsEntry("spanId", "aaaaaaaaaaaaaaaa");
    }

    @Test
    void intercept_fallsBackToTraceparentHeader_whenMdcEmptyAndB3Absent() {
        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader("traceparent",
                "00-00112233445566778899aabbccddeeff-1122334455667788-01".getBytes(StandardCharsets.UTF_8)));

        interceptor.intercept(record("k", "v", headers), consumer);

        Map<String, String> mdc = appender.list.get(0).getMDCPropertyMap();
        assertThat(mdc).containsEntry("traceId", "00112233445566778899aabbccddeeff");
        assertThat(mdc).containsEntry("spanId", "1122334455667788");
    }

    @Test
    void intercept_logsWithoutTrace_whenMdcAndHeadersEmpty() {
        interceptor.intercept(record("k", "v", new RecordHeaders()), consumer);

        Map<String, String> mdc = appender.list.get(0).getMDCPropertyMap();
        assertThat(mdc).doesNotContainKey("traceId");
        assertThat(mdc).doesNotContainKey("spanId");
    }

    @Test
    void intercept_skipsExcludedTopic() {
        KafkaConsumerLogMessageSettings settings = new KafkaConsumerLogMessageSettings();
        settings.setExcludeTopicPatterns(List.of("orders\\..*"));
        WiretapRecordInterceptor<String, String> filtered = new WiretapRecordInterceptor<>(
                new KafkaLogSink(settings, new KafkaAccessFieldNames(), null, null, null));

        filtered.intercept(record("k", "v", new RecordHeaders()), consumer);

        assertThat(appender.list).isEmpty();
    }

    @Test
    void intercept_respectsVisibilityFlags() throws Exception {
        KafkaConsumerLogMessageSettings settings = new KafkaConsumerLogMessageSettings();
        settings.getVisibilitySettings().put(KafkaConfigurableField.KEY, Boolean.FALSE);
        settings.getVisibilitySettings().put(KafkaConfigurableField.VALUE, Boolean.FALSE);
        WiretapRecordInterceptor<String, String> hidden = new WiretapRecordInterceptor<>(
                new KafkaLogSink(settings, new KafkaAccessFieldNames(), null, null, null));

        hidden.intercept(record("k", "v", new RecordHeaders()), consumer);

        Map<String, Object> payload = capturedKafkaInfo();
        assertThat(payload).doesNotContainKeys("key", "value");
    }

    @Test
    void intercept_returnsSameRecord_evenWhenLoggingThrows() {
        ConsumerRecord<String, String> input = record("k", "v", new RecordHeaders());
        // groupMetadata throws → readGroupId returns null silently; intercept still returns record
        when(consumer.groupMetadata()).thenThrow(new IllegalStateException("boom"));

        ConsumerRecord<String, String> result = interceptor.intercept(input, consumer);

        assertThat(result).isSameAs(input);
        // emit still happened — just without groupId
        assertThat(appender.list).hasSize(1);
    }
}
