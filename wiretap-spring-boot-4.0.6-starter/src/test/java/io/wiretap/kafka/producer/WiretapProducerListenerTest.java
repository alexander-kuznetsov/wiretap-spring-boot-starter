package io.wiretap.kafka.producer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.wiretap.kafka.KafkaLogSink;
import io.wiretap.kafka.message.KafkaValueMaskingHandler;
import io.wiretap.kafka.message.settings.KafkaAccessFieldNames;
import io.wiretap.kafka.message.settings.KafkaInfoLogMessageSettings.KafkaConfigurableField;
import io.wiretap.kafka.message.settings.KafkaProducerLogMessageSettings;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WiretapProducerListenerTest {

    private static final ObjectMapper MAPPER = tools.jackson.databind.json.JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private WiretapProducerListener listener;
    private ListAppender<ILoggingEvent> appender;
    private Logger sinkLogger;

    @BeforeEach
    void setUp() {
        listener = new WiretapProducerListener(
                new KafkaLogSink(new KafkaProducerLogMessageSettings(), new KafkaAccessFieldNames(), null, null, null, new io.wiretap.metrics.NoOpWiretapMetrics()));

        sinkLogger = (Logger) LoggerFactory.getLogger(KafkaLogSink.class);
        appender = new ListAppender<>();
        appender.start();
        sinkLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        sinkLogger.detachAppender(appender);
    }

    private ProducerRecord<Object, Object> producerRecord(String key, String value) {
        RecordHeaders headers = new RecordHeaders();
        headers.add(new RecordHeader("x-trace-id", "abc".getBytes(StandardCharsets.UTF_8)));
        return new ProducerRecord<>("orders.events", null, 1700000000000L, key, (Object) value, headers);
    }

    private RecordMetadata metadata(int partition, long offset) {
        return new RecordMetadata(
                new TopicPartition("orders.events", partition), offset, 0, 1700000000000L, 0, 0);
    }

    private Map<String, Object> latestKafkaInfo() throws Exception {
        return MAPPER.readValue(
                appender.list.get(appender.list.size() - 1).getMDCPropertyMap().get(KafkaLogSink.MDC_KEY), MAP_TYPE);
    }

    @Test
    void onSuccess_emitsInfoLogWithSuccessStatusBrokerFields() throws Exception {
        listener.onSuccess(producerRecord("ord-42", "{\"orderId\":\"ord-42\"}"), metadata(3, 18472L));

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).isEqualTo("Sent outgoing kafka message orders.events");

        Map<String, Object> payload = latestKafkaInfo();
        assertThat(payload)
                .containsEntry("direction", "OUTGOING")
                .containsEntry("topic", "orders.events")
                .containsEntry("partition", 3)
                .containsEntry("offset", 18472)
                .containsEntry("key", "ord-42")
                .containsEntry("status", "SUCCESS");
        JsonNode valueTree = MAPPER.readTree((String) payload.get("value"));
        assertThat(valueTree.get("orderId").asString()).isEqualTo("ord-42");
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) payload.get("headers");
        assertThat(headers).containsEntry("x-trace-id", "abc");
    }

    @Test
    void onError_emitsWarnWithErrorStatusAndErrorFields() throws Exception {
        IllegalStateException boom = new IllegalStateException("broker unreachable");

        listener.onError(producerRecord("ord-42", "{}"), null, boom);

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).isEqualTo("Failed to send outgoing kafka message orders.events");

        Map<String, Object> payload = latestKafkaInfo();
        assertThat(payload)
                .containsEntry("status", "ERROR")
                .containsEntry("error_class", "java.lang.IllegalStateException")
                .containsEntry("error_message", "broker unreachable");
    }

    @Test
    void onSuccess_skipsExcludedTopic() {
        KafkaProducerLogMessageSettings settings = new KafkaProducerLogMessageSettings();
        settings.setExcludeTopicPatterns(List.of("orders\\..*"));
        WiretapProducerListener filtered = new WiretapProducerListener(
                new KafkaLogSink(settings, new KafkaAccessFieldNames(), null, null, null, new io.wiretap.metrics.NoOpWiretapMetrics()));

        filtered.onSuccess(producerRecord("k", "v"), metadata(0, 1L));

        assertThat(appender.list).isEmpty();
    }

    @Test
    void onSuccess_visibilityOff_omitsKeyAndValue() throws Exception {
        KafkaProducerLogMessageSettings settings = new KafkaProducerLogMessageSettings();
        settings.getVisibilitySettings().put(KafkaConfigurableField.KEY, Boolean.FALSE);
        settings.getVisibilitySettings().put(KafkaConfigurableField.VALUE, Boolean.FALSE);
        WiretapProducerListener hidden = new WiretapProducerListener(
                new KafkaLogSink(settings, new KafkaAccessFieldNames(), null, null, null, new io.wiretap.metrics.NoOpWiretapMetrics()));

        hidden.onSuccess(producerRecord("k", "v"), metadata(0, 1L));

        Map<String, Object> payload = latestKafkaInfo();
        assertThat(payload).doesNotContainKeys("key", "value");
        assertThat(payload).containsEntry("status", "SUCCESS");
    }

    @Test
    void valueIsJsonObject_isPrettyPrintedWithNewlines() throws Exception {
        listener.onSuccess(producerRecord("k", "{\"id\":42,\"name\":\"a\"}"), metadata(0, 1L));

        String value = (String) latestKafkaInfo().get("value");
        assertThat(value).contains("\n");
        JsonNode tree = MAPPER.readTree(value);
        assertThat(tree.get("id").asInt()).isEqualTo(42);
        assertThat(tree.get("name").asString()).isEqualTo("a");
    }

    @Test
    void keyIsJsonObject_isPrettyPrintedWithNewlines() throws Exception {
        listener.onSuccess(producerRecord("{\"k\":1}", "v"), metadata(0, 1L));

        String key = (String) latestKafkaInfo().get("key");
        assertThat(key).contains("\n");
        JsonNode tree = MAPPER.readTree(key);
        assertThat(tree.get("k").asInt()).isEqualTo(1);
    }

    @Test
    void valueIsJsonArrayOfObjects_isPrettyPrintedWithNewlines() throws Exception {
        listener.onSuccess(producerRecord("k", "[{\"x\":1},{\"x\":2}]"), metadata(0, 1L));

        String value = (String) latestKafkaInfo().get("value");
        assertThat(value).contains("\n");
        JsonNode tree = MAPPER.readTree(value);
        assertThat(tree.isArray()).isTrue();
        assertThat(tree).hasSize(2);
    }

    @Test
    void valueIsPlainString_isLoggedAsIs() throws Exception {
        listener.onSuccess(producerRecord("k", "not-json"), metadata(0, 1L));

        assertThat(latestKafkaInfo().get("value")).isEqualTo("not-json");
    }

    @Test
    void valueIsMalformedJson_isLoggedAsIs() throws Exception {
        String malformed = "{\"a\":";
        listener.onSuccess(producerRecord("k", malformed), metadata(0, 1L));

        assertThat(latestKafkaInfo().get("value")).isEqualTo(malformed);
    }

    @Test
    void valueIsJsonScalar_isLoggedAsIs() throws Exception {
        listener.onSuccess(producerRecord("k", "42"), metadata(0, 1L));
        assertThat(latestKafkaInfo().get("value")).isEqualTo("42");
    }

    @Test
    void prettyPrintBeforeTruncating_truncatesPrettyString() throws Exception {
        KafkaProducerLogMessageSettings settings = new KafkaProducerLogMessageSettings();
        settings.getMessageBodySettings().setEnableValueTruncating(true);
        settings.getMessageBodySettings().setMaxValueLength(20);
        WiretapProducerListener truncating = new WiretapProducerListener(
                new KafkaLogSink(settings, new KafkaAccessFieldNames(), null, null, null, new io.wiretap.metrics.NoOpWiretapMetrics()));

        truncating.onSuccess(producerRecord("k", "{\"id\":42,\"name\":\"alice\",\"city\":\"berlin\"}"), metadata(0, 1L));

        String value = (String) latestKafkaInfo().get("value");
        assertThat(value).endsWith("...[truncated]");
        assertThat(value).hasSize(20 + "...[truncated]".length());
    }

    @Test
    void maskingBeforePrettyPrint_handlerSeesSingleLineInput() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> seenByHandler = new java.util.concurrent.atomic.AtomicReference<>();
        KafkaValueMaskingHandler handler = (topic, value) -> {
            seenByHandler.set(value);
            return value;
        };
        KafkaProducerLogMessageSettings settings = new KafkaProducerLogMessageSettings();
        settings.getMessageBodySettings().setEnableValueMasking(true);
        WiretapProducerListener masked = new WiretapProducerListener(
                new KafkaLogSink(settings, new KafkaAccessFieldNames(), handler, null, null, new io.wiretap.metrics.NoOpWiretapMetrics()));

        String singleLine = "{\"id\":42}";
        masked.onSuccess(producerRecord("k", singleLine), metadata(0, 1L));

        assertThat(seenByHandler.get()).isEqualTo(singleLine);
        assertThat((String) latestKafkaInfo().get("value")).contains("\n");
    }
}
