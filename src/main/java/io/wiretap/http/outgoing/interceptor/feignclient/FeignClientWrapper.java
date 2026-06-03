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
import io.wiretap.http.message.HttpRequestParamsMaskingHandler;
import io.wiretap.http.message.HttpUrlMaskingHandler;
import io.wiretap.http.message.settings.body.BodyParser;
import io.wiretap.http.outgoing.interceptor.Supplier;
import io.wiretap.metrics.BodyMetricsContext;
import io.wiretap.metrics.WiretapMetrics;
import io.wiretap.util.FieldVisibilityMap;
import io.wiretap.util.HeaderSelector;
import io.wiretap.util.HttpStatusClassifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
@Slf4j
public class FeignClientWrapper implements Client {
    private static final String CLIENT = "feign";
    private static final String DIRECTION = "outgoing";
    private final Client delegate;
    private final BodyParser bodyParser;
    private final FeignClientMessageSettings commonRestLogSettings;
    private final HttpAccessFieldNames httpFieldNames;
    @Nullable
    private final HttpUrlMaskingHandler urlMaskingHandler;
    @Nullable
    private final HttpRequestParamsMaskingHandler paramsMaskingHandler;
    private final WiretapMetrics metrics;

    public FeignClientWrapper(
            Client delegate,
            BodyParser bodyParser,
            FeignClientMessageSettings commonRestLogSettings,
            HttpAccessFieldNames httpFieldNames,
            @Nullable HttpUrlMaskingHandler urlMaskingHandler,
            @Nullable HttpRequestParamsMaskingHandler paramsMaskingHandler,
            WiretapMetrics metrics
    ) {
        this.delegate = delegate;
        this.bodyParser = bodyParser;
        this.commonRestLogSettings = commonRestLogSettings;
        this.httpFieldNames = httpFieldNames;
        this.urlMaskingHandler = urlMaskingHandler;
        this.paramsMaskingHandler = paramsMaskingHandler;
        this.metrics = metrics;
    }
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String HTTP_INFO_MDC_NAME = "HTTP-REQUEST-LOG";
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
        final String requestURL = request.url();
        boolean shouldSkip = commonRestLogSettings.getExcludeRequestPatterns().stream()
                .anyMatch(requestURL::matches);

        if (shouldSkip) {
            metrics.recordHttpSkipped(DIRECTION, CLIENT, "exclude_pattern");
            return delegate.execute(request, options);
        }
        final long startNanos = metrics.startSample();
        final Request requestWithAdditionalHeaders = setAdditionalHeaders(request);

        Optional<HttpMessageInfo> requestHttpInfoOptional = getRequestHttpInfo(request);

        final StopWatch requestStopWatch = new StopWatch();
        requestStopWatch.start();
        final Response bufferedResponse;
        try {
            bufferedResponse = getBufferedResponse(request, options, requestHttpInfoOptional, requestStopWatch);
        } catch (IOException | RuntimeException e) {
            metrics.recordHttpRequest(startNanos, requestStopWatch.getTotalTimeNanos(), DIRECTION, CLIENT, "exception", "exception");
            throw e;
        }
        requestStopWatch.stop();

        Optional<HttpMessageInfo> fullHttpInfo = requestHttpInfoOptional.flatMap(requestHttpInfo ->
                getResponseHttpInfo(requestHttpInfo, requestWithAdditionalHeaders, requestStopWatch.getTotalTimeMillis(), bufferedResponse)
        );
        fullHttpInfo.ifPresent(this::logRequest);

        int status = bufferedResponse.status();
        recordBodySizes(request, bufferedResponse);
        metrics.recordHttpRequest(startNanos, requestStopWatch.getTotalTimeNanos(), DIRECTION, CLIENT, HttpStatusClassifier.outcome(status), HttpStatusClassifier.statusGroup(status));

        return bufferedResponse;
    }

    private void recordBodySizes(Request request, Response response) {
        try {
            long reqLen = getContentLength(request.headers());
            if (reqLen >= 0) {
                metrics.recordHttpBodySize(DIRECTION, CLIENT,
                        BodyMetricsContext.classify(getContentType(request.headers())), "request", reqLen);
            } else if (request.body() != null) {
                metrics.recordHttpBodySize(DIRECTION, CLIENT,
                        BodyMetricsContext.classify(getContentType(request.headers())), "request", request.body().length);
            }
        } catch (Exception ignored) { /* metrics must not break the hot path */ }
        try {
            long respLen = getContentLength(response.headers());
            if (respLen >= 0) {
                metrics.recordHttpBodySize(DIRECTION, CLIENT,
                        BodyMetricsContext.classify(getContentType(response.headers())), "response", respLen);
            }
        } catch (Exception ignored) { /* metrics must not break the hot path */ }
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
                    specificRestLogSettings.getHttpBodySettings(),
                    new BodyMetricsContext(DIRECTION, CLIENT, BodyMetricsContext.classify(requestContentType))
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
                        .requestParams(maskRequestParams(visibilityMap.getVisible(REQUEST_PARAMS, requestParamsSupplier)))
                        .requestBody(requestBodyString)
                        .requestBodyLength(getContentLength(request.headers()))
                        .build()
        );
    } catch (Exception e) {
        log.error("Error while providing request info of feign-client request...", e);
        metrics.recordHttpBodyCaptureFailure(DIRECTION, CLIENT, "capture");
        return Optional.empty();
    }
}

    private Optional<HttpMessageInfo> getResponseHttpInfo(HttpMessageInfo httpInfo, Request httpRequest, long elapsedTime, Response bufferedResponse) {
        try {
            final String requestUrl = httpRequest.url();
            final HttpInfoLogMessageSettings specificRestLogSettings = commonRestLogSettings.getRequestSettingsByUrl(requestUrl);
            // Lazy suppliers for request/response fields, evaluated only when
            // visibility settings allow that field to be logged.
            final MediaType responseContentType = getContentType(bufferedResponse.headers());
            final Supplier<JsonNode> responseBodySupplier = () -> bodyParser.parseResponseBody(
                    new String(copyToByteArray(bufferedResponse.body().asInputStream()), StandardCharsets.UTF_8),
                    requestUrl,
                    responseContentType,
                    specificRestLogSettings.getHttpBodySettings(),
                    new BodyMetricsContext(DIRECTION, CLIENT, BodyMetricsContext.classify(responseContentType))
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
            metrics.recordHttpBodyCaptureFailure(DIRECTION, CLIENT, "capture");
            return Optional.of(httpInfo);
        }
    }

    private Supplier<Map<String, String>> getHeadersSupplier(final Collection<String> neededHeaderNames, Map<String, Collection<String>> headersMap) {
        return () -> HeaderSelector.selectMulti(neededHeaderNames, headersMap);
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
        return commonRestLogSettings.isEnableUrlMasking() && urlMaskingHandler != null
                ? urlMaskingHandler.maskUrl(notMaskedUrl) : notMaskedUrl;
    }

    private Map<String, List<String>> maskRequestParams(Map<String, List<String>> params) {
        if (params == null
                || !commonRestLogSettings.isEnableRequestParamsMasking()
                || paramsMaskingHandler == null) {
            return params;
        }
        return params.entrySet().stream()
                .collect(toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .map(v -> paramsMaskingHandler.maskParamValue(e.getKey(), v))
                                .toList()));
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
            long serStart = metrics.startSample();
            final String stringLogMessage = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(logMessage.toMap(httpFieldNames));
            metrics.recordJsonSerialization(serStart, "http", DIRECTION, CLIENT);

            try (final MDC.MDCCloseable ignored = MDC.putCloseable(HTTP_INFO_MDC_NAME, stringLogMessage)) {
                log.info("Captured outgoing feign-client request {}", getMaskedRequestUrl(logMessage.getRequestUrl()));
            }
        } catch (JsonProcessingException e) {
            log.error("Error while logging feign-client http-info...", e);
            metrics.recordHttpBodyCaptureFailure(DIRECTION, CLIENT, "serialize");
        }
    }
}