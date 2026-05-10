package io.wiretap.http.outgoing.interceptor.rest;

import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.RestTemplateLogMessageSettings;
import io.wiretap.http.message.settings.body.BodyParser;

/**
 * Logging interceptor for outbound traffic issued via RestTemplate.
 */
public class RestTemplateLoggingInterceptor extends RestLoggingInterceptor {

    public RestTemplateLoggingInterceptor(RestTemplateLogMessageSettings logMessageSettings, BodyParser bodyParser,
                                          HttpAccessFieldNames httpFieldNames) {
        super(logMessageSettings, bodyParser, "RestTemplate", httpFieldNames);
    }
}
