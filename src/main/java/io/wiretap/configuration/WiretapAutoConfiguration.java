package io.wiretap.configuration;

import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.body.BodyParserConfiguration;
import io.wiretap.metrics.NoOpWiretapMetrics;
import io.wiretap.metrics.WiretapMetrics;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

@Configuration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@EnableConfigurationProperties(WiretapAccessLogFieldsProperties.class)
@Import({
        BodyParserConfiguration.class,
        MessageMaskingConfiguration.class,
        IncomingHttpConfiguration.class,
        OutgoingRestTemplateConfiguration.class,
        OutgoingRestClientConfiguration.class,
        OutgoingFeignClientConfiguration.class,
        OutgoingWebServiceTemplateConfiguration.class,
        OutgoingKafkaProducerConfiguration.class,
        IncomingKafkaConsumerConfiguration.class,
        WiretapAccessLogConfiguration.class,
        WiretapAppLogConfiguration.class,
        WiretapTracingConfiguration.class,
        WiretapMetricsConfiguration.class
})
public class WiretapAutoConfiguration {

    @Bean
    public HttpAccessFieldNames httpAccessFieldNames(WiretapAccessLogFieldsProperties fieldNames) {
        return fieldNames.getHttp();
    }

    /**
     * Fallback {@link WiretapMetrics} bean installed when no
     * {@code MeterRegistry} is present in the context, when
     * {@code io.micrometer:micrometer-core} is absent from the classpath, or
     * when the operator has set {@code wiretap.metrics.enabled=false}.
     * Interceptors can therefore inject {@link WiretapMetrics} unconditionally.
     */
    @Bean
    @ConditionalOnMissingBean(WiretapMetrics.class)
    public WiretapMetrics noOpWiretapMetrics() {
        return new NoOpWiretapMetrics();
    }
}
