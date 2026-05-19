package io.wiretap.kafka.consumer;

import io.wiretap.kafka.KafkaLogSink;
import io.wiretap.kafka.message.KafkaMessageInfo;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.record.TimestampType;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * Spring Kafka {@link RecordInterceptor} that emits the {@code kafka_info}
 * log line for every received record. Replaces the older Kafka-native
 * {@code ConsumerInterceptor}-based approach because RecordInterceptor
 * runs <em>inside</em> the Spring Kafka listener observation span —
 * MDC carries {@code traceId} / {@code spanId} by the time we log, so
 * the consumer-side line correlates with the {@code @KafkaListener}
 * processing without manual header parsing.
 *
 * <p>If listener observation is disabled (i.e. MDC is empty when this
 * runs), we still try to extract a trace context from {@code b3} or
 * {@code traceparent} headers via {@link TraceContextExtractor} so the
 * log line is not orphaned when an upstream producer did propagate.
 *
 * <p>Stateless across threads — a single instance is shared by all
 * Spring Kafka listener containers via {@code ContainerCustomizer}.
 */
public class WiretapRecordInterceptor<K, V> implements RecordInterceptor<K, V> {

    private final KafkaLogSink sink;

    public WiretapRecordInterceptor(KafkaLogSink sink) {
        this.sink = sink;
    }

    @Override
    public ConsumerRecord<K, V> intercept(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
        try {
            if (record == null || !sink.isTopicLogged(record.topic())) return record;

            if (MDC.get("traceId") == null) {
                TraceContextExtractor.TraceContext trace = TraceContextExtractor.extract(record.headers());
                if (trace != null) {
                    try (MDC.MDCCloseable t = MDC.putCloseable("traceId", trace.traceId());
                         MDC.MDCCloseable p = MDC.putCloseable("spanId", trace.spanId())) {
                        emit(record, consumer);
                    }
                    return record;
                }
            }
            emit(record, consumer);
        } catch (Exception ignored) {
            // never break the consumer hot path
        }
        return record;
    }

    private void emit(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
        KafkaMessageInfo info = KafkaMessageInfo.builder()
                .direction(KafkaMessageInfo.Direction.INCOMING)
                .topic(record.topic())
                .partition(record.partition())
                .offset(record.offset())
                .clientId(readClientId(consumer))
                .groupId(readGroupId(consumer))
                .key(record.key() == null ? null : String.valueOf(record.key()))
                .keyLength(byteLength(record.key()))
                .value(record.value() == null ? null : String.valueOf(record.value()))
                .valueLength(byteLength(record.value()))
                .headers(sink.collectHeaders(record.headers()))
                .timestamp(KafkaLogSink.formatTimestamp(record.timestamp()))
                .timestampType(timestampTypeName(record.timestampType()))
                .build();
        sink.emit(info);
    }

    private static String readClientId(Consumer<?, ?> consumer) {
        if (consumer == null) return null;
        try {
            var metrics = consumer.metrics();
            if (metrics == null || metrics.isEmpty()) return null;
            MetricName any = metrics.keySet().iterator().next();
            return any.tags().get("client-id");
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String readGroupId(Consumer<?, ?> consumer) {
        if (consumer == null) return null;
        try {
            return consumer.groupMetadata().groupId();
        } catch (Exception ignored) {
            return null;
        }
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
