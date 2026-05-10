package io.wiretap.http.outgoing.interceptor.webclient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wiretap.http.message.HttpMessageInfo;
import io.wiretap.http.message.settings.AdditionalRequestHeaders;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField;
import io.wiretap.http.message.settings.WebClientLogMessageSettings;
import io.wiretap.http.message.settings.body.BodyParser;
import io.wiretap.http.outgoing.interceptor.Supplier;
import io.wiretap.util.FieldVisibilityMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static io.wiretap.http.message.HttpMessageInfo.RequestDirection.OUTGOING;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.REQUEST_BODY;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.REQUEST_HEADERS;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.REQUEST_URL;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.RESPONSE_BODY;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.RESPONSE_HEADERS;
import static io.wiretap.util.HttpBodyUtils.getStringBody;
import static io.wiretap.util.MaskUtil.maskAllPans;
import static io.wiretap.util.MaskUtil.maskPhoneNumber;
import static java.util.stream.Collectors.toMap;

/**
 * WebFlux {@link ExchangeFilterFunction} that logs outbound HTTP calls issued via
 * {@code WebClient} (or any client built on top of it, such as
 * {@code graphql.kickstart.spring.webclient.boot.GraphQLWebClient}).
 * <p>
 * Registers itself automatically via {@link io.wiretap.configuration.WebClientInterceptorConfiguration}
 * which applies it to the auto-configured {@code WebClient.Builder}.
 */
public class WebClientLoggingFilter implements ExchangeFilterFunction {

    private static final Logger log = LoggerFactory.getLogger(WebClientLoggingFilter.class);
    private static final String HTTP_INFO_MDC_NAME = "HTTP-REQUEST-LOG";

    private final WebClientLogMessageSettings settings;
    private final BodyParser bodyParser;
    private final HttpAccessFieldNames httpFieldNames;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebClientLoggingFilter(
            WebClientLogMessageSettings settings,
            BodyParser bodyParser,
            HttpAccessFieldNames httpFieldNames
    ) {
        this.settings = settings;
        this.bodyParser = bodyParser;
        this.httpFieldNames = httpFieldNames;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        String requestUrl = request.url().toString();

        if (settings.getExcludeRequestPatterns().stream().anyMatch(requestUrl::matches)) {
            return next.exchange(request);
        }

        AtomicReference<String> capturedRequestBody = new AtomicReference<>("");
        long startTime = System.currentTimeMillis();

        ClientRequest wrappedRequest = wrapRequestWithHeadersAndBodyCapture(request, requestUrl, capturedRequestBody);

        return next.exchange(wrappedRequest)
                .flatMap(response -> bufferResponseAndLog(request, response, capturedRequestBody, startTime))
                .onErrorResume(ex -> {
                    logPartialRequest(request, capturedRequestBody.get(), startTime);
                    return Mono.error(ex);
                });
    }

    private ClientRequest wrapRequestWithHeadersAndBodyCapture(
            ClientRequest original, String requestUrl, AtomicReference<String> capturedBody) {

        ClientRequest.Builder builder = ClientRequest.from(original);

        settings.getAdditionalRequestHeaders().stream()
                .filter(h -> requestUrl.matches(h.getMatchUrlPattern()))
                .findFirst()
                .ifPresent(h -> h.getAdditionalHeaderNames().forEach(name -> {
                    String value = MDC.get(name);
                    if (value != null) builder.header(name, value);
                }));

        return builder
                .body((outputMessage, context) -> {
                    CaptureBodyClientHttpRequest capturing = new CaptureBodyClientHttpRequest(outputMessage);
                    return original.body().insert(capturing, context)
                            .doOnSuccess(v -> capturedBody.set(capturing.getCapturedBody()));
                })
                .build();
    }

    private Mono<ClientResponse> bufferResponseAndLog(
            ClientRequest request, ClientResponse response,
            AtomicReference<String> capturedRequestBody, long startTime) {

        return DataBufferUtils.join(response.body(BodyExtractors.toDataBuffers()))
                .defaultIfEmpty(DefaultDataBufferFactory.sharedInstance.wrap(new byte[0]))
                .flatMap(joined -> {
                    try {
                        byte[] bytes = new byte[joined.readableByteCount()];
                        joined.read(bytes);
                        DataBufferUtils.release(joined);

                        String responseBody = new String(bytes, StandardCharsets.UTF_8);
                        long elapsed = System.currentTimeMillis() - startTime;
                        logFullRequest(request, capturedRequestBody.get(), response, responseBody, elapsed);

                        DataBuffer replayBuffer = DefaultDataBufferFactory.sharedInstance.wrap(bytes);
                        return Mono.just(response.mutate().body(Flux.just(replayBuffer)).build());
                    } catch (Exception e) {
                        DataBufferUtils.release(joined);
                        return Mono.error(e);
                    }
                });
    }

    private void logFullRequest(
            ClientRequest request, String requestBodyStr,
            ClientResponse response, String responseBodyStr, long elapsed) {
        try {
            String requestUrl = request.url().toString();
            String specificUrl = requestUrl;
            var specificSettings = settings.getRequestSettingsByUrl(specificUrl);
            FieldVisibilityMap<HttpConfigurableField> visibility = specificSettings.getVisibilitySettings();

            MediaType requestContentType = request.headers().getContentType();
            MediaType responseContentType = response.headers().contentType().orElse(null);

            Supplier<JsonNode> requestBodySupplier = () ->
                    bodyParser.parseRequestBody(requestBodyStr, requestUrl, requestContentType,
                            specificSettings.getHttpBodySettings());
            Supplier<JsonNode> responseBodySupplier = () ->
                    bodyParser.parseResponseBody(responseBodyStr, requestUrl, responseContentType,
                            specificSettings.getHttpBodySettings());

            Supplier<Map<String, String>> requestHeadersSupplier =
                    headersSupplier(specificSettings.getRequestHeaders(), request.headers().toSingleValueMap());
            Supplier<Map<String, String>> responseHeadersSupplier =
                    headersSupplier(specificSettings.getResponseHeaders(), response.headers().asHttpHeaders().toSingleValueMap());

            HttpMessageInfo info = HttpMessageInfo.builder()
                    .requestDirection(OUTGOING)
                    .requestUrl(Boolean.TRUE.equals(visibility.get(REQUEST_URL)) ? maskedUrl(requestUrl) : null)
                    .httpMethod(Optional.ofNullable(request.method()).map(HttpMethod::name).orElse(null))
                    .requestHeaders(visibility.getVisible(REQUEST_HEADERS, requestHeadersSupplier))
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
        }
    }

    private void logPartialRequest(ClientRequest request, String requestBodyStr, long startTime) {
        try {
            String requestUrl = request.url().toString();
            var specificSettings = settings.getRequestSettingsByUrl(requestUrl);
            FieldVisibilityMap<HttpConfigurableField> visibility = specificSettings.getVisibilitySettings();

            Supplier<JsonNode> requestBodySupplier = () ->
                    bodyParser.parseRequestBody(requestBodyStr, requestUrl,
                            request.headers().getContentType(), specificSettings.getHttpBodySettings());
            Supplier<Map<String, String>> requestHeadersSupplier =
                    headersSupplier(specificSettings.getRequestHeaders(), request.headers().toSingleValueMap());

            HttpMessageInfo info = HttpMessageInfo.builder()
                    .requestDirection(OUTGOING)
                    .requestUrl(Boolean.TRUE.equals(visibility.get(REQUEST_URL)) ? maskedUrl(requestUrl) : null)
                    .httpMethod(Optional.ofNullable(request.method()).map(HttpMethod::name).orElse(null))
                    .requestHeaders(visibility.getVisible(REQUEST_HEADERS, requestHeadersSupplier))
                    .requestBody(getStringBody(visibility.getVisible(REQUEST_BODY, requestBodySupplier)))
                    .requestBodyLength(request.headers().getContentLength())
                    .elapsedTime(System.currentTimeMillis() - startTime)
                    .build();

            logToMdc(info);
        } catch (Exception e) {
            log.error("Error while logging partial WebClient http-info", e);
        }
    }

    private void logToMdc(HttpMessageInfo info) {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(info.toMap(httpFieldNames));
            try (MDC.MDCCloseable ignored = MDC.putCloseable(HTTP_INFO_MDC_NAME, json)) {
                log.info("Captured outgoing webclient request {}", maskedUrl(info.getRequestUrl()));
            }
        } catch (JsonProcessingException e) {
            log.error("Error serialising WebClient http-info", e);
        }
    }

    private String maskedUrl(String url) {
        if (url == null) return null;
        return settings.isEnableUrlMasking() ? maskPhoneNumber(maskAllPans(url, true)) : url;
    }

    private Supplier<Map<String, String>> headersSupplier(
            java.util.Collection<String> needed, Map<String, String> all) {
        return () -> needed.stream()
                .filter(all::containsKey)
                .collect(toMap(Function.identity(), all::get));
    }
}
