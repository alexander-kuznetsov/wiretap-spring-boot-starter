package io.wiretap.http.outgoing.interceptor.webclient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import io.wiretap.http.message.HttpMessageInfo;
import io.wiretap.http.message.HttpRequestParamsMaskingHandler;
import io.wiretap.http.message.HttpUrlMaskingHandler;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.HttpInfoLogMessageSettings;
import io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField;
import io.wiretap.http.message.settings.WebClientLogMessageSettings;
import io.wiretap.http.message.settings.body.BodyParser;
import io.wiretap.http.message.settings.body.HttpBodySettings;
import io.wiretap.http.outgoing.interceptor.Supplier;
import io.wiretap.metrics.BodyMetricsContext;
import io.wiretap.metrics.WiretapMetrics;
import io.wiretap.util.FieldVisibilityMap;
import io.wiretap.util.HeaderSelector;
import io.wiretap.util.HttpStatusClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.wiretap.http.message.HttpMessageInfo.RequestDirection.OUTGOING;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.REQUEST_BODY;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.REQUEST_HEADERS;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.REQUEST_PARAMS;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.REQUEST_URL;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.RESPONSE_BODY;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.RESPONSE_HEADERS;
import static io.wiretap.util.HttpBodyUtils.getStringBody;
import org.jetbrains.annotations.Nullable;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * WebFlux {@link ExchangeFilterFunction} that logs outbound HTTP calls issued via
 * {@code WebClient} (or any client built on top of it, such as
 * {@code graphql.kickstart.spring.webclient.boot.GraphQLWebClient}).
 * <p>
 * Three guarantees keep the filter safe to install on any auto-configured
 * {@code WebClient.Builder}:
 * <ul>
 *   <li><b>Streaming-aware</b> — responses with content types like
 *       {@code text/event-stream}, {@code application/x-ndjson},
 *       {@code application/octet-stream}, or gRPC variants are logged with
 *       metadata only; the body Flux is never joined or mutated, so
 *       Server-Sent Events and large downloads pass through untouched.</li>
 *   <li><b>Visibility-aware capture</b> — when {@code REQUEST_BODY} or
 *       {@code RESPONSE_BODY} visibility is disabled for a URL, the
 *       corresponding body is not captured at all (no decorator, no buffer
 *       drain) — saves both memory and CPU.</li>
 *   <li><b>Bounded capture</b> — captured bytes are capped at
 *       {@code httpBodySettings.maxBodyLength} on the way through, regardless
 *       of payload size, so a multi-MB body never inflates heap usage past
 *       the configured limit.</li>
 * </ul>
 */
public class WebClientLoggingFilter implements ExchangeFilterFunction {

    private static final Logger log = LoggerFactory.getLogger(WebClientLoggingFilter.class);
    private static final String HTTP_INFO_MDC_NAME = "HTTP-REQUEST-LOG";

    /**
     * Content types whose response bodies must never be buffered for logging.
     * Joining them would either hang forever (SSE), break ordering guarantees,
     * or pin large amounts of memory per request.
     */
    private static final Set<MediaType> STREAMING_CONTENT_TYPES = Set.of(
            MediaType.TEXT_EVENT_STREAM,
            MediaType.APPLICATION_NDJSON,
            MediaType.APPLICATION_OCTET_STREAM,
            MediaType.parseMediaType("multipart/x-mixed-replace"),
            MediaType.parseMediaType("application/grpc"),
            MediaType.parseMediaType("application/grpc+proto"),
            MediaType.parseMediaType("application/grpc+json")
    );

    static final String STREAMING_BODY_MARKER = "[streaming response — body not captured]";
    private static final String CLIENT = "webclient";
    private static final String DIRECTION = "outgoing";

    private final WebClientLogMessageSettings settings;
    private final BodyParser bodyParser;
    private final HttpAccessFieldNames httpFieldNames;
    @Nullable
    private final HttpUrlMaskingHandler urlMaskingHandler;
    @Nullable
    private final HttpRequestParamsMaskingHandler paramsMaskingHandler;
    private final WiretapMetrics metrics;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebClientLoggingFilter(
            WebClientLogMessageSettings settings,
            BodyParser bodyParser,
            HttpAccessFieldNames httpFieldNames,
            @Nullable HttpUrlMaskingHandler urlMaskingHandler,
            @Nullable HttpRequestParamsMaskingHandler paramsMaskingHandler,
            WiretapMetrics metrics
    ) {
        this.settings = settings;
        this.bodyParser = bodyParser;
        this.httpFieldNames = httpFieldNames;
        this.urlMaskingHandler = urlMaskingHandler;
        this.paramsMaskingHandler = paramsMaskingHandler;
        this.metrics = metrics;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        String requestUrl = request.url().toString();

        if (settings.getExcludeRequestPatterns().stream().anyMatch(requestUrl::matches)) {
            metrics.recordHttpSkipped(DIRECTION, CLIENT, "exclude_pattern");
            return next.exchange(request);
        }

        HttpInfoLogMessageSettings urlSettings = settings.getRequestSettingsByUrl(requestUrl);
        FieldVisibilityMap<HttpConfigurableField> visibility = urlSettings.getVisibilitySettings();
        boolean captureRequestBody = Boolean.TRUE.equals(visibility.get(REQUEST_BODY));
        boolean captureResponseBody = Boolean.TRUE.equals(visibility.get(RESPONSE_BODY));
        int maxBodyLength = urlSettings.getHttpBodySettings().getMaxBodyLength();

        AtomicReference<String> capturedRequestBody = new AtomicReference<>("");
        long startTime = System.currentTimeMillis();
        long startNanos = metrics.startSample();

        ClientRequest wrappedRequest = wrapRequestWithHeadersAndOptionalBodyCapture(
                request, requestUrl, capturedRequestBody, captureRequestBody, maxBodyLength);

        return next.exchange(wrappedRequest)
                .flatMap(response -> handleResponse(
                        request, response, capturedRequestBody, startTime, startNanos,
                        captureResponseBody, maxBodyLength))
                .onErrorResume(ex -> {
                    // The downstream call failed; everything up to here is the
                    // downstream wait, so subtract it (mirrors the success path)
                    // and leave only the partial-log work below as wiretap overhead.
                    long downstreamNanos = metrics.startSample() - startNanos;
                    logPartialRequest(request, capturedRequestBody.get(), startTime);
                    metrics.recordHttpRequest(startNanos, downstreamNanos, DIRECTION, CLIENT, "exception", "exception");
                    metrics.recordHttpBodyCaptureFailure(DIRECTION, CLIENT, "capture");
                    return Mono.error(ex);
                });
    }

    private ClientRequest wrapRequestWithHeadersAndOptionalBodyCapture(
            ClientRequest original, String requestUrl,
            AtomicReference<String> capturedBody, boolean captureRequestBody, int maxCapture) {

        ClientRequest.Builder builder = ClientRequest.from(original);

        settings.getAdditionalRequestHeaders().stream()
                .filter(h -> requestUrl.matches(h.getMatchUrlPattern()))
                .findFirst()
                .ifPresent(h -> h.getAdditionalHeaderNames().forEach(name -> {
                    String value = MDC.get(name);
                    if (value != null) builder.header(name, value);
                }));

        if (!captureRequestBody) {
            return builder.build();
        }

        return builder
                .body((outputMessage, context) -> {
                    CaptureBodyClientHttpRequest capturing =
                            new CaptureBodyClientHttpRequest(outputMessage, maxCapture);
                    return original.body().insert(capturing, context)
                            .doOnSuccess(v -> capturedBody.set(capturing.getCapturedBody()));
                })
                .build();
    }

    private Mono<ClientResponse> handleResponse(
            ClientRequest request, ClientResponse response,
            AtomicReference<String> capturedRequestBody, long startTime, long startNanos,
            boolean captureResponseBody, int maxCapture) {

        MediaType responseContentType = response.headers().contentType().orElse(null);
        boolean streaming = isStreaming(responseContentType);

        if (streaming || !captureResponseBody) {
            // Response headers have arrived and wiretap does not read the body here,
            // so everything up to this point is downstream; only the serialisation
            // inside logFullRequest below is wiretap overhead.
            long downstreamNanos = metrics.startSample() - startNanos;
            String marker = streaming ? STREAMING_BODY_MARKER : "";
            long elapsed = System.currentTimeMillis() - startTime;
            logFullRequest(request, capturedRequestBody.get(), response, marker, streaming, elapsed);
            int status = response.statusCode().value();
            if (streaming) {
                metrics.recordHttpSkipped(DIRECTION, CLIENT, "streaming");
            }
            metrics.recordHttpRequest(startNanos, downstreamNanos, DIRECTION, CLIENT, HttpStatusClassifier.outcome(status), HttpStatusClassifier.statusGroup(status));
            return Mono.just(response);
        }

        return joinAndLogResponseBody(request, response, capturedRequestBody, startTime, startNanos, maxCapture);
    }

    /**
     * Joins the response body, captures up to {@code maxCapture} bytes for the
     * log line, then re-emits the full body downstream so consumers see the
     * unchanged payload. Memory for the captured string is bounded; memory for
     * the joined payload is not (downstream would consume it anyway).
     * <p>
     * Streaming and visibility-disabled cases are handled upstream and never
     * reach this method.
     */
    private Mono<ClientResponse> joinAndLogResponseBody(
            ClientRequest request, ClientResponse response,
            AtomicReference<String> capturedRequestBody, long startTime, long startNanos, int maxCapture) {

        return DataBufferUtils.join(response.body(BodyExtractors.toDataBuffers()))
                .defaultIfEmpty(DefaultDataBufferFactory.sharedInstance.wrap(new byte[0]))
                .flatMap(joined -> {
                    // The response body is fully received at this point, so the
                    // network read counts as downstream; only the work below
                    // (read / parse / serialise) is wiretap overhead.
                    long downstreamNanos = metrics.startSample() - startNanos;
                    try {
                        int total = joined.readableByteCount();
                        int captureSize = Math.min(total, maxCapture);
                        byte[] all = new byte[total];
                        joined.read(all);
                        DataBufferUtils.release(joined);

                        boolean truncated = total > maxCapture;
                        String captured = new String(all, 0, captureSize, StandardCharsets.UTF_8);
                        if (truncated) {
                            captured += CaptureBodyClientHttpRequest.TRUNCATED_MARKER;
                        }
                        long elapsed = System.currentTimeMillis() - startTime;
                        MediaType responseContentType = response.headers().contentType().orElse(null);
                        logFullRequest(request, capturedRequestBody.get(), response, captured, truncated, elapsed);

                        int status = response.statusCode().value();
                        metrics.recordHttpBodySize(DIRECTION, CLIENT,
                                BodyMetricsContext.classify(responseContentType), "response", total);
                        long requestBodyLength = request.headers().getContentLength();
                        if (requestBodyLength >= 0) {
                            metrics.recordHttpBodySize(DIRECTION, CLIENT,
                                    BodyMetricsContext.classify(request.headers().getContentType()),
                                    "request", requestBodyLength);
                        }
                        metrics.recordHttpRequest(startNanos, downstreamNanos, DIRECTION, CLIENT, HttpStatusClassifier.outcome(status), HttpStatusClassifier.statusGroup(status));

                        DataBuffer replay = DefaultDataBufferFactory.sharedInstance.wrap(all);
                        return Mono.just(response.mutate().body(Flux.just(replay)).build());
                    } catch (Exception e) {
                        DataBufferUtils.release(joined);
                        metrics.recordHttpBodyCaptureFailure(DIRECTION, CLIENT, "capture");
                        return Mono.error(e);
                    }
                });
    }

    private static boolean isStreaming(MediaType contentType) {
        if (contentType == null) return false;
        for (MediaType streaming : STREAMING_CONTENT_TYPES) {
            if (streaming.isCompatibleWith(contentType)) return true;
        }
        return false;
    }

    private void logFullRequest(
            ClientRequest request, String requestBodyStr,
            ClientResponse response, String responseBodyStr,
            boolean useResponseStringAsIs, long elapsed) {
        try {
            String requestUrl = request.url().toString();
            HttpInfoLogMessageSettings specificSettings = settings.getRequestSettingsByUrl(requestUrl);
            FieldVisibilityMap<HttpConfigurableField> visibility = specificSettings.getVisibilitySettings();
            HttpBodySettings bodySettings = specificSettings.getHttpBodySettings();

            MediaType requestContentType = request.headers().getContentType();
            MediaType responseContentType = response.headers().contentType().orElse(null);

            boolean requestTruncated = requestBodyStr != null
                    && requestBodyStr.endsWith(CaptureBodyClientHttpRequest.TRUNCATED_MARKER);

            Supplier<JsonNode> requestBodySupplier = requestTruncated
                    ? () -> new TextNode(requestBodyStr)
                    : () -> bodyParser.parseRequestBody(requestBodyStr, requestUrl, requestContentType, bodySettings,
                            new BodyMetricsContext(DIRECTION, CLIENT, BodyMetricsContext.classify(requestContentType)));
            Supplier<JsonNode> responseBodySupplier = useResponseStringAsIs
                    ? () -> new TextNode(responseBodyStr)
                    : () -> bodyParser.parseResponseBody(responseBodyStr, requestUrl, responseContentType, bodySettings,
                            new BodyMetricsContext(DIRECTION, CLIENT, BodyMetricsContext.classify(responseContentType)));

            Supplier<Map<String, String>> requestHeadersSupplier =
                    headersSupplier(specificSettings.getRequestHeaders(), request.headers().toSingleValueMap());
            Supplier<Map<String, String>> responseHeadersSupplier =
                    headersSupplier(specificSettings.getResponseHeaders(), response.headers().asHttpHeaders().toSingleValueMap());
            Supplier<Map<String, List<String>>> requestParamsSupplier = requestParamsSupplier(request);

            HttpMessageInfo info = HttpMessageInfo.builder()
                    .requestDirection(OUTGOING)
                    .requestUrl(Boolean.TRUE.equals(visibility.get(REQUEST_URL)) ? maskedUrl(requestUrl) : null)
                    .httpMethod(Optional.ofNullable(request.method()).map(HttpMethod::name).orElse(null))
                    .requestHeaders(visibility.getVisible(REQUEST_HEADERS, requestHeadersSupplier))
                    .requestParams(maskRequestParams(visibility.getVisible(REQUEST_PARAMS, requestParamsSupplier)))
                    .requestBody(getStringBody(visibility.getVisible(REQUEST_BODY, requestBodySupplier)))
                    .requestBodyLength(request.headers().getContentLength())
                    .responseHeaders(visibility.getVisible(RESPONSE_HEADERS, responseHeadersSupplier))
                    .responseBody(getStringBody(visibility.getVisible(RESPONSE_BODY, responseBodySupplier)))
                    .responseBodyLength(response.headers().contentLength().orElse(-1L))
                    .returnCode(response.statusCode().value())
                    .elapsedTime(elapsed)
                    .build();

            logToMdc(info);
        } catch (Exception e) {
            log.error("Error while logging WebClient http-info", e);
            metrics.recordHttpBodyCaptureFailure(DIRECTION, CLIENT, "capture");
        }
    }

    private void logPartialRequest(ClientRequest request, String requestBodyStr, long startTime) {
        try {
            String requestUrl = request.url().toString();
            HttpInfoLogMessageSettings specificSettings = settings.getRequestSettingsByUrl(requestUrl);
            FieldVisibilityMap<HttpConfigurableField> visibility = specificSettings.getVisibilitySettings();

            boolean requestTruncated = requestBodyStr != null
                    && requestBodyStr.endsWith(CaptureBodyClientHttpRequest.TRUNCATED_MARKER);
            Supplier<JsonNode> requestBodySupplier = requestTruncated
                    ? () -> new TextNode(requestBodyStr)
                    : () -> bodyParser.parseRequestBody(requestBodyStr, requestUrl,
                            request.headers().getContentType(), specificSettings.getHttpBodySettings(),
                            new BodyMetricsContext(DIRECTION, CLIENT, BodyMetricsContext.classify(request.headers().getContentType())));
            Supplier<Map<String, String>> requestHeadersSupplier =
                    headersSupplier(specificSettings.getRequestHeaders(), request.headers().toSingleValueMap());
            Supplier<Map<String, List<String>>> requestParamsSupplier = requestParamsSupplier(request);

            HttpMessageInfo info = HttpMessageInfo.builder()
                    .requestDirection(OUTGOING)
                    .requestUrl(Boolean.TRUE.equals(visibility.get(REQUEST_URL)) ? maskedUrl(requestUrl) : null)
                    .httpMethod(Optional.ofNullable(request.method()).map(HttpMethod::name).orElse(null))
                    .requestHeaders(visibility.getVisible(REQUEST_HEADERS, requestHeadersSupplier))
                    .requestParams(maskRequestParams(visibility.getVisible(REQUEST_PARAMS, requestParamsSupplier)))
                    .requestBody(getStringBody(visibility.getVisible(REQUEST_BODY, requestBodySupplier)))
                    .requestBodyLength(request.headers().getContentLength())
                    .elapsedTime(System.currentTimeMillis() - startTime)
                    .build();

            logToMdc(info);
        } catch (Exception e) {
            log.error("Error while logging partial WebClient http-info", e);
            metrics.recordHttpBodyCaptureFailure(DIRECTION, CLIENT, "capture");
        }
    }

    private void logToMdc(HttpMessageInfo info) {
        try {
            long serStart = metrics.startSample();
            String json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(info.toMap(httpFieldNames));
            metrics.recordJsonSerialization(serStart, "http", DIRECTION, CLIENT);
            try (MDC.MDCCloseable ignored = MDC.putCloseable(HTTP_INFO_MDC_NAME, json)) {
                log.info("Captured outgoing webclient request {}", maskedUrl(info.getRequestUrl()));
            }
        } catch (JsonProcessingException e) {
            log.error("Error serialising WebClient http-info", e);
            metrics.recordHttpBodyCaptureFailure(DIRECTION, CLIENT, "serialize");
        }
    }

    private String maskedUrl(String url) {
        if (url == null) return null;
        return settings.isEnableUrlMasking() && urlMaskingHandler != null
                ? urlMaskingHandler.maskUrl(url) : url;
    }

    private Map<String, List<String>> maskRequestParams(Map<String, List<String>> params) {
        if (params == null
                || !settings.isEnableRequestParamsMasking()
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

    private Supplier<Map<String, List<String>>> requestParamsSupplier(ClientRequest request) {
        return () -> URLEncodedUtils.parse(request.url(), StandardCharsets.UTF_8).stream()
                .collect(groupingBy(NameValuePair::getName, mapping(NameValuePair::getValue, toList())));
    }

    private Supplier<Map<String, String>> headersSupplier(
            java.util.Collection<String> needed, Map<String, String> all) {
        return () -> HeaderSelector.select(needed, all);
    }
}
