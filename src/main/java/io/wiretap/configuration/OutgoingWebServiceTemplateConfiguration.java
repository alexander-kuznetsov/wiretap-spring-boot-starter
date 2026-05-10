package io.wiretap.configuration;

import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.WebServiceTemplateLogMessageSettings;
import io.wiretap.http.message.settings.body.BodyParser;
import io.wiretap.http.outgoing.interceptor.webservicetemplate.WebServiceTemplateLoggingInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WebServiceTemplateLogMessageSettings.class)
public class OutgoingWebServiceTemplateConfiguration {

    @Bean
    public WebServiceTemplateLoggingInterceptor webServiceTemplateLoggingInterceptor(
            WebServiceTemplateLogMessageSettings settings, BodyParser bodyParser, HttpAccessFieldNames httpFieldNames) {
        return new WebServiceTemplateLoggingInterceptor(settings, bodyParser, httpFieldNames);
    }
}
