package io.wiretap.http.outgoing.interceptor.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StopWatch;
import io.wiretap.http.message.HttpMessageInfo;
import io.wiretap.http.message.settings.AdditionalRequestHeaders;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.HttpInfoLogMessageSettings;
import io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField;
import io.wiretap.http.message.settings.RestLogMessageSettings;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static io.wiretap.http.message.HttpMessageInfo.RequestDirection.OUTGOING;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.REQUEST_BODY;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.REQUEST_HEADERS;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.REQUEST_PARAMS;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.REQUEST_URL;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.RESPONSE_BODY;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.RESPONSE_HEADERS;
import static io.wiretap.util.HttpBodyUtils.getStringBody;
import static io.wiretap.util.HttpBodyUtils.getXmlRequestType;
import static io.wiretap.util.HttpBodyUtils.isXmlBody;
import io.wiretap.http.message.HttpRequestParamsMaskingHandler;
import io.wiretap.http.message.HttpUrlMaskingHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Base interceptor that logs outbound HTTP requests issued through Spring's
 * {@code RestTemplate} or {@code RestClient}. Subclassed by client-specific
 * interceptors that supply the right settings bean and a human-readable client name.
 */
class RestLoggingInterceptor implements ClientHttpRequestInterceptor {
    private static final Logger log = LoggerFactory.getLogger(RestLoggingInterceptor.class);
    private static final String HTTP_INFO_MDC_NAME = "HTTP-REQUEST-LOG";
    private static final String DIRECTION = "outgoing";
    private final RestLogMessageSettings commonRestLogSettings;
    private final String clientName;
    private final String clientTag;
    private final BodyParser bodyParser;
    private final HttpAccessFieldNames httpFieldNames;
    @Nullable
    private final HttpUrlMaskingHandler urlMaskingHandler;
    @Nullable
    private final HttpRequestParamsMaskingHandler paramsMaskingHandler;
    private final WiretapMetrics metrics;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RestLoggingInterceptor(
            final RestLogMessageSettings logMessageSettings,
            final BodyParser bodyParser,
            String clientName,
            final HttpAccessFieldNames httpFieldNames,
            @Nullable HttpUrlMaskingHandler urlMaskingHandler,
            @Nullable HttpRequestParamsMaskingHandler paramsMaskingHandler,
            WiretapMetrics metrics
    ) {
        this.commonRestLogSettings = logMessageSettings;
        this.bodyParser = bodyParser;
        this.clientName = clientName;
        this.clientTag = clientName.toLowerCase(java.util.Locale.ROOT);
        this.httpFieldNames = httpFieldNames;
        this.urlMaskingHandler = urlMaskingHandler;
        this.paramsMaskingHandler = paramsMaskingHandler;
        this.metrics = metrics;
    }


    @NotNull
    @Override
    public ClientHttpResponse intercept(
            @NotNull final HttpRequest httpRequest,
            @NotNull final byte[] bytes,
            @NotNull final ClientHttpRequestExecution clientHttpRequestExecution
    ) throws IOException {
        final String requestURL = httpRequest.getURI().toString();
        boolean shouldSkip = commonRestLogSettings.getExcludeRequestPatterns().stream() // TODO add local caching of the resolved decision
                .anyMatch(requestURL::matches);
        if (shouldSkip) {
            metrics.recordHttpSkipped(DIRECTION, clientTag, "exclude_pattern");
            return clientHttpRequestExecution.execute(httpRequest, bytes);
        }

        final long startNanos = metrics.startSample();
        final HttpRequest requestWithAdditionalHeaders = setAdditionalHeaders(httpRequest);
        Optional<HttpMessageInfo> requestHttpInfoOptional = getRequestHttpInfo(bytes, requestWithAdditionalHeaders);

        final StopWatch requestStopWatch = new StopWatch();
        requestStopWatch.start();
        final BufferingClientHttpResponseWrapper bufferingResponse;
        try {
            bufferingResponse = executeRestRequest(
                    bytes,
                    clientHttpRequestExecution,
                    requestWithAdditionalHeaders,
                    requestHttpInfoOptional,
                    requestStopWatch
            );
        } catch (IOException e) {
            metrics.recordHttpRequest(startNanos, 0L, DIRECTION, clientTag, "exception", "exception");
            throw e;
        }
        requestStopWatch.stop();
        Optional<HttpMessageInfo> fullHttpInfo = requestHttpInfoOptional.flatMap(requestHttpInfo ->
                getResponseHttpInfo(requestHttpInfo, requestWithAdditionalHeaders, requestStopWatch.getTotalTimeMillis(), bufferingResponse)
        );
        fullHttpInfo.ifPresent(this::logRequest);

        int status = -1;
        try {
            status = bufferingResponse.getStatusCode().value();
        } catch (Exception ignored) { /* status unavailable */ }
        recordBodySizes(httpRequest, bytes, bufferingResponse);
        metrics.recordHttpRequest(startNanos, requestStopWatch.getTotalTimeNanos(), DIRECTION, clientTag, HttpStatusClassifier.outcome(status), HttpStatusClassifier.statusGroup(status));
        return bufferingResponse;
    }

    private void recordBodySizes(HttpRequest httpRequest, byte[] requestBytes, BufferingClientHttpResponseWrapper response) {
        try {
            String reqClass = BodyMetricsContext.classify(getContentType(httpRequest.getHeaders()));
            metrics.recordHttpBodySize(DIRECTION, clientTag, reqClass, "request", requestBytes == null ? 0L : requestBytes.length);
        } catch (Exception ignored) { /* metrics must not break the hot path */ }
        try {
            String respClass = BodyMetricsContext.classify(getContentType(response.getHeaders()));
            long bytes = response.getHeaders().getContentLength();
            if (bytes >= 0) {
                metrics.recordHttpBodySize(DIRECTION, clientTag, respClass, "response", bytes);
            }
        } catch (Exception ignored) { /* metrics must not break the hot path */ }
    }

    @NotNull
    private BufferingClientHttpResponseWrapper executeRestRequest(
            final byte[] bytes,
            final ClientHttpRequestExecution clientHttpRequestExecution,
            final HttpRequest requestWithAdditionalHeaders,
            final Optional<HttpMessageInfo> requestHttpInfoOptional,
            final StopWatch stopWatch
    ) throws IOException {
        try {
            return new BufferingClientHttpResponseWrapper(
                    clientHttpRequestExecution.execute(requestWithAdditionalHeaders, bytes)
            );
        } catch (IOException e) {
            stopWatch.stop();
            requestHttpInfoOptional.ifPresent(requestHttpInfo -> {
                requestHttpInfo.setElapsedTime(stopWatch.getTotalTimeMillis());
                requestHttpInfo.setSourcePort(SourcePortLocalThreadKeeper.getAndRemove());
                logRequest(requestHttpInfo);
            });
            throw e;
        }
    }

/**
 * Captures the outgoing request side of {@link HttpMessageInfo} eagerly so that
 * even on timeout / network failure we still emit a partial log entry.
 */
private Optional<HttpMessageInfo> getRequestHttpInfo(byte[] bodyBytes, HttpRequest httpRequest) {
    try {
        log.debug("Getting request part info of outgoing {} request...", clientName);
        final String requestUrl = httpRequest.getURI().toString();
        final HttpInfoLogMessageSettings specificRestLogSettings = commonRestLogSettings.getRequestSettingsByUrl(requestUrl);
        final String originIncomingRequestBodyString = new String(bodyBytes, StandardCharsets.UTF_8);
        final Supplier<JsonNode> requestBodySupplier = () -> {
            final MediaType requestContentType = getContentType(httpRequest.getHeaders());
            return bodyParser.parseRequestBody(
                    new String(bodyBytes, StandardCharsets.UTF_8), requestUrl, requestContentType, specificRestLogSettings.getHttpBodySettings(),
                    new BodyMetricsContext(DIRECTION, clientTag, BodyMetricsContext.classify(requestContentType))
            );
        };

        final Supplier<Map<String, String>> requestHeadersSupplier = getHeadersSupplier(specificRestLogSettings.getRequestHeaders(), httpRequest.getHeaders());
        final Supplier<Map<String, List<String>>> requestParamsSupplier = getRequestParamsSupplier(httpRequest);

        final FieldVisibilityMap<HttpConfigurableField> visibilityMap = specificRestLogSettings.getVisibilitySettings();
        final String requestBodyString = getStringBody(visibilityMap.getVisible(REQUEST_BODY, requestBodySupplier));
        final boolean isXmlRequestBody = isXmlBody(getContentType(httpRequest.getHeaders()));
        return Optional.of(
                HttpMessageInfo.builder()
                        .requestDirection(OUTGOING)
                        .requestUrl(Boolean.TRUE.equals(visibilityMap.get(REQUEST_URL)) ? getMaskedRequestUrl(requestUrl) : null)
                        .httpMethod(Optional.ofNullable(httpRequest.getMethod()).map(HttpMethod::name).orElse(null))
                        .requestHeaders(visibilityMap.getVisible(REQUEST_HEADERS, requestHeadersSupplier))
                        .requestParams(maskRequestParams(visibilityMap.getVisible(REQUEST_PARAMS, requestParamsSupplier)))
                        .requestBody(requestBodyString)
                        .requestBodyLength(httpRequest.getHeaders().getContentLength())
                        .xmlBodyType(getXmlType(originIncomingRequestBodyString, isXmlRequestBody))
                        .build()
        );
    } catch (Exception e) {
        log.error("Error while providing request info of {} request...", clientName, e);
        metrics.recordHttpBodyCaptureFailure(DIRECTION, clientTag, "capture");
        return Optional.empty();
    }
}

    private Optional<HttpMessageInfo> getResponseHttpInfo(HttpMessageInfo httpInfo, HttpRequest httpRequest, long elapsedTime, BufferingClientHttpResponseWrapper bufferingResponse) {
        try {
            final String requestUrl = httpRequest.getURI().toString();
            final HttpInfoLogMessageSettings specificRestLogSettings = commonRestLogSettings.getRequestSettingsByUrl(requestUrl);
            // Lazy suppliers for request/response fields, evaluated only when
            // visibility settings allow that field to be logged.
            final MediaType responseContentType = getContentType(bufferingResponse.getHeaders());
            final Supplier<JsonNode> responseBodySupplier = () -> bodyParser.parseResponseBody(
                    bufferingResponse, requestUrl, responseContentType, specificRestLogSettings.getHttpBodySettings(),
                    new BodyMetricsContext(DIRECTION, clientTag, BodyMetricsContext.classify(responseContentType))
            );

            final Supplier<Map<String, String>> responseHeadersSupplier = getHeadersSupplier(specificRestLogSettings.getResponseHeaders(), bufferingResponse.getHeaders());
            final FieldVisibilityMap<HttpConfigurableField> visibilityMap = specificRestLogSettings.getVisibilitySettings();
            final String responseBodyString = getStringBody(visibilityMap.getVisible(RESPONSE_BODY, responseBodySupplier));
            httpInfo.setElapsedTime(elapsedTime);
            httpInfo.setResponseHeaders(visibilityMap.getVisible(RESPONSE_HEADERS, responseHeadersSupplier));
            httpInfo.setResponseBody(responseBodyString);
            httpInfo.setResponseBodyLength(bufferingResponse.getHeaders().getContentLength());
            httpInfo.setReturnCode(bufferingResponse.getStatusCode().value());
            httpInfo.setSourcePort(SourcePortLocalThreadKeeper.getAndRemove());
            return Optional.of(httpInfo);
        } catch (Exception e) {
            log.error("Error while providing response info of {} request...", clientName, e);
            metrics.recordHttpBodyCaptureFailure(DIRECTION, clientTag, "capture");
            SourcePortLocalThreadKeeper.clear();
            return Optional.of(httpInfo);
        }
    }

    /**
     * Writes the populated {@link HttpMessageInfo} into MDC (so the logback
     * pattern can extract it into the {@code http_info} JSON field), emits the
     * INFO event, and clears MDC via try-with-resources.
     *
     * @param logMessage info about the REST request and its response
     */
    private void logRequest(final HttpMessageInfo logMessage) {
        try {
            long serStart = metrics.startSample();
            final String stringLogMessage = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(logMessage.toMap(httpFieldNames));
            metrics.recordJsonSerialization(serStart, "http", DIRECTION, clientTag);

            try (final MDC.MDCCloseable ignored = MDC.putCloseable(HTTP_INFO_MDC_NAME, stringLogMessage)) {
                log.info("Captured outgoing rest request {}", getMaskedRequestUrl(logMessage.getRequestUrl()));
            }
        } catch (JsonProcessingException e) {
            log.error("Error while logging {} http-info...", clientName, e);
            metrics.recordHttpBodyCaptureFailure(DIRECTION, clientTag, "serialize");
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

    private String getXmlType(String requestContent, boolean isXmlBody) {
        try {
            return isXmlBody ? getXmlRequestType(requestContent) : null;
        } catch (Exception e) {
            log.error("Failed while getting xsi:type", e);
            return "Unknown";
        }
    }

    /** Copies configured additional headers from MDC into the outgoing request. */
    private HttpRequest setAdditionalHeaders(final HttpRequest httpRequest) {
        List<AdditionalRequestHeaders> additionalHeadersSettings = commonRestLogSettings.getAdditionalRequestHeaders();
        additionalHeadersSettings.stream()
                .filter(additionalRequestHeader -> httpRequest.getURI().toString().matches(additionalRequestHeader.getMatchUrlPattern()))
                .findFirst()
                .ifPresent(additionalResponseHeaders -> addAdditionalHeaders(httpRequest, additionalResponseHeaders.getAdditionalHeaderNames()));

        return httpRequest;
    }

    private void addAdditionalHeaders(HttpRequest httpRequest, List<String> additionalResponseHeaders) {
        additionalResponseHeaders.forEach(headerName -> {
            final String headerValue = MDC.get(headerName);
            if (headerValue != null) {
                httpRequest.getHeaders().add(headerName, headerValue);
            }
        });
    }

    private Supplier<Map<String, String>> getHeadersSupplier(final Collection<String> neededHeaderNames, final HttpHeaders allHeaders) {
        return () -> HeaderSelector.select(neededHeaderNames, allHeaders);
    }

    private Supplier<Map<String, List<String>>> getRequestParamsSupplier(final HttpRequest httpRequest) {
        return () -> URLEncodedUtils.parse(httpRequest.getURI(), StandardCharsets.UTF_8).stream()
                .collect(groupingBy(NameValuePair::getName, mapping(NameValuePair::getValue, toList())));
    }

    @Nullable
    private static MediaType getContentType(HttpHeaders headers) {
        return Optional.ofNullable(headers.get(HttpHeaders.CONTENT_TYPE))
                .map(contentType -> MediaType.valueOf(contentType.get(0)))
                .orElse(null);
    }
}