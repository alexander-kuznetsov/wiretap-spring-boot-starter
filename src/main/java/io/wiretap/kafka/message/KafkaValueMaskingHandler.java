package io.wiretap.kafka.message;

/**
 * SPI for masking Kafka record {@code key} / {@code value} before logging.
 * Register a Spring bean implementing this interface to activate.
 */
public interface KafkaValueMaskingHandler {

    /**
     * @param topic Kafka topic the record belongs to (useful for per-topic policy)
     * @param value stringified {@code key} or {@code value}; never {@code null}
     * @return masked text to be written to the log
     */
    String maskValue(String topic, String value);
}
