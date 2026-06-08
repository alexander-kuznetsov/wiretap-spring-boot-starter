package io.wiretap.configuration;

import io.micrometer.tracing.Tracer;
import io.wiretap.http.incoming.filter.AccessLogTraceIdForwarder;
import io.wiretap.http.incoming.filter.CorrelationHeadersMdcFilter;
import io.wiretap.http.incoming.provider.httpinfo.HttpInfoMessageProvider;
import io.wiretap.http.incoming.provider.message.MessageProvider;
import io.wiretap.http.message.HttpRequestParamsMaskingHandler;
import io.wiretap.http.message.HttpUrlMaskingHandler;
import io.wiretap.http.message.settings.RestControllerLogMessageSettings;
import io.wiretap.http.message.settings.body.BodyParser;
import io.wiretap.metrics.WiretapMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@EnableConfigurationProperties({WiretapHeadersProperties.class, RestControllerLogMessageSettings.class})
public class IncomingHttpConfiguration {

    private final WiretapHeadersProperties headersProperties;

    public IncomingHttpConfiguration(WiretapHeadersProperties headersProperties) {
        this.headersProperties = headersProperties;
    }

    /**
     * Populates MDC with correlation headers before any other filter runs (Spring
     * Security registers its chain at order {@code -100}; this sits ahead of it) and
     * clears MDC once the request completes.
     */
    @Bean
    public FilterRegistrationBean<CorrelationHeadersMdcFilter> correlationHeadersMdcFilter() {
        FilterRegistrationBean<CorrelationHeadersMdcFilter> registration =
                new FilterRegistrationBean<>(new CorrelationHeadersMdcFilter(headersProperties));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AccessLogTraceIdForwarder> sleuthAttrsFilter(Tracer tracer) {
        return new FilterRegistrationBean<>(new AccessLogTraceIdForwarder(tracer));
    }

    @Bean
    public HttpInfoMessageProvider httpInfoMessageProvider(
            BodyParser bodyParser,
            RestControllerLogMessageSettings logSettings,
            @Value("${wiretap.pretty-print:false}") boolean isPrettyLog,
            WiretapAccessLogFieldsProperties fieldNames,
            @Autowired(required = false) HttpUrlMaskingHandler urlMaskingHandler,
            @Autowired(required = false) HttpRequestParamsMaskingHandler paramsMaskingHandler,
            WiretapMetrics metrics
    ) {
        return new HttpInfoMessageProvider(bodyParser, logSettings, isPrettyLog, fieldNames, urlMaskingHandler, paramsMaskingHandler, metrics);
    }

    @Bean
    public MessageProvider messageProvider(
            RestControllerLogMessageSettings settings,
            WiretapAccessLogFieldsProperties fieldNames,
            @Autowired(required = false) HttpUrlMaskingHandler urlMaskingHandler
    ) {
        return new MessageProvider(settings, fieldNames, urlMaskingHandler);
    }
}
