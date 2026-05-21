package io.wiretap.configuration;

import io.wiretap.kafka.KafkaLogSink;
import io.wiretap.kafka.consumer.WiretapRecordInterceptor;
import io.wiretap.kafka.message.KafkaHeaderMaskingHandler;
import io.wiretap.kafka.message.KafkaTopicMaskingHandler;
import io.wiretap.kafka.message.KafkaValueMaskingHandler;
import io.wiretap.kafka.message.settings.KafkaAccessFieldNames;
import io.wiretap.kafka.message.settings.KafkaConsumerLogMessageSettings;
import io.wiretap.metrics.WiretapMetrics;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ContainerCustomizer;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.RecordInterceptor;

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
            @Autowired(required = false) KafkaTopicMaskingHandler topicMaskingHandler,
            WiretapMetrics metrics
    ) {
        KafkaAccessFieldNames names = fieldNames.getKafka();
        return new KafkaLogSink(settings, names,
                valueMaskingHandler, headerMaskingHandler, topicMaskingHandler, metrics);
    }

    @Bean
    public RecordInterceptor<Object, Object> wiretapRecordInterceptor(
            @Qualifier("wiretapConsumerLogSink") KafkaLogSink consumerLogSink) {
        return new WiretapRecordInterceptor<>(consumerLogSink);
    }

    /**
     * Applied automatically to the Spring Boot auto-configured
     * {@code ConcurrentKafkaListenerContainerFactory} via
     * {@code factory.setContainerCustomizer(...)}. For manually constructed
     * factories (multi-cluster setups, custom listeners), inject this
     * customizer with {@code ObjectProvider} and call
     * {@code factory.setContainerCustomizer(...)} explicitly — README has
     * a snippet.
     *
     * <p>If the application already configures its own {@link RecordInterceptor}
     * on a container (e.g. for retry-state cleanup), combine it with this one
     * using {@link org.springframework.kafka.listener.CompositeRecordInterceptor}.
     */
    @Bean
    public ContainerCustomizer<Object, Object, ConcurrentMessageListenerContainer<Object, Object>>
            wiretapListenerContainerCustomizer(RecordInterceptor<Object, Object> wiretapRecordInterceptor) {
        return container -> container.setRecordInterceptor(wiretapRecordInterceptor);
    }
}
