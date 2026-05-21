package io.wiretap.configuration;

import io.wiretap.http.message.HttpUrlMaskingHandler;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.WebServiceTemplateLogMessageSettings;
import io.wiretap.http.message.settings.body.BodyParser;
import io.wiretap.http.outgoing.interceptor.webservicetemplate.WebServiceTemplateLoggingInterceptor;
import io.wiretap.metrics.WiretapMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WebServiceTemplateLogMessageSettings.class)
public class OutgoingWebServiceTemplateConfiguration {

    @Bean
    public WebServiceTemplateLoggingInterceptor webServiceTemplateLoggingInterceptor(
            WebServiceTemplateLogMessageSettings settings, BodyParser bodyParser, HttpAccessFieldNames httpFieldNames,
            @Autowired(required = false) HttpUrlMaskingHandler urlMaskingHandler,
            WiretapMetrics metrics) {
        return new WebServiceTemplateLoggingInterceptor(settings, bodyParser, httpFieldNames, urlMaskingHandler, metrics);
    }
}
