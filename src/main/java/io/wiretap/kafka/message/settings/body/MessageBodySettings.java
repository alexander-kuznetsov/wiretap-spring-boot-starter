package io.wiretap.kafka.message.settings.body;

import lombok.Data;

/**
 * Truncation / masking knobs for Kafka message key and value rendered into the log.
 * Mirrors {@code HttpBodySettings} for HTTP traffic.
 */
@Data
public class MessageBodySettings {
    private boolean enableValueMasking = false;
    private boolean enableValueTruncating = false;
    private int maxValueLength = 2000;
}
