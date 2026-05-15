package io.wiretap.kafka.message;

/**
 * SPI for masking the Kafka topic name before logging. Useful when topic names
 * embed sensitive identifiers (e.g. multi-tenant {@code tenant-<id>.events}).
 */
public interface KafkaTopicMaskingHandler {

    /**
     * @param topic original topic name
     * @return masked topic name to be written to the log
     */
    String maskTopic(String topic);
}
