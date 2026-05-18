package io.wiretap.kafka.consumer;

import io.wiretap.kafka.KafkaLogSink;
import io.wiretap.kafka.message.KafkaMessageInfo;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.record.TimestampType;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Kafka {@link ConsumerInterceptor} that emits a {@code kafka_info} log line for
 * every received record. Invoked from {@link #onConsume} after the configured
 * {@code Deserializer} has produced typed {@code key} / {@code value}, but
 * before the records are returned to the application listener — so the log
 * carries the same object representation that the listener will see.
 *
 * <p>Kafka instantiates this class via reflection (no-arg constructor) when it
 * appears in {@code interceptor.classes}. The Spring-managed {@link KafkaLogSink}
 * is wired in lazily via {@link #setSink(KafkaLogSink)} — the interceptor stays
 * inert until the application context is up.
 *
 * <p>Stateless on purpose. Commit-time callbacks ({@link #onCommit}) are not
 * logged.
 */
public class WiretapConsumerInterceptor<K, V> implements ConsumerInterceptor<K, V> {

    private static volatile KafkaLogSink sink;

    private String clientId;
    private String groupId;

    public static void setSink(KafkaLogSink s) {
        sink = s;
    }

    @Override
    public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {
        KafkaLogSink s = sink;
        if (s == null || records == null || records.isEmpty()) return records;

        for (ConsumerRecord<K, V> record : records) {
            emitRecord(s, record);
        }
        return records;
    }

    private void emitRecord(KafkaLogSink s, ConsumerRecord<K, V> record) {
        try {
            if (!s.isTopicLogged(record.topic())) return;

            TraceContextExtractor.TraceContext trace = TraceContextExtractor.extract(record.headers());
            if (trace != null) {
                try (MDC.MDCCloseable t = MDC.putCloseable("traceId", trace.traceId());
                     MDC.MDCCloseable p = MDC.putCloseable("spanId", trace.spanId())) {
                    emitWithMessageInfo(s, record);
                }
                return;
            }
            emitWithMessageInfo(s, record);
        } catch (Exception ignored) {
            // never let logging break the consumer hot path
        }
    }

    private void emitWithMessageInfo(KafkaLogSink s, ConsumerRecord<K, V> record) {
        try {
            KafkaMessageInfo info = KafkaMessageInfo.builder()
                    .direction(KafkaMessageInfo.Direction.INCOMING)
                    .topic(record.topic())
                    .partition(record.partition())
                    .offset(record.offset())
                    .clientId(clientId)
                    .groupId(groupId)
                    .key(record.key() == null ? null : String.valueOf(record.key()))
                    .keyLength(byteLength(record.key()))
                    .value(record.value() == null ? null : String.valueOf(record.value()))
                    .valueLength(byteLength(record.value()))
                    .headers(s.collectHeaders(record.headers()))
                    .timestamp(KafkaLogSink.formatTimestamp(record.timestamp()))
                    .timestampType(timestampTypeName(record.timestampType()))
                    .build();

            s.emit(info);
        } catch (Exception ignored) {
            // never let logging break the consumer hot path
        }
    }

    @Override
    public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
        // commit-time activity is not interesting for an access log
    }

    @Override
    public void close() {
    }

    @Override
    public void configure(Map<String, ?> configs) {
        Object id = configs.get(ConsumerConfig.CLIENT_ID_CONFIG);
        if (id != null) this.clientId = id.toString();
        Object group = configs.get(ConsumerConfig.GROUP_ID_CONFIG);
        if (group != null) this.groupId = group.toString();
    }

    private static String timestampTypeName(TimestampType type) {
        return type == null || type == TimestampType.NO_TIMESTAMP_TYPE ? null : type.name();
    }

    private static long byteLength(Object o) {
        if (o == null) return 0L;
        if (o instanceof byte[] b) return b.length;
        if (o instanceof String s) return s.getBytes(StandardCharsets.UTF_8).length;
        return String.valueOf(o).getBytes(StandardCharsets.UTF_8).length;
    }
}
