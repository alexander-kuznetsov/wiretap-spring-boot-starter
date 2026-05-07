package io.wiretap.configuration;

import brave.Tracing;
import brave.propagation.B3Propagation;
import feign.Client;
import io.micrometer.tracing.Tracer;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import io.wiretap.http.incoming.filter.AccessLogTraceIdForwarder;
import io.wiretap.http.incoming.filter.BufferedHttpBodyThreadCleaner;
import io.wiretap.http.incoming.interceptor.CorrelationHeadersMdcForwarder;
import io.wiretap.http.incoming.filter.ExtraRequestInfoThreadCleaner;
import io.wiretap.http.incoming.provider.httpinfo.HttpInfoMessageProvider;
import io.wiretap.http.incoming.provider.message.MessageProvider;
import io.wiretap.http.incoming.provider.operationinfo.ExtraRequestInfoProvider;
import io.wiretap.http.message.settings.*;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.body.BodyParser;
import io.wiretap.http.message.settings.body.BodyParserConfiguration;
import io.wiretap.http.outgoing.interceptor.feignclient.FeignClientWrapper;
import io.wiretap.http.outgoing.interceptor.rest.RestClientLoggingInterceptor;
import io.wiretap.http.outgoing.interceptor.rest.RestTemplateLoggingInterceptor;
import io.wiretap.http.outgoing.interceptor.webservicetemplate.WebServiceTemplateLoggingInterceptor;

@Configuration
// Highest precedence ensures Wiretap's beans are created before other Spring
// auto-configurations. For example, Spring Boot Actuator declares
// `restTemplateExchangeTagsProvider` with `@ConditionalOnMissingBean`, so the
// equivalent bean from this configuration must already be in context when
// Actuator is initialised.
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@ComponentScan(basePackageClasses = {
        HttpInfoMessageProvider.class,
        RestTemplateLogMessageSettings.class,
        RestClientLogMessageSettings.class,
        RestControllerLogMessageSettings.class,
        RestTemplateLoggingInterceptor.class,
        RestClientLoggingInterceptor.class,
        WebServiceTemplateLogMessageSettings.class,
        WebServiceTemplateLoggingInterceptor.class,
        MessageProvider.class,
        ExtraRequestInfoProvider.class,
        WiretapHeadersProperties.class
})
@Import(value = {BodyParserConfiguration.class, MessageMaskingConfiguration.class, WiretapFieldProvidersInit.class})
public class LoggerConfiguration implements WebMvcConfigurer {

    private final WiretapHeadersProperties headersProperties;

    public LoggerConfiguration(WiretapHeadersProperties headersProperties) {
        this.headersProperties = headersProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CorrelationHeadersMdcForwarder(headersProperties));
    }

    @Bean
    public FilterRegistrationBean<AccessLogTraceIdForwarder> sleuthAttrsFilter(Tracer tracer) {
        AccessLogTraceIdForwarder filter = new AccessLogTraceIdForwarder(tracer);
        return new FilterRegistrationBean<>(filter);
    }

    @Bean
    public FilterRegistrationBean<ExtraRequestInfoThreadCleaner> operationContextCleanFilter() {
        ExtraRequestInfoThreadCleaner filter = new ExtraRequestInfoThreadCleaner();
        FilterRegistrationBean<ExtraRequestInfoThreadCleaner> bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<BufferedHttpBodyThreadCleaner> errorResponseCleanFilter() {
        BufferedHttpBodyThreadCleaner filter = new BufferedHttpBodyThreadCleaner();
        FilterRegistrationBean<BufferedHttpBodyThreadCleaner> bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
    /**
     * Customises the auto-configured {@code RestTemplateBuilder} bean by
     * appending the Wiretap logging interceptor configured via {@code application.yml}.
     */
    @ConditionalOnProperty(name = "wiretap.rest-template-interceptor.enabled", havingValue = "true", matchIfMissing = true)
    @Bean
    public RestTemplateCustomizer restTemplateLogCustomizer(
            final RestTemplateLoggingInterceptor interceptor
    ) {
        return restTemplate -> restTemplate.getInterceptors().add(interceptor);
    }

    /**
     * Customises the auto-configured {@code RestClient.Builder} bean by
     * appending the Wiretap logging interceptor configured via {@code application.yml}.
     */
    @ConditionalOnProperty(name = "wiretap.rest-client-interceptor.enabled", havingValue = "true", matchIfMissing = true)
    @Bean
    public RestClientCustomizer restClientLogCustomizer(
            final RestClientLoggingInterceptor interceptor
    ) {
        return restClient -> restClient.requestInterceptor(interceptor);
    }

    @Bean
    public HttpAccessFieldNames httpAccessFieldNames(WiretapFieldNamesProperties fieldNames) {
        return fieldNames.getHttp();
    }

    @ConditionalOnProperty(name = "wiretap.feign-interceptor.enabled", havingValue = "true", matchIfMissing = true)
    @Bean
    public Client loggingFeignClient(BodyParser bodyParser, FeignClientMessageSettings feignClientMessageSettings,
                                     HttpAccessFieldNames httpFieldNames) {
        return new FeignClientWrapper(
                new Client.Default(null, null),
                bodyParser,
                feignClientMessageSettings,
                httpFieldNames
        );
    }

    /**
     * Enables the legacy 64-bit B3 trace-id format by default
     * (e.g. {@code 04244599167d5c83}) for backwards compatibility.
     */
    @ConditionalOnProperty(name = "wiretap.tracing.propagation.type.b3.enabled", havingValue = "true", matchIfMissing = true)
    @Bean
    public Tracing braveTracing() {
        return Tracing.newBuilder()
                .propagationFactory(B3Propagation.newFactoryBuilder().injectFormat(B3Propagation.Format.SINGLE_NO_PARENT).build())
                .build();
    }
}