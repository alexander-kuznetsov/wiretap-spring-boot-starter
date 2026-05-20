package io.wiretap.integrationtests.masking;

import io.wiretap.kafka.message.KafkaValueMaskingHandler;
import org.springframework.stereotype.Component;

/**
 * Masks {@code value} for any topic in the {@code secrets.*} namespace.
 * Demonstrates per-topic policies via {@link KafkaValueMaskingHandler}.
 */
@Component
public class SensitiveTopicValueMaskingHandler implements KafkaValueMaskingHandler {

    @Override
    public String maskValue(String topic, String value) {
        if (topic != null && topic.startsWith("secrets.")) {
            return "***";
        }
        return value;
    }
}
