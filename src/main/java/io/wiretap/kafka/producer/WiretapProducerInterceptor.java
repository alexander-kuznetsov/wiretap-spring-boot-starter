package io.wiretap.kafka.producer;

import io.wiretap.kafka.KafkaLogSink;
import io.wiretap.kafka.message.KafkaMessageInfo;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Kafka {@link ProducerInterceptor} that emits the structured
 * {@code kafka_info} log line at two well-defined points:
 *
 * <ul>
 *   <li>{@link #onSend} — before serialization. Captures the typed {@code key} /
 *       {@code value} as the application produced them, plus configured headers,
 *       partition (if pinned) and timestamp (if set on the record). Status is
 *       not yet known, so the line carries only the snapshot.</li>
 *   <li>{@link #onAcknowledgement} — emits a second line <em>only</em> when the
 *       broker reports an error, with {@code status=ERROR} plus error class /
 *       message and the metadata's partition/offset if available. Successful
 *       acknowledgements are already covered by the {@code onSend} line.</li>
 * </ul>
 *
 * <p>The class is instantiated by Kafka through reflection (no-arg constructor
 * via {@code interceptor.classes}). The Spring-managed {@link KafkaLogSink} is
 * wired in lazily via {@link #setSink(KafkaLogSink)} so the interceptor stays
 * inert until the application context is up.
 *
 * <p>Stateless on purpose — works identically on platform and virtual threads.
 */
public class WiretapProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {

    private static volatile KafkaLogSink sink;

    private String clientId;

    public static void setSink(KafkaLogSink s) {
        sink = s;
    }

    @Override
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
        KafkaLogSink s = sink;
        if (s == null || record == null) return record;

        try {
            if (!s.isTopicLogged(record.topic())) return record;

            KafkaMessageInfo info = KafkaMessageInfo.builder()
                    .direction(KafkaMessageInfo.Direction.OUTGOING)
                    .topic(record.topic())
                    .partition(record.partition())
                    .clientId(clientId)
                    .key(record.key() == null ? null : String.valueOf(record.key()))
                    .keyLength(byteLength(record.key()))
                    .value(record.value() == null ? null : String.valueOf(record.value()))
                    .valueLength(byteLength(record.value()))
                    .headers(s.collectHeaders(record.headers()))
                    .timestamp(record.timestamp() == null ? null : KafkaLogSink.formatTimestamp(record.timestamp()))
                    .build();

            s.emit(info);
        } catch (Exception ignored) {
            // never let logging break the producer hot path
        }
        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        KafkaLogSink s = sink;
        if (s == null || exception == null) return;

        try {
            String topic = metadata != null ? metadata.topic() : null;
            if (topic != null && !s.isTopicLogged(topic)) return;

            KafkaMessageInfo info = KafkaMessageInfo.builder()
                    .direction(KafkaMessageInfo.Direction.OUTGOING)
                    .topic(topic)
                    .partition(metadata != null ? metadata.partition() : null)
                    .offset(metadata != null && metadata.hasOffset() ? metadata.offset() : null)
                    .clientId(clientId)
                    .timestamp(metadata != null && metadata.hasTimestamp()
                            ? KafkaLogSink.formatTimestamp(metadata.timestamp()) : null)
                    .status(KafkaMessageInfo.Status.ERROR)
                    .errorClass(exception.getClass().getName())
                    .errorMessage(exception.getMessage())
                    .build();
            s.emit(info);
        } catch (Exception ignored) {
            // never let logging break the producer hot path
        }
    }

    @Override
    public void close() {
    }

    @Override
    public void configure(Map<String, ?> configs) {
        Object id = configs.get(ProducerConfig.CLIENT_ID_CONFIG);
        if (id != null) this.clientId = id.toString();
    }

    private static long byteLength(Object o) {
        if (o == null) return 0L;
        if (o instanceof byte[] b) return b.length;
        if (o instanceof String s) return s.getBytes(StandardCharsets.UTF_8).length;
        return String.valueOf(o).getBytes(StandardCharsets.UTF_8).length;
    }
}
