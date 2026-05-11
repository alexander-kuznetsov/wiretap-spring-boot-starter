package io.wiretap.http.outgoing.interceptor.rest;

import io.wiretap.http.message.HttpUrlMaskingHandler;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.RestClientLogMessageSettings;
import io.wiretap.http.message.settings.body.BodyParser;
import org.jetbrains.annotations.Nullable;

/**
 * Logging interceptor for outbound traffic issued via RestClient.
 */
public class RestClientLoggingInterceptor extends RestLoggingInterceptor {

    public RestClientLoggingInterceptor(
            RestClientLogMessageSettings logMessageSettings,
            BodyParser bodyParser,
            HttpAccessFieldNames httpFieldNames,
            @Nullable HttpUrlMaskingHandler urlMaskingHandler
    ) {
        super(logMessageSettings, bodyParser, "RestClient", httpFieldNames, urlMaskingHandler);
    }
}
