package io.wiretap.configuration;

import io.wiretap.kafka.KafkaLogSink;
import io.wiretap.kafka.consumer.WiretapConsumerInterceptor;
import io.wiretap.kafka.message.KafkaHeaderMaskingHandler;
import io.wiretap.kafka.message.KafkaTopicMaskingHandler;
import io.wiretap.kafka.message.KafkaValueMaskingHandler;
import io.wiretap.kafka.message.settings.KafkaAccessFieldNames;
import io.wiretap.kafka.message.settings.KafkaConsumerLogMessageSettings;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaConsumerFactoryCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

@Configuration
@ConditionalOnClass({KafkaListenerEndpointRegistry.class, ConsumerConfig.class})
@ConditionalOnProperty(name = "wiretap.kafka-consumer-interceptor.enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KafkaConsumerLogMessageSettings.class)
public class IncomingKafkaConsumerConfiguration {

    @Bean
    public KafkaLogSink wiretapConsumerLogSink(
            KafkaConsumerLogMessageSettings settings,
            WiretapAccessLogFieldsProperties fieldNames,
            @Autowired(required = false) KafkaValueMaskingHandler valueMaskingHandler,
            @Autowired(required = false) KafkaHeaderMaskingHandler headerMaskingHandler,
            @Autowired(required = false) KafkaTopicMaskingHandler topicMaskingHandler
    ) {
        KafkaAccessFieldNames names = fieldNames.getKafka();
        KafkaLogSink sink = new KafkaLogSink(settings, names,
                valueMaskingHandler, headerMaskingHandler, topicMaskingHandler);
        WiretapConsumerInterceptor.setSink(sink);
        return sink;
    }

    @Bean
    public DefaultKafkaConsumerFactoryCustomizer wiretapConsumerInterceptorCustomizer() {
        return factory -> factory.updateConfigs(java.util.Map.of(
                ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG,
                WiretapConsumerInterceptor.class.getName()
        ));
    }
}
