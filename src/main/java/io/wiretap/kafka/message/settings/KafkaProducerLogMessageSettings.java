package io.wiretap.kafka.message.settings;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wiretap.kafka-producer-interceptor")
public class KafkaProducerLogMessageSettings extends KafkaInfoLogMessageSettings {
}
