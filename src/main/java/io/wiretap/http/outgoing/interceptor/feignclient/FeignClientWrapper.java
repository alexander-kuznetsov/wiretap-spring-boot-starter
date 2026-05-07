package io.wiretap.http.outgoing.interceptor.feignclient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Client;
import feign.Request;
import feign.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StopWatch;
import org.springframework.util.StreamUtils;
import io.wiretap.http.message.HttpMessageInfo;
import io.wiretap.http.message.settings.AdditionalRequestHeaders;
import io.wiretap.http.message.settings.FeignClientMessageSettings;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.HttpInfoLogMessageSettings;
import io.wiretap.http.message.settings.body.BodyParser;
import io.wiretap.http.outgoing.interceptor.Supplier;
import io.wiretap.util.FieldVisibilityMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.springframework.util.StreamUtils.copyToByteArray;
import static io.wiretap.http.message.HttpMessageInfo.RequestDirection.OUTGOING;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.REQUEST_BODY;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.REQUEST_HEADERS;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.REQUEST_PARAMS;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.REQUEST_URL;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.RESPONSE_BODY;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.RESPONSE_HEADERS;
import static io.wiretap.util.HttpBodyUtils.getStringBody;
import static io.wiretap.util.MaskUtil.maskAllPans;
import static io.wiretap.util.MaskUtil.maskPhoneNumber;

@Slf4j
@RequiredArgsConstructor
public class FeignClientWrapper implements Client {
    private final Client delegate;
    private final BodyParser bodyParser;
    private final FeignClientMessageSettings commonRestLogSettings;
    private final HttpAccessFieldNames httpFieldNames;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String HTTP_INFO_MDC_NAME = "HTTP-REQUEST-LOG";
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
        final String requestURL = request.url();
        boolean shouldSkip = commonRestLogSettings.getExcludeRequestPatterns().stream()
                .anyMatch(requestURL::matches);

        if (shouldSkip) {
            return delegate.execute(request, options);
        }
        final Request requestWithAdditionalHeaders = setAdditionalHeaders(request);

        Optional<HttpMessageInfo> requestHttpInfoOptional = getRequestHttpInfo(request);

        final StopWatch requestStopWatch = new StopWatch();
        requestStopWatch.start();
        final Response bufferedResponse = getBufferedResponse(request, options, requestHttpInfoOptional, requestStopWatch);
        requestStopWatch.stop();

        Optional<HttpMessageInfo> fullHttpInfo = requestHttpInfoOptional.flatMap(requestHttpInfo ->
                getResponseHttpInfo(requestHttpInfo, requestWithAdditionalHeaders, requestStopWatch.getTotalTimeMillis(), bufferedResponse)
        );
        fullHttpInfo.ifPresent(this::logRequest);

        return bufferedResponse;
    }

    @NotNull
    private Response getBufferedResponse(
            Request request,
            Request.Options options,
            final Optional<HttpMessageInfo> requestHttpInfo,
            final StopWatch stopWatch
    ) throws IOException {
        try (Response response = delegate.execute(request, options)) {
            return Response.builder()
                    .request(request)
                    .status(response.status())
                    .reason(response.reason())
                    .headers(response.headers())
                    .body(StreamUtils.copyToByteArray(response.body().asInputStream()))
                    .build();
        } catch (Exception e) {
            stopWatch.stop();
            requestHttpInfo.ifPresent(httpInfo -> {
                httpInfo.setElapsedTime(stopWatch.getTotalTimeMillis());
                logRequest(httpInfo);
            });
            throw e;
        }
    }

/**
 * Captures the request side of {@link HttpMessageInfo} eagerly so that even on
 * timeout / network failure we still emit a partial log entry.
 */
private Optional<HttpMessageInfo> getRequestHttpInfo(Request request) {
    try {

        log.debug("Getting request part info of outgoing rest template request...");
        final String requestUrl = request.url();

        final HttpInfoLogMessageSettings specificRestLogSettings = commonRestLogSettings.getRequestSettingsByUrl(requestUrl);
        final Supplier<JsonNode> requestBodySupplier = () -> {
            final MediaType requestContentType = getContentType(request.headers());
            return bodyParser.parseRequestBody(
                    new String(request.body() != null? request.body() : EMPTY_BYTE_ARRAY, StandardCharsets.UTF_8),
                    requestUrl,
                    requestContentType,
                    specificRestLogSettings.getHttpBodySettings()
            );
        };

        final Supplier<Map<String, String>> requestHeadersSupplier = getHeadersSupplier(specificRestLogSettings.getRequestHeaders(), request.headers());

        final Supplier<Map<String, List<String>>> requestParamsSupplier = getRequestParamsSupplier(request);

        final FieldVisibilityMap<HttpInfoLogMessageSettings.HttpConfigurableField> visibilityMap = specificRestLogSettings.getVisibilitySettings();
        final String requestBodyString = getStringBody(visibilityMap.getVisible(REQUEST_BODY, requestBodySupplier));


        return Optional.of(
                HttpMessageInfo.builder()
                        .requestDirection(OUTGOING)
                        .requestUrl(Boolean.TRUE.equals(visibilityMap.get(REQUEST_URL)) ? getMaskedRequestUrl(requestUrl) : null)
                        .httpMethod(request.httpMethod().name())
                        .requestHeaders(visibilityMap.getVisible(REQUEST_HEADERS, requestHeadersSupplier))
                        .requestParams(visibilityMap.getVisible(REQUEST_PARAMS, requestParamsSupplier))
                        .requestBody(requestBodyString)
                        .requestBodyLength(getContentLength(request.headers()))
                        .build()
        );
    } catch (Exception e) {
        log.error("Error while providing request info of feign-client request...", e);
        return Optional.empty();
    }
}

    private Optional<HttpMessageInfo> getResponseHttpInfo(HttpMessageInfo httpInfo, Request httpRequest, long elapsedTime, Response bufferedResponse) {
        try {
            final String requestUrl = httpRequest.url();
            final HttpInfoLogMessageSettings specificRestLogSettings = commonRestLogSettings.getRequestSettingsByUrl(requestUrl);
            // Lazy suppliers for request/response fields, evaluated only when
            // visibility settings allow that field to be logged.
            final Supplier<JsonNode> responseBodySupplier = () -> bodyParser.parseResponseBody(
                    new String(copyToByteArray(bufferedResponse.body().asInputStream()), StandardCharsets.UTF_8),
                    requestUrl,
                    getContentType(bufferedResponse.headers()),
                    specificRestLogSettings.getHttpBodySettings()
            );
            final Supplier<Map<String, String>> responseHeadersSupplier = getHeadersSupplier(specificRestLogSettings.getResponseHeaders(), bufferedResponse.headers());
            final FieldVisibilityMap<HttpInfoLogMessageSettings.HttpConfigurableField> visibilityMap = specificRestLogSettings.getVisibilitySettings();
            final String responseBodyString = getStringBody(visibilityMap.getVisible(RESPONSE_BODY, responseBodySupplier));

            httpInfo.setElapsedTime(elapsedTime);
            httpInfo.setResponseHeaders(visibilityMap.getVisible(RESPONSE_HEADERS, responseHeadersSupplier));
            httpInfo.setResponseBody(responseBodyString);
            httpInfo.setResponseBodyLength(getContentLength(bufferedResponse.headers()));
            httpInfo.setReturnCode(bufferedResponse.status());

            return Optional.of(httpInfo);
        } catch (Exception e) {
            log.error("Error while providing response info of feign-client request...", e);
            return Optional.of(httpInfo);
        }
    }

    @SuppressWarnings("ConstantConditions")
    private Supplier<Map<String, String>> getHeadersSupplier(final Collection<String> neededHeaderNames, Map<String, Collection<String>> headersMap) {
        return () -> neededHeaderNames.stream()
                .filter(headerName -> headersMap.get(headerName) != null)
                .collect(toMap(
                        Function.identity(),
                        headerName -> String.join(";", headersMap.get(headerName))
                ));
    }
    private Supplier<Map<String, List<String>>> getRequestParamsSupplier(final Request httpRequest) {
        return () -> URLEncodedUtils.parse(httpRequest.url(), StandardCharsets.UTF_8).stream()
                .collect(groupingBy(NameValuePair::getName, mapping(NameValuePair::getValue, toList())));
    }

    @Nullable
    private MediaType getContentType(Map<String, Collection<String>> headersMap) {
        return Optional.ofNullable(headersMap.get(HttpHeaders.CONTENT_TYPE))
                .map(contentType -> MediaType.valueOf(contentType.iterator().next()))
                .orElse(null);
    }

    private long getContentLength(@NotNull Map<String, Collection<String>> headersMap) {
        return Optional.ofNullable(headersMap.get(HttpHeaders.CONTENT_LENGTH))
                .flatMap(contentLengthValues -> {
                    String contentLengthString = contentLengthValues.iterator().next();

                    return contentLengthString != null ?
                            Optional.ofNullable(parseContentLengthHeader(contentLengthString)) :
                            Optional.empty();
                }).orElse(-1L);
    }

    private Long parseContentLengthHeader(String contentLengthString) {
        try {
            return Long.parseLong(contentLengthString);
        } catch (NumberFormatException e) {
            log.error("Error during parsing content-length header value", e);
            return null;
        }
    }

    private String getMaskedRequestUrl(String notMaskedUrl) {
        return commonRestLogSettings.isEnableUrlMasking() ?
                maskPhoneNumber(maskAllPans(notMaskedUrl, true)) : notMaskedUrl;
    }

    /** Copies configured additional headers from MDC into the outgoing request. */
    private Request setAdditionalHeaders(final Request httpRequest) {
        List<AdditionalRequestHeaders> additionalHeadersSettings = commonRestLogSettings.getAdditionalRequestHeaders();
        additionalHeadersSettings.stream()
                .filter(additionalRequestHeader -> httpRequest.url().matches(additionalRequestHeader.getMatchUrlPattern()))
                .findFirst()
                .ifPresent(additionalResponseHeaders -> addAdditionalHeaders(httpRequest, additionalResponseHeaders.getAdditionalHeaderNames()));

        return httpRequest;
    }

    private void addAdditionalHeaders(Request httpRequest, List<String> additionalResponseHeaders) {
        additionalResponseHeaders.forEach(headerName -> {
            final String headerValue = MDC.get(headerName);
            if (headerValue != null) {
                httpRequest.headers().put(headerName, Collections.singleton(headerValue));
            }
        });
    }

    /**
     * Writes the populated {@link HttpMessageInfo} into MDC, emits the log
     * event, and clears MDC via try-with-resources.
     *
     * @param logMessage Feign request/response info
     */
    private void logRequest(final HttpMessageInfo logMessage) {
        try {
            final String stringLogMessage = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(logMessage.toMap(httpFieldNames));

            try (final MDC.MDCCloseable ignored = MDC.putCloseable(HTTP_INFO_MDC_NAME, stringLogMessage)) {
                log.info("Captured outgoing feign-client request {}", getMaskedRequestUrl(logMessage.getRequestUrl()));
            }
        } catch (JsonProcessingException e) {
            log.error("Error while logging feign-client http-info...", e);
        }
    }
}