package io.wiretap.http.outgoing.interceptor.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.RestTemplateLogMessageSettings;
import io.wiretap.http.message.settings.body.BodyParser;

/**
 * Logging interceptor for outbound traffic issued via RestTemplate.
 */
@Component
public class RestTemplateLoggingInterceptor extends RestLoggingInterceptor {

    @Autowired
    public RestTemplateLoggingInterceptor(RestTemplateLogMessageSettings logMessageSettings, BodyParser bodyParser,
                                          HttpAccessFieldNames httpFieldNames) {
        super(logMessageSettings, bodyParser, "RestTemplate", httpFieldNames);
    }
}
