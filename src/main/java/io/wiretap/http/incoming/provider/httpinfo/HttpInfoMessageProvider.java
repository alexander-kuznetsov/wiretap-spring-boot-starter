package io.wiretap.http.incoming.provider.httpinfo;

import ch.qos.logback.access.spi.IAccessEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import io.wiretap.http.message.HttpMessageInfo;
import io.wiretap.http.message.HttpMessageInfo.RequestDirection;
import io.wiretap.http.message.settings.HttpInfoLogMessageSettings;
import io.wiretap.http.message.settings.RestControllerLogMessageSettings;
import io.wiretap.http.message.settings.body.BodyParser;
import io.wiretap.http.outgoing.interceptor.Supplier;
import io.wiretap.util.FieldVisibilityMap;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
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
import static io.wiretap.util.MaskUtil.maskAllPans;
import static io.wiretap.util.MaskUtil.maskPhoneNumber;

/**
 * Logback-access provider plugged into {@code logback-access.xml} that emits the
 * full {@code http_info} object describing the inbound HTTP request and response.
 */
@Slf4j
@Component
public class HttpInfoMessageProvider extends AbstractFieldJsonProvider<IAccessEvent> {
    private final BodyParser bodyParser;
    private final RestControllerLogMessageSettings logSettings;
    private final ObjectMapper mapper;
    private final boolean isPrettyLog;

    @Autowired
    public HttpInfoMessageProvider(
            final BodyParser bodyParser,
            final RestControllerLogMessageSettings logSettings,
            @Value("${wiretap.pretty-print:false}") boolean isPrettyLog
    ) {
        super();
        this.bodyParser = bodyParser;
        this.mapper = new ObjectMapper();
        setFieldName("http_info");
        this.logSettings = logSettings;
        this.isPrettyLog = isPrettyLog;
    }

    @PostConstruct
    public synchronized void init() {
        LazyHttpInfoMessageProvider.provider = this;
    }

    @Override
    public void writeTo(JsonGenerator generator, IAccessEvent event) {
        try {
            generator.writeFieldName(this.getFieldName());
            final String requestUrl = event.getRequestURI();

            final HttpInfoLogMessageSettings specificLogSettings = logSettings.getRequestSettingsByUrl(requestUrl);

            final FieldVisibilityMap<HttpInfoLogMessageSettings.HttpConfigurableField> visibilityMap = specificLogSettings.getVisibilitySettings();
            final Supplier<JsonNode> responseBodySupplier = () -> {
                final MediaType responseContentType = Optional.ofNullable(event.getResponseHeaderMap().get(HttpHeaders.CONTENT_TYPE))
                        .map(MediaType::valueOf)
                        .orElse(null);
                return bodyParser.parseResponseBody(getResponseBodyWithFallback(event.getResponseContent()), requestUrl, responseContentType, specificLogSettings.getHttpBodySettings());
            };
            final Supplier<JsonNode> requestBodySupplier = () -> {
                final MediaType requestContentType = Optional.ofNullable(event.getRequestHeaderMap().get(HttpHeaders.CONTENT_TYPE))
                        .map(MediaType::valueOf)
                        .orElse(null);
                return bodyParser.parseRequestBody(getRequestBodyWithFallback(event.getRequestContent()), requestUrl, requestContentType, specificLogSettings.getHttpBodySettings());
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
                    .requestBodyLength(getRequestBodyLengthWithFallback(event)) // capture the original (pre-processing) body length
                    .responseBody(responseBodyString)
                    .responseBodyLength(getResponseBodyLengthWithFallback(event)) // capture the original (pre-processing) body length
                    .xmlBodyType(getXmlType(event.getRequestContent(), isXmlBody))
                    .requestParams(visibilityMap.getVisible(REQUEST_PARAMS, requestParamsSupplier))
                    .requestHeaders(visibilityMap.getVisible(REQUEST_HEADERS, requestHeadersSupplier))
                    .responseHeaders(visibilityMap.getVisible(RESPONSE_HEADERS, responseHeadersSupplier))
                    .build();

            generator.writeRawValue(
                    isPrettyLog ?
                            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(message) :
                            mapper.writer().writeValueAsString(message)
            );
        } catch (Throwable e) {
            log.error("Error while providing to log http-info...", e);
        } finally {
            BufferedHttpBodyThreadKeeper.clear();
        }
    }

    private long getRequestBodyLengthWithFallback(IAccessEvent event) {
        int length = event.getRequestContent().length();
        return length != 0 ? length : BufferedHttpBodyThreadKeeper.getRequestBodyLength();
    }

    private long getResponseBodyLengthWithFallback(IAccessEvent event) {
        int length = event.getResponseContent().length();
        return length != 0 ? length : BufferedHttpBodyThreadKeeper.getResponseBodyLength();
    }

    private String getRequestBodyWithFallback(String requestBodyString) {
        return StringUtils.isEmpty(requestBodyString) ? BufferedHttpBodyThreadKeeper.getRequestBody() : requestBodyString;
    }

    private String getResponseBodyWithFallback(String responseBodyString) {
        return StringUtils.isEmpty(responseBodyString) ? BufferedHttpBodyThreadKeeper.getResponseBody() : responseBodyString;
    }

    private String getMaskedRequestUrl(String notMaskedUrl) {
        return logSettings.isEnableUrlMasking() ?
                maskPhoneNumber(maskAllPans(notMaskedUrl, true)) : notMaskedUrl;
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
        return () -> neededHeaderNames.stream()
                .filter(headerName -> allHeaders.get(headerName) != null)
                .collect(Collectors.toMap(Function.identity(), allHeaders::get));
    }

    private Supplier<Map<String, List<String>>> getRequestParamsSupplier(Map<String, String[]> requestParamsMap) {
        return () -> requestParamsMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> Arrays.asList(entry.getValue())));
    }
}

