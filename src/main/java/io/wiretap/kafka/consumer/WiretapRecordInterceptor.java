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
 * Spring Kafka {@link RecordInterceptor} that emits a single
 * {@code kafka_info INCOMING} log line per record, on the
 * {@code success(...)} / {@code failure(...)} callback. The line carries
 * the full record snapshot plus {@code duration} (ms) and
 * {@code status} (SUCCESS / ERROR), with {@code error_class} /
 * {@code error_message} when the listener threw.
 *
 * <p>The hook runs inside the Spring Kafka listener observation span,
 * so MDC carries {@code traceId} / {@code spanId} by the time we log.
 * If listener observation is disabled and MDC is empty, we fall back to
 * extracting a trace context from {@code b3} / {@code traceparent}
 * record headers via {@link TraceContextExtractor}.
 *
 * <p>{@code intercept(...)} only stamps the start time into a
 * {@link ThreadLocal} (one {@code long} — safe to use under virtual
 * threads). The whole {@code intercept → listener → success/failure}
 * sequence runs on a single Spring Kafka listener thread, so no
 * cross-thread propagation is needed; {@code afterRecord(...)} and
 * {@code clearThreadState(...)} clean the slot.
 *
 * <p>If the listener hangs past {@code max.poll.interval.ms}, Spring
 * Kafka still raises an exception that ends up in {@code failure(...)},
 * so the log line is not lost. The only sliver of latency we cannot
 * cover is a JVM crash mid-processing.
 */
public class WiretapRecordInterceptor<K, V> implements RecordInterceptor<K, V> {

    private final KafkaLogSink sink;
    private final ThreadLocal<RecordContext> contextHolder = new ThreadLocal<>();

    public WiretapRecordInterceptor(KafkaLogSink sink) {
        this.sink = sink;
    }

    @Override
    public ConsumerRecord<K, V> intercept(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
        try {
            if (record != null && sink.isTopicLogged(record.topic())) {
                contextHolder.set(new RecordContext(System.nanoTime()));
            }
        } catch (Exception ignored) {
            // never break the consumer hot path
        }
        return record;
    }

    @Override
    public void success(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
        emitProcessed(record, consumer, KafkaMessageInfo.Status.SUCCESS, null);
    }

    @Override
    public void failure(ConsumerRecord<K, V> record, Exception exception, Consumer<K, V> consumer) {
        emitProcessed(record, consumer, KafkaMessageInfo.Status.ERROR, exception);
    }

    @Override
    public void afterRecord(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
        contextHolder.remove();
    }

    @Override
    public void clearThreadState(Consumer<?, ?> consumer) {
        contextHolder.remove();
    }

    private void emitProcessed(ConsumerRecord<K, V> record, Consumer<K, V> consumer,
                               KafkaMessageInfo.Status status, Exception exception) {
        try {
            if (record == null || !sink.isTopicLogged(record.topic())) return;

            RecordContext ctx = contextHolder.get();
            Long duration = ctx == null ? null : (System.nanoTime() - ctx.startNanos) / 1_000_000L;

            if (MDC.get("traceId") == null) {
                TraceContextExtractor.TraceContext trace = TraceContextExtractor.extract(record.headers());
                if (trace != null) {
                    try (MDC.MDCCloseable t = MDC.putCloseable("traceId", trace.traceId());
                         MDC.MDCCloseable p = MDC.putCloseable("spanId", trace.spanId())) {
                        emit(record, consumer, status, exception, duration);
                    }
                    return;
                }
            }
            emit(record, consumer, status, exception, duration);
        } catch (Exception ignored) {
            // never break the consumer hot path
        }
    }

    private void emit(ConsumerRecord<K, V> record, Consumer<K, V> consumer,
                      KafkaMessageInfo.Status status, Exception exception, Long duration) {
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
                .duration(duration)
                .status(status)
                .errorClass(exception == null ? null : exception.getClass().getName())
                .errorMessage(exception == null ? null : exception.getMessage())
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

    private record RecordContext(long startNanos) {}
}
