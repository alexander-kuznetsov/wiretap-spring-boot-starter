package io.wiretap.configuration;

import io.wiretap.http.message.HttpRequestParamsMaskingHandler;
import io.wiretap.http.message.HttpUrlMaskingHandler;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.RestTemplateLogMessageSettings;
import io.wiretap.http.message.settings.body.BodyParser;
import io.wiretap.http.outgoing.interceptor.rest.RestTemplateLoggingInterceptor;
import io.wiretap.metrics.WiretapMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RestTemplateLogMessageSettings.class)
public class OutgoingRestTemplateConfiguration {

    @Bean
    public RestTemplateLoggingInterceptor restTemplateLoggingInterceptor(
            RestTemplateLogMessageSettings settings, BodyParser bodyParser, HttpAccessFieldNames httpFieldNames,
            @Autowired(required = false) HttpUrlMaskingHandler urlMaskingHandler,
            @Autowired(required = false) HttpRequestParamsMaskingHandler paramsMaskingHandler,
            WiretapMetrics metrics) {
        return new RestTemplateLoggingInterceptor(settings, bodyParser, httpFieldNames,
                urlMaskingHandler, paramsMaskingHandler, metrics);
    }

    @ConditionalOnProperty(name = "wiretap.rest-template-interceptor.enabled", havingValue = "true", matchIfMissing = true)
    @Bean
    public RestTemplateCustomizer restTemplateLogCustomizer(RestTemplateLoggingInterceptor interceptor) {
        return restTemplate -> restTemplate.getInterceptors().add(interceptor);
    }
}
