package io.wiretap.kafka.message.settings;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wiretap.kafka-consumer-interceptor")
public class KafkaConsumerLogMessageSettings extends KafkaInfoLogMessageSettings {
}
