package io.wiretap.http.outgoing.interceptor.rest;

import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.RestClientLogMessageSettings;
import io.wiretap.http.message.settings.body.BodyParser;

/**
 * Logging interceptor for outbound traffic issued via RestClient.
 */
public class RestClientLoggingInterceptor extends RestLoggingInterceptor {

    public RestClientLoggingInterceptor(RestClientLogMessageSettings logMessageSettings, BodyParser bodyParser,
                                        HttpAccessFieldNames httpFieldNames) {
        super(logMessageSettings, bodyParser, "RestClient", httpFieldNames);
    }
}
