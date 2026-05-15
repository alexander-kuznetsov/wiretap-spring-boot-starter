package io.wiretap.configuration;

import io.wiretap.kafka.KafkaLogSink;
import io.wiretap.kafka.message.KafkaHeaderMaskingHandler;
import io.wiretap.kafka.message.KafkaTopicMaskingHandler;
import io.wiretap.kafka.message.KafkaValueMaskingHandler;
import io.wiretap.kafka.message.settings.KafkaAccessFieldNames;
import io.wiretap.kafka.message.settings.KafkaProducerLogMessageSettings;
import io.wiretap.kafka.producer.WiretapProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

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
        KafkaLogSink sink = new KafkaLogSink(settings, names,
                valueMaskingHandler, headerMaskingHandler, topicMaskingHandler);
        WiretapProducerInterceptor.setSink(sink);
        return sink;
    }

    @Bean
    public DefaultKafkaProducerFactoryCustomizer wiretapProducerInterceptorCustomizer() {
        return factory -> factory.updateConfigs(java.util.Map.of(
                ProducerConfig.INTERCEPTOR_CLASSES_CONFIG,
                WiretapProducerInterceptor.class.getName()
        ));
    }
}
