package io.wiretap.http.outgoing.interceptor.rest;

import io.wiretap.http.message.HttpRequestParamsMaskingHandler;
import io.wiretap.http.message.HttpUrlMaskingHandler;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.RestTemplateLogMessageSettings;
import io.wiretap.http.message.settings.body.BodyParser;
import org.jetbrains.annotations.Nullable;

/**
 * Logging interceptor for outbound traffic issued via RestTemplate.
 */
public class RestTemplateLoggingInterceptor extends RestLoggingInterceptor {

    public RestTemplateLoggingInterceptor(
            RestTemplateLogMessageSettings logMessageSettings,
            BodyParser bodyParser,
            HttpAccessFieldNames httpFieldNames,
            @Nullable HttpUrlMaskingHandler urlMaskingHandler,
            @Nullable HttpRequestParamsMaskingHandler paramsMaskingHandler
    ) {
        super(logMessageSettings, bodyParser, "RestTemplate", httpFieldNames, urlMaskingHandler, paramsMaskingHandler);
    }
}
