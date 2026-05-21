package io.wiretap.kafka.consumer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
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

    private static final ObjectMapper MAPPER = tools.jackson.databind.json.JsonMapper.builder().build();
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

    private Map<String, Object> latestKafkaInfo() throws Exception {
        return MAPPER.readValue(
                appender.list.get(appender.list.size() - 1).getMDCPropertyMap().get(KafkaLogSink.MDC_KEY), MAP_TYPE);
    }

    // ---- intercept ----

    @Test
    void intercept_doesNotEmitAnyLog() {
        interceptor.intercept(record("k", "v", new RecordHeaders()), consumer);

        assertThat(appender.list).isEmpty();
    }

    @Test
    void intercept_skipsExcludedTopicSilently() {
        KafkaConsumerLogMessageSettings settings = new KafkaConsumerLogMessageSettings();
        settings.setExcludeTopicPatterns(List.of("orders\\..*"));
        WiretapRecordInterceptor<String, String> filtered = new WiretapRecordInterceptor<>(
                new KafkaLogSink(settings, new KafkaAccessFieldNames(), null, null, null));

        ConsumerRecord<String, String> rec = record("k", "v", new RecordHeaders());
        filtered.intercept(rec, consumer);
        filtered.success(rec, consumer);

        assertThat(appender.list).isEmpty();
    }

    // ---- success ----

    @Test
    void success_emitsSingleLogWithSuccessStatusAndDuration() throws Exception {
        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader("x-trace-id", "abc".getBytes(StandardCharsets.UTF_8)));
        ConsumerRecord<String, String> rec = record("ord-42", "{\"orderId\":\"ord-42\"}", headers);

        interceptor.intercept(rec, consumer);
        interceptor.success(rec, consumer);

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).startsWith("Processed incoming kafka message orders.events in ")
                .endsWith("ms");

        Map<String, Object> payload = latestKafkaInfo();
        assertThat(payload)
                .containsEntry("direction", "INCOMING")
                .containsEntry("topic", "orders.events")
                .containsEntry("partition", 3)
                .containsEntry("offset", 18472)
                .containsEntry("client_id", "test-consumer")
                .containsEntry("group_id", "checkout-group")
                .containsEntry("key", "ord-42")
                .containsEntry("value", "{\"orderId\":\"ord-42\"}")
                .containsEntry("timestamp_type", "CREATE_TIME")
                .containsEntry("status", "SUCCESS")
                .containsKey("duration");
        assertThat(((Number) payload.get("duration")).longValue()).isGreaterThanOrEqualTo(0L);
    }

    // ---- failure ----

    @Test
    void failure_emitsWarnWithErrorStatusDurationAndErrorFields() throws Exception {
        ConsumerRecord<String, String> rec = record("k", "v", new RecordHeaders());
        IllegalStateException boom = new IllegalStateException("validation failed");

        interceptor.intercept(rec, consumer);
        interceptor.failure(rec, boom, consumer);

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).startsWith("Failed to process incoming kafka message orders.events");

        Map<String, Object> payload = latestKafkaInfo();
        assertThat(payload)
                .containsEntry("status", "ERROR")
                .containsEntry("error_class", "java.lang.IllegalStateException")
                .containsEntry("error_message", "validation failed")
                .containsKey("duration");
    }

    // ---- MDC / trace propagation ----

    @Test
    void success_inheritsMdcTraceId_whenPopulatedBeforeIntercept() {
        MDC.put("traceId", "preexisting-trace");
        MDC.put("spanId", "preexisting-span");
        ConsumerRecord<String, String> rec = record("k", "v", new RecordHeaders());

        interceptor.intercept(rec, consumer);
        interceptor.success(rec, consumer);

        Map<String, String> mdc = appender.list.get(0).getMDCPropertyMap();
        assertThat(mdc).containsEntry("traceId", "preexisting-trace");
        assertThat(mdc).containsEntry("spanId", "preexisting-span");
    }

    @Test
    void success_fallsBackToB3HeaderTrace_whenMdcEmpty() {
        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader("b3",
                "0123456789abcdef0123456789abcdef-aaaaaaaaaaaaaaaa-1".getBytes(StandardCharsets.UTF_8)));
        ConsumerRecord<String, String> rec = record("k", "v", headers);

        interceptor.intercept(rec, consumer);
        interceptor.success(rec, consumer);

        Map<String, String> mdc = appender.list.get(0).getMDCPropertyMap();
        assertThat(mdc).containsEntry("traceId", "0123456789abcdef0123456789abcdef");
        assertThat(mdc).containsEntry("spanId", "aaaaaaaaaaaaaaaa");
    }

    @Test
    void failure_fallsBackToTraceparentHeader_whenMdcEmpty() {
        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader("traceparent",
                "00-00112233445566778899aabbccddeeff-1122334455667788-01".getBytes(StandardCharsets.UTF_8)));
        ConsumerRecord<String, String> rec = record("k", "v", headers);

        interceptor.intercept(rec, consumer);
        interceptor.failure(rec, new RuntimeException("boom"), consumer);

        Map<String, String> mdc = appender.list.get(0).getMDCPropertyMap();
        assertThat(mdc).containsEntry("traceId", "00112233445566778899aabbccddeeff");
        assertThat(mdc).containsEntry("spanId", "1122334455667788");
    }

    @Test
    void success_logsWithoutTrace_whenMdcAndHeadersEmpty() {
        ConsumerRecord<String, String> rec = record("k", "v", new RecordHeaders());

        interceptor.intercept(rec, consumer);
        interceptor.success(rec, consumer);

        Map<String, String> mdc = appender.list.get(0).getMDCPropertyMap();
        assertThat(mdc).doesNotContainKey("traceId");
        assertThat(mdc).doesNotContainKey("spanId");
    }

    // ---- visibility ----

    @Test
    void durationVisibilityFalse_omitsDurationField() throws Exception {
        KafkaConsumerLogMessageSettings settings = new KafkaConsumerLogMessageSettings();
        settings.getVisibilitySettings().put(KafkaConfigurableField.DURATION, Boolean.FALSE);
        WiretapRecordInterceptor<String, String> hidden = new WiretapRecordInterceptor<>(
                new KafkaLogSink(settings, new KafkaAccessFieldNames(), null, null, null));

        ConsumerRecord<String, String> rec = record("k", "v", new RecordHeaders());
        hidden.intercept(rec, consumer);
        hidden.success(rec, consumer);

        Map<String, Object> payload = latestKafkaInfo();
        assertThat(payload).doesNotContainKey("duration");
        assertThat(payload).containsEntry("status", "SUCCESS");
    }

    @Test
    void valueAndKeyVisibilityFalse_omitsThoseFields() throws Exception {
        KafkaConsumerLogMessageSettings settings = new KafkaConsumerLogMessageSettings();
        settings.getVisibilitySettings().put(KafkaConfigurableField.KEY, Boolean.FALSE);
        settings.getVisibilitySettings().put(KafkaConfigurableField.VALUE, Boolean.FALSE);
        WiretapRecordInterceptor<String, String> hidden = new WiretapRecordInterceptor<>(
                new KafkaLogSink(settings, new KafkaAccessFieldNames(), null, null, null));

        ConsumerRecord<String, String> rec = record("k", "v", new RecordHeaders());
        hidden.intercept(rec, consumer);
        hidden.success(rec, consumer);

        Map<String, Object> payload = latestKafkaInfo();
        assertThat(payload).doesNotContainKeys("key", "value");
    }

    // ---- ThreadLocal cleanup ----

    @Test
    void afterRecord_clearsThreadLocal_soNextSuccessHasNoDuration() throws Exception {
        ConsumerRecord<String, String> rec = record("k", "v", new RecordHeaders());

        interceptor.intercept(rec, consumer);
        interceptor.success(rec, consumer);
        interceptor.afterRecord(rec, consumer);

        // simulate a stray success on the same thread without a preceding intercept
        interceptor.success(rec, consumer);

        // second log should have no duration field
        assertThat(appender.list).hasSize(2);
        Map<String, Object> second = MAPPER.readValue(
                appender.list.get(1).getMDCPropertyMap().get(KafkaLogSink.MDC_KEY), MAP_TYPE);
        assertThat(second).doesNotContainKey("duration");
        assertThat(second).containsEntry("status", "SUCCESS");
    }

    @Test
    void interceptReturnsSameRecord_evenWhenSinkThrows() {
        // groupMetadata throws → readGroupId returns null silently; intercept stamps start time anyway
        when(consumer.groupMetadata()).thenThrow(new IllegalStateException("boom"));
        ConsumerRecord<String, String> rec = record("k", "v", new RecordHeaders());

        ConsumerRecord<String, String> result = interceptor.intercept(rec, consumer);
        interceptor.success(rec, consumer);

        assertThat(result).isSameAs(rec);
        assertThat(appender.list).hasSize(1);
    }
}
