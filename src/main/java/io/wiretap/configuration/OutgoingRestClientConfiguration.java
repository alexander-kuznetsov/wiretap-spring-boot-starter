package io.wiretap.configuration;

import io.wiretap.http.message.HttpUrlMaskingHandler;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.RestClientLogMessageSettings;
import io.wiretap.http.message.settings.body.BodyParser;
import io.wiretap.http.outgoing.interceptor.rest.RestClientLoggingInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RestClientLogMessageSettings.class)
public class OutgoingRestClientConfiguration {

    @Bean
    public RestClientLoggingInterceptor restClientLoggingInterceptor(
            RestClientLogMessageSettings settings, BodyParser bodyParser, HttpAccessFieldNames httpFieldNames,
            @Autowired(required = false) HttpUrlMaskingHandler urlMaskingHandler) {
        return new RestClientLoggingInterceptor(settings, bodyParser, httpFieldNames, urlMaskingHandler);
    }

    @ConditionalOnProperty(name = "wiretap.rest-client-interceptor.enabled", havingValue = "true", matchIfMissing = true)
    @Bean
    public RestClientCustomizer restClientLogCustomizer(RestClientLoggingInterceptor interceptor) {
        return restClient -> restClient.requestInterceptor(interceptor);
    }
}
