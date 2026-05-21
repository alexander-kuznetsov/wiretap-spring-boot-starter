package io.wiretap.kafka.producer;

import io.wiretap.kafka.KafkaLogSink;
import io.wiretap.kafka.message.KafkaMessageInfo;
import io.wiretap.metrics.WiretapMetrics;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.support.ProducerListener;

import java.nio.charset.StandardCharsets;

/**
 * Spring Kafka {@link ProducerListener} that emits a single
 * {@code kafka_info OUTGOING} log line per record on
 * {@code onSuccess(...)} / {@code onError(...)}. Carries the full
 * record snapshot (key/value/headers as the application produced them)
 * plus broker-side fields from {@link RecordMetadata}
 * (partition/offset/timestamp), and a {@code status} of
 * {@code SUCCESS} or {@code ERROR} with {@code error_class} /
 * {@code error_message} when the send failed.
 *
 * <p>No {@code duration} — measuring it would require coordinating
 * pre/post callbacks across threads (the ack callback runs on the
 * producer I/O thread, not the caller's). Kafka's own JMX/Micrometer
 * metrics cover producer latency through
 * {@code record-queue-time-avg} / {@code request-latency-avg} instead.
 *
 * <p>Spring Boot's auto-configured {@code KafkaTemplate} picks up
 * registered {@link ProducerListener} beans automatically through
 * {@code ObjectProvider}. Manually constructed templates need an
 * explicit {@code template.setProducerListener(...)} — see README.
 */
public class WiretapProducerListener implements ProducerListener<Object, Object> {

    private static final String DIRECTION = "producer";

    private final KafkaLogSink sink;
    private final WiretapMetrics metrics;

    public WiretapProducerListener(KafkaLogSink sink) {
        this.sink = sink;
        this.metrics = sink.getMetrics();
    }

    @Override
    public void onSuccess(ProducerRecord<Object, Object> record, RecordMetadata metadata) {
        emit(record, metadata, KafkaMessageInfo.Status.SUCCESS, null);
    }

    @Override
    public void onError(ProducerRecord<Object, Object> record, RecordMetadata metadata, Exception exception) {
        emit(record, metadata, KafkaMessageInfo.Status.ERROR, exception);
    }

    private void emit(ProducerRecord<Object, Object> record, RecordMetadata metadata,
                      KafkaMessageInfo.Status status, Exception exception) {
        if (record == null) return;
        long startNanos = metrics.startSample();
        try {
            if (!sink.isTopicLogged(record.topic())) {
                metrics.recordKafkaSkipped(DIRECTION, "exclude_topic");
                return;
            }

            Integer partition = record.partition();
            if (metadata != null) {
                try {
                    if (metadata.hasOffset()) partition = metadata.partition();
                } catch (Throwable ignored) {
                    // hasOffset/partition may throw on partially-built RecordMetadata
                }
            }
            Long offset = null;
            if (metadata != null) {
                try {
                    if (metadata.hasOffset()) offset = metadata.offset();
                } catch (Throwable ignored) { }
            }
            String timestamp = null;
            if (record.timestamp() != null) {
                timestamp = KafkaLogSink.formatTimestamp(record.timestamp());
            } else if (metadata != null) {
                try {
                    if (metadata.hasTimestamp()) {
                        timestamp = KafkaLogSink.formatTimestamp(metadata.timestamp());
                    }
                } catch (Throwable ignored) { }
            }

            KafkaMessageInfo info = KafkaMessageInfo.builder()
                    .direction(KafkaMessageInfo.Direction.OUTGOING)
                    .topic(record.topic())
                    .partition(partition)
                    .offset(offset)
                    .key(record.key() == null ? null : String.valueOf(record.key()))
                    .keyLength(byteLength(record.key()))
                    .value(record.value() == null ? null : String.valueOf(record.value()))
                    .valueLength(byteLength(record.value()))
                    .headers(sink.collectHeaders(record.headers()))
                    .timestamp(timestamp)
                    .status(status)
                    .errorClass(exception == null ? null : exception.getClass().getName())
                    .errorMessage(exception == null ? null : exception.getMessage())
                    .build();
            sink.emit(info);
            metrics.recordKafkaMessage(startNanos, DIRECTION,
                    status == KafkaMessageInfo.Status.SUCCESS ? "success" : "error", record.topic());
        } catch (Exception ignored) {
            // never break the producer hot path
        }
    }

    private static long byteLength(Object o) {
        if (o == null) return 0L;
        if (o instanceof byte[] b) return b.length;
        if (o instanceof String s) return s.getBytes(StandardCharsets.UTF_8).length;
        return String.valueOf(o).getBytes(StandardCharsets.UTF_8).length;
    }
}
