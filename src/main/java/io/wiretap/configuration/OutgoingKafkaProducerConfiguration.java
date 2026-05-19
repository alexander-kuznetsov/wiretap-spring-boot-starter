package io.wiretap.configuration;

import io.wiretap.kafka.KafkaLogSink;
import io.wiretap.kafka.message.KafkaHeaderMaskingHandler;
import io.wiretap.kafka.message.KafkaTopicMaskingHandler;
import io.wiretap.kafka.message.KafkaValueMaskingHandler;
import io.wiretap.kafka.message.settings.KafkaAccessFieldNames;
import io.wiretap.kafka.message.settings.KafkaProducerLogMessageSettings;
import io.wiretap.kafka.producer.WiretapProducerListener;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.support.ProducerListener;

@Configuration
@ConditionalOnClass({KafkaListenerEndpointRegistry.class, ProducerConfig.class})
@ConditionalOnProperty(name = "wiretap.kafka-producer-interceptor.enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KafkaProducerLogMessageSettings.class)
public class OutgoingKafkaProducerConfiguration {

    @Bean
    public KafkaLogSink wiretapProducerLogSink(
            KafkaProducerLogMessageSettings settings,
            WiretapAccessLogFieldsProperties fieldNames,
            @Autowired(required = false) KafkaValueMaskingHandler valueMaskingHandler,
            @Autowired(required = false) KafkaHeaderMaskingHandler headerMaskingHandler,
            @Autowired(required = false) KafkaTopicMaskingHandler topicMaskingHandler
    ) {
        KafkaAccessFieldNames names = fieldNames.getKafka();
        return new KafkaLogSink(settings, names,
                valueMaskingHandler, headerMaskingHandler, topicMaskingHandler);
    }

    /**
     * Spring Boot's auto-configured {@code KafkaTemplate} picks up
     * {@link ProducerListener} beans from the context through
     * {@code ObjectProvider} and attaches them automatically. Manually
     * constructed templates (multi-cluster setups) need an explicit
     * {@code template.setProducerListener(...)} — README has a snippet.
     */
    @Bean
    public ProducerListener<Object, Object> wiretapProducerListener(
            @Qualifier("wiretapProducerLogSink") KafkaLogSink producerLogSink) {
        return new WiretapProducerListener(producerLogSink);
    }
}
