package io.wiretap.configuration;

import io.wiretap.http.message.HttpRequestParamsMaskingHandler;
import io.wiretap.http.message.HttpUrlMaskingHandler;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.WebClientLogMessageSettings;
import io.wiretap.http.message.settings.body.BodyParser;
import io.wiretap.http.outgoing.interceptor.webclient.WebClientLoggingFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for WebClient / GraphQL WebClient logging.
 * Activates only when {@code spring-webflux} is on the classpath.
 * Applies {@link WebClientLoggingFilter} to the auto-configured
 * {@code WebClient.Builder}, which covers any client built from it,
 * including {@code graphql.kickstart.spring.webclient.boot.GraphQLWebClient}.
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.web.reactive.function.client.WebClient")
@AutoConfigureAfter(WiretapAutoConfiguration.class)
@EnableConfigurationProperties(WebClientLogMessageSettings.class)
public class WebClientInterceptorConfiguration {

    @Bean
    public WebClientLoggingFilter webClientLoggingFilter(
            WebClientLogMessageSettings settings,
            BodyParser bodyParser,
            HttpAccessFieldNames httpFieldNames,
            @Autowired(required = false) HttpUrlMaskingHandler urlMaskingHandler,
            @Autowired(required = false) HttpRequestParamsMaskingHandler paramsMaskingHandler
    ) {
        return new WebClientLoggingFilter(settings, bodyParser, httpFieldNames, urlMaskingHandler, paramsMaskingHandler);
    }

    @ConditionalOnProperty(name = "wiretap.web-client-interceptor.enabled", havingValue = "true", matchIfMissing = true)
    @Bean
    public WebClientCustomizer webClientLoggingCustomizer(WebClientLoggingFilter filter) {
        return builder -> builder.filter(filter);
    }
}
