package io.wiretap.http.incoming.provider.httpinfo;

import ch.qos.logback.access.common.spi.IAccessEvent;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import io.wiretap.http.message.BufferedHttpMessageInfo;
import io.wiretap.http.message.HttpMessageInfo;
import io.wiretap.http.message.HttpMessageInfo.RequestDirection;
import io.wiretap.http.message.settings.HttpInfoLogMessageSettings;
import io.wiretap.http.message.settings.RestControllerLogMessageSettings;
import io.wiretap.configuration.WiretapAccessLogFieldsProperties;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.body.BodyParser;
import io.wiretap.http.outgoing.interceptor.Supplier;
import io.wiretap.util.FieldVisibilityMap;
import io.wiretap.util.HeaderSelector;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
 * Logback-access provider plugged into {@code logback-access.xml} that emits the
 * full {@code http_info} object describing the inbound HTTP request and response.
 */
@Slf4j
public class HttpInfoMessageProvider extends AbstractFieldJsonProvider<IAccessEvent> {
    private final BodyParser bodyParser;
    private final RestControllerLogMessageSettings logSettings;
    private final ObjectMapper mapper;
    private final boolean isPrettyLog;
    private final HttpAccessFieldNames httpFieldNames;
    @Nullable
    private final HttpUrlMaskingHandler urlMaskingHandler;
    @Nullable
    private final HttpRequestParamsMaskingHandler paramsMaskingHandler;

    public HttpInfoMessageProvider(
            final BodyParser bodyParser,
            final RestControllerLogMessageSettings logSettings,
            @Value("${wiretap.pretty-print:false}") boolean isPrettyLog,
            final WiretapAccessLogFieldsProperties fieldNames,
            @Nullable HttpUrlMaskingHandler urlMaskingHandler,
            @Nullable HttpRequestParamsMaskingHandler paramsMaskingHandler
    ) {
        super();
        this.bodyParser = bodyParser;
        this.mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        setFieldName(fieldNames.getHttpInfo());
        this.logSettings = logSettings;
        this.isPrettyLog = isPrettyLog;
        this.httpFieldNames = fieldNames.getHttp();
        this.urlMaskingHandler = urlMaskingHandler;
        this.paramsMaskingHandler = paramsMaskingHandler;
    }

    @PostConstruct
    public synchronized void init() {
        LazyHttpInfoMessageProvider.setProvider(this);
    }

    @Override
    public void writeTo(JsonGenerator generator, IAccessEvent event) {
        try {
            generator.writeName(this.getFieldName());
            final String requestUrl = event.getRequestURI();

            final HttpInfoLogMessageSettings specificLogSettings = logSettings.getRequestSettingsByUrl(requestUrl);
            final HttpServletRequest httpRequest = event.getRequest();
            final BufferedHttpMessageInfo buffered = BufferedHttpBodyHolder.get(httpRequest);

            final FieldVisibilityMap<HttpInfoLogMessageSettings.HttpConfigurableField> visibilityMap = specificLogSettings.getVisibilitySettings();
            final Supplier<JsonNode> responseBodySupplier = () -> {
                final MediaType responseContentType = Optional.ofNullable(event.getResponseHeaderMap().get(HttpHeaders.CONTENT_TYPE))
                        .map(MediaType::valueOf)
                        .orElse(null);
                return bodyParser.parseResponseBody(responseBodyWithFallback(event.getResponseContent(), buffered), requestUrl, responseContentType, specificLogSettings.getHttpBodySettings());
            };
            final Supplier<JsonNode> requestBodySupplier = () -> {
                final MediaType requestContentType = Optional.ofNullable(event.getRequestHeaderMap().get(HttpHeaders.CONTENT_TYPE))
                        .map(MediaType::valueOf)
                        .orElse(null);
                return bodyParser.parseRequestBody(requestBodyWithFallback(event.getRequestContent(), buffered), requestUrl, requestContentType, specificLogSettings.getHttpBodySettings());
            };


            final Supplier<Map<String, String>> responseHeadersSupplier = getHeadersSupplier(specificLogSettings.getResponseHeaders(), event.getResponseHeaderMap());
            final Supplier<Map<String, String>> requestHeadersSupplier = getHeadersSupplier(specificLogSettings.getRequestHeaders(), event.getRequestHeaderMap());
            final Supplier<Map<String, List<String>>> requestParamsSupplier = getRequestParamsSupplier(event.getRequestParameterMap());

            final String requestBodyString = getStringBody(visibilityMap.getVisible(REQUEST_BODY, requestBodySupplier));
            final String responseBodyString = getStringBody(visibilityMap.getVisible(RESPONSE_BODY, responseBodySupplier));

            final boolean isXmlBody = isXmlBody(getContentType(event.getRequestHeaderMap()));

            final HttpMessageInfo message = HttpMessageInfo.builder()
                    .requestDirection(RequestDirection.INCOMING)
                    .requestUrl(Boolean.TRUE.equals(visibilityMap.get(REQUEST_URL)) ? getMaskedRequestUrl(requestUrl) : null)
                    .httpMethod(event.getMethod())
                    .protocol(event.getProtocol())
                    .elapsedTime(event.getElapsedTime())
                    .returnCode(event.getStatusCode())
                    .requestBody(requestBodyString)
                    .requestBodyLength(requestBodyLengthWithFallback(event, buffered)) // capture the original (pre-processing) body length
                    .responseBody(responseBodyString)
                    .responseBodyLength(responseBodyLengthWithFallback(event, buffered)) // capture the original (pre-processing) body length
                    .xmlBodyType(getXmlType(event.getRequestContent(), isXmlBody))
                    .requestParams(maskRequestParams(visibilityMap.getVisible(REQUEST_PARAMS, requestParamsSupplier)))
                    .requestHeaders(visibilityMap.getVisible(REQUEST_HEADERS, requestHeadersSupplier))
                    .responseHeaders(visibilityMap.getVisible(RESPONSE_HEADERS, responseHeadersSupplier))
                    .build();

            generator.writeRawValue(
                    isPrettyLog ?
                            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(message.toMap(httpFieldNames)) :
                            mapper.writer().writeValueAsString(message.toMap(httpFieldNames))
            );
        } catch (Throwable e) {
            log.error("Error while providing to log http-info...", e);
        }
    }

    private static long requestBodyLengthWithFallback(IAccessEvent event, @Nullable BufferedHttpMessageInfo buffered) {
        int length = event.getRequestContent().length();
        if (length != 0) return length;
        return buffered != null ? buffered.requestBodyLength() : 0L;
    }

    private static long responseBodyLengthWithFallback(IAccessEvent event, @Nullable BufferedHttpMessageInfo buffered) {
        int length = event.getResponseContent().length();
        if (length != 0) return length;
        return buffered != null ? buffered.responseBodyLength() : 0L;
    }

    private static String requestBodyWithFallback(String requestBodyString, @Nullable BufferedHttpMessageInfo buffered) {
        if (!StringUtils.isEmpty(requestBodyString)) return requestBodyString;
        return buffered != null ? buffered.requestBody() : null;
    }

    private static String responseBodyWithFallback(String responseBodyString, @Nullable BufferedHttpMessageInfo buffered) {
        if (!StringUtils.isEmpty(responseBodyString)) return responseBodyString;
        return buffered != null ? buffered.responseBody() : null;
    }

    private String getMaskedRequestUrl(String notMaskedUrl) {
        return logSettings.isEnableUrlMasking() && urlMaskingHandler != null
                ? urlMaskingHandler.maskUrl(notMaskedUrl) : notMaskedUrl;
    }

    private Map<String, List<String>> maskRequestParams(Map<String, List<String>> params) {
        if (params == null
                || !logSettings.isEnableRequestParamsMasking()
                || paramsMaskingHandler == null) {
            return params;
        }
        return params.entrySet().stream()
                .collect(Collectors.toMap(
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

    private MediaType getContentType(Map<String, String> headersMap) {
        return Optional.ofNullable(headersMap.get(HttpHeaders.CONTENT_TYPE))
                .map(MediaType::valueOf)
                .orElse(null);
    }

    private Supplier<Map<String, String>> getHeadersSupplier(Collection<String> neededHeaderNames, Map<String, String> allHeaders) {
        return () -> HeaderSelector.select(neededHeaderNames, allHeaders);
    }

    private Supplier<Map<String, List<String>>> getRequestParamsSupplier(Map<String, String[]> requestParamsMap) {
        return () -> requestParamsMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> Arrays.asList(entry.getValue())));
    }
}

