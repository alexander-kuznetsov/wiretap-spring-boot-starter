package io.wiretap.kafka.producer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wiretap.kafka.KafkaLogSink;
import io.wiretap.kafka.message.settings.KafkaAccessFieldNames;
import io.wiretap.kafka.message.settings.KafkaInfoLogMessageSettings.KafkaConfigurableField;
import io.wiretap.kafka.message.settings.KafkaProducerLogMessageSettings;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WiretapProducerInterceptorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private WiretapProducerInterceptor<String, String> interceptor;
    private ListAppender<ILoggingEvent> appender;
    private Logger sinkLogger;

    @BeforeEach
    void setUp() {
        wireSink(new KafkaProducerLogMessageSettings(), null);
        interceptor = new WiretapProducerInterceptor<>();
        interceptor.configure(Map.of(ProducerConfig.CLIENT_ID_CONFIG, "test-producer"));

        sinkLogger = (Logger) LoggerFactory.getLogger(KafkaLogSink.class);
        appender = new ListAppender<>();
        appender.start();
        sinkLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        sinkLogger.detachAppender(appender);
        WiretapProducerInterceptor.setSink(null);
    }

    private void wireSink(KafkaProducerLogMessageSettings settings,
                          io.wiretap.kafka.message.KafkaValueMaskingHandler valueMasker) {
        WiretapProducerInterceptor.setSink(
                new KafkaLogSink(settings, new KafkaAccessFieldNames(), valueMasker, null, null));
    }

    @Test
    void onSend_emitsPreSerializationSnapshot() throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                "orders.events", null, "ord-42", "{\"orderId\":\"ord-42\"}");
        interceptor.onSend(record);

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getMessage()).isEqualTo("Captured outgoing kafka message {}");
        Map<String, Object> payload = MAPPER.readValue(
                event.getMDCPropertyMap().get(KafkaLogSink.MDC_KEY), MAP_TYPE);
        assertThat(payload)
                .containsEntry("direction", "OUTGOING")
                .containsEntry("topic", "orders.events")
                .containsEntry("client_id", "test-producer")
                .containsEntry("key", "ord-42")
                .containsEntry("value", "{\"orderId\":\"ord-42\"}");
        // pre-serialization snapshot has no broker-side info yet
        assertThat(payload).doesNotContainKeys("offset", "status", "duration");
    }

    @Test
    void onAcknowledgementWithoutException_emitsNothing() {
        // successful ack is already covered by the onSend line; no second log
        interceptor.onAcknowledgement(
                new RecordMetadata(new TopicPartition("orders.events", 3), 18472L, 0, 0L, 0, 0),
                null);
        assertThat(appender.list).isEmpty();
    }

    @Test
    void onAcknowledgementWithException_emitsErrorStatus() throws Exception {
        interceptor.onAcknowledgement(
                new RecordMetadata(new TopicPartition("orders.events", 3), -1L, 0, 0L, 0, 0),
                new IllegalStateException("kafka boom"));

        assertThat(appender.list).hasSize(1);
        Map<String, Object> payload = MAPPER.readValue(
                appender.list.get(0).getMDCPropertyMap().get(KafkaLogSink.MDC_KEY), MAP_TYPE);
        assertThat(payload)
                .containsEntry("direction", "OUTGOING")
                .containsEntry("topic", "orders.events")
                .containsEntry("partition", 3)
                .containsEntry("status", "ERROR")
                .containsEntry("error_class", "java.lang.IllegalStateException")
                .containsEntry("error_message", "kafka boom");
    }

    @Test
    void excludedTopic_skipsLogging() {
        KafkaProducerLogMessageSettings settings = new KafkaProducerLogMessageSettings();
        settings.setExcludeTopicPatterns(List.of("internal\\..*"));
        wireSink(settings, null);

        interceptor.onSend(new ProducerRecord<>("internal.metrics", "k", "v"));
        interceptor.onAcknowledgement(
                new RecordMetadata(new TopicPartition("internal.metrics", 0), 1L, 0, 0L, 0, 0),
                new IllegalStateException("boom"));

        assertThat(appender.list).isEmpty();
    }

    @Test
    void visibilityOff_omitsValue() throws Exception {
        KafkaProducerLogMessageSettings settings = new KafkaProducerLogMessageSettings();
        settings.getVisibilitySettings().put(KafkaConfigurableField.VALUE, Boolean.FALSE);
        wireSink(settings, null);

        interceptor.onSend(new ProducerRecord<>("orders.events", "k", "secret-payload"));

        Map<String, Object> payload = MAPPER.readValue(
                appender.list.get(0).getMDCPropertyMap().get(KafkaLogSink.MDC_KEY), MAP_TYPE);
        assertThat(payload).doesNotContainKey("value");
        assertThat(payload).containsEntry("key", "k");
    }

    @Test
    void wildcardHeaders_logEveryRecordHeader() throws Exception {
        KafkaProducerLogMessageSettings settings = new KafkaProducerLogMessageSettings();
        settings.setHeaders(List.of("*"));
        wireSink(settings, null);

        ProducerRecord<String, String> record = new ProducerRecord<>(
                "orders.events", null, "k", "v");
        record.headers().add("x-trace-id", "trace-1".getBytes());
        record.headers().add("x-tenant", "acme".getBytes());
        record.headers().add("x-debug", "true".getBytes());
        interceptor.onSend(record);

        Map<String, Object> payload = MAPPER.readValue(
                appender.list.get(0).getMDCPropertyMap().get(KafkaLogSink.MDC_KEY), MAP_TYPE);
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) payload.get("headers");
        assertThat(headers)
                .containsEntry("x-trace-id", "trace-1")
                .containsEntry("x-tenant", "acme")
                .containsEntry("x-debug", "true");
    }

    @Test
    void valueMasking_appliesPerHandler() throws Exception {
        KafkaProducerLogMessageSettings settings = new KafkaProducerLogMessageSettings();
        settings.getMessageBodySettings().setEnableValueMasking(true);
        wireSink(settings, (topic, value) -> "***");

        interceptor.onSend(new ProducerRecord<>("orders.events", "card-1234", "very-secret"));

        Map<String, Object> payload = MAPPER.readValue(
                appender.list.get(0).getMDCPropertyMap().get(KafkaLogSink.MDC_KEY), MAP_TYPE);
        assertThat(payload)
                .containsEntry("key", "***")
                .containsEntry("value", "***");
    }
}
