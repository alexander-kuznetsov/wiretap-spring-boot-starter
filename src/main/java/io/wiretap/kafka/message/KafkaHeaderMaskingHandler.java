package io.wiretap.kafka.message;

/**
 * SPI for masking Kafka record header values before logging.
 * Register a Spring bean implementing this interface to activate.
 */
public interface KafkaHeaderMaskingHandler {

    /**
     * @param topic Kafka topic the record belongs to
     * @param name  header name (e.g. {@code "x-auth-token"})
     * @param value original header value rendered as UTF-8 string
     * @return masked value to be written to the log
     */
    String maskHeaderValue(String topic, String name, String value);
}
