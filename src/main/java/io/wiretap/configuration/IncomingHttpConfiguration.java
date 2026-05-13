package io.wiretap.configuration;

import io.micrometer.tracing.Tracer;
import io.wiretap.http.incoming.filter.AccessLogTraceIdForwarder;
import io.wiretap.http.incoming.filter.BufferedHttpBodyThreadCleaner;
import io.wiretap.http.incoming.interceptor.CorrelationHeadersMdcForwarder;
import io.wiretap.http.incoming.provider.httpinfo.HttpInfoMessageProvider;
import io.wiretap.http.incoming.provider.message.MessageProvider;
import io.wiretap.http.message.HttpRequestParamsMaskingHandler;
import io.wiretap.http.message.HttpUrlMaskingHandler;
import io.wiretap.http.message.settings.RestControllerLogMessageSettings;
import io.wiretap.http.message.settings.body.BodyParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties({WiretapHeadersProperties.class, RestControllerLogMessageSettings.class})
public class IncomingHttpConfiguration implements WebMvcConfigurer {

    private final WiretapHeadersProperties headersProperties;

    public IncomingHttpConfiguration(WiretapHeadersProperties headersProperties) {
        this.headersProperties = headersProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new CorrelationHeadersMdcForwarder(headersProperties));
    }

    @Bean
    public FilterRegistrationBean<AccessLogTraceIdForwarder> sleuthAttrsFilter(Tracer tracer) {
        return new FilterRegistrationBean<>(new AccessLogTraceIdForwarder(tracer));
    }

    @Bean
    public FilterRegistrationBean<BufferedHttpBodyThreadCleaner> errorResponseCleanFilter() {
        FilterRegistrationBean<BufferedHttpBodyThreadCleaner> bean =
                new FilterRegistrationBean<>(new BufferedHttpBodyThreadCleaner());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    public HttpInfoMessageProvider httpInfoMessageProvider(
            BodyParser bodyParser,
            RestControllerLogMessageSettings logSettings,
            @Value("${wiretap.pretty-print:false}") boolean isPrettyLog,
            WiretapAccessLogFieldsProperties fieldNames,
            @Autowired(required = false) HttpUrlMaskingHandler urlMaskingHandler,
            @Autowired(required = false) HttpRequestParamsMaskingHandler paramsMaskingHandler
    ) {
        return new HttpInfoMessageProvider(bodyParser, logSettings, isPrettyLog, fieldNames, urlMaskingHandler, paramsMaskingHandler);
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
