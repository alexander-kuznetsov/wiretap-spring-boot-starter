package io.wiretap.http.outgoing.interceptor.webservicetemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.client.WebServiceClientException;
import org.springframework.ws.client.support.interceptor.ClientInterceptorAdapter;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.soap.saaj.SaajSoapMessage;
import org.springframework.ws.transport.WebServiceConnection;
import org.springframework.ws.transport.context.TransportContext;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.AbstractHttpSenderConnection;
import org.springframework.ws.transport.http.ClientHttpRequestConnection;
import io.wiretap.http.message.HttpMessageInfo;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.HttpInfoLogMessageSettings;
import io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField;
import io.wiretap.http.message.settings.WebServiceTemplateLogMessageSettings;
import io.wiretap.http.message.settings.body.BodyParser;
import io.wiretap.http.outgoing.interceptor.Supplier;
import io.wiretap.util.FieldVisibilityMap;
import io.wiretap.util.HeaderSelector;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.wiretap.http.message.HttpMessageInfo.RequestDirection.OUTGOING;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.REQUEST_BODY;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.REQUEST_HEADERS;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.REQUEST_URL;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.RESPONSE_BODY;
import static io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField.RESPONSE_HEADERS;
import static io.wiretap.util.HttpBodyUtils.getStringBody;
import io.wiretap.http.message.HttpUrlMaskingHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Logs outbound SOAP requests issued through Spring's {@code WebServiceTemplate} client
 * in the same JSON shape as inbound HTTP and {@code RestTemplate} traffic.
 * <p>
 * Wire it into your client like:
 * <pre>
 * webServiceTemplate.setInterceptors(new ClientInterceptor[]{webServiceTemplateLoggingInterceptor});
 * </pre>
 */
public class WebServiceTemplateLoggingInterceptor extends ClientInterceptorAdapter {
    private static final Logger log = LoggerFactory.getLogger(WebServiceTemplateLoggingInterceptor.class);
    private static final String CUSTOM_LOG_MESSAGE = "HTTP-REQUEST-LOG";
    private static final String STARTED_AT = "startTime";
    private final WebServiceTemplateLogMessageSettings soapLogSettings;
    private final BodyParser bodyParser;
    private final ObjectMapper objectMapper = tools.jackson.databind.json.JsonMapper.builder().build();
    private final HttpAccessFieldNames httpFieldNames;
    @Nullable
    private final HttpUrlMaskingHandler urlMaskingHandler;

    public WebServiceTemplateLoggingInterceptor(
            WebServiceTemplateLogMessageSettings soapLogSettings,
            BodyParser bodyParser,
            HttpAccessFieldNames httpFieldNames,
            @Nullable HttpUrlMaskingHandler urlMaskingHandler
    ) {
        this.soapLogSettings = soapLogSettings;
        this.bodyParser = bodyParser;
        this.httpFieldNames = httpFieldNames;
        this.urlMaskingHandler = urlMaskingHandler;
    }

    @Override
    public boolean handleRequest(MessageContext messageContext) {
        long startTime = System.currentTimeMillis();
        messageContext.setProperty(STARTED_AT, startTime);

        final TransportContext context = TransportContextHolder.getTransportContext();
        if (context.getConnection() instanceof ClientHttpRequestConnection connection) {
            final String requestUrl = getRequestUrl(connection);

            soapLogSettings.getAdditionalRequestHeaders().stream()
                    .filter(additionalRequestHeader -> requestUrl.matches(additionalRequestHeader.getMatchUrlPattern()))
                    .findFirst()
                    .ifPresent(additionalRequestHeaders -> addAdditionalHeaders(connection, additionalRequestHeaders.getAdditionalHeaderNames()));
        }

        return true;
    }

    private void addAdditionalHeaders(ClientHttpRequestConnection connection, List<String> additionalResponseHeaders) {
        additionalResponseHeaders.forEach(headerName -> {
            final String headerValue = MDC.get(headerName);
            if (headerValue != null) {
                try {
                    connection.addRequestHeader(headerName, headerValue);
                } catch (IOException e) {
                    log.error("Error while adding additional header: {}", headerName, e);
                }
            }
        });
    }

    private String getRequestUrl(ClientHttpRequestConnection connection) {
        try {
            return connection.getUri().toString();
        } catch (URISyntaxException e) {
            log.error("Error while getting request url", e);
            throw new RuntimeException(e);
        }
    }
    // The first invocation passes a non-null `ex` if the request failed; the
    // second invocation happens after normal completion to release resources.
    @Override
    public void afterCompletion(MessageContext messageContext, Exception ex) throws WebServiceClientException {
        if (ex != null) {
            log.error("An error occurred during the execution of the soap request", ex);
            logHttpInfo(messageContext);
        }
    }

    @Override
    public boolean handleResponse(MessageContext messageContext) throws WebServiceClientException {
        logHttpInfo(messageContext);
        return true;
    }

    private void logHttpInfo(MessageContext messageContext) {
        try {
            long startTime = (long) messageContext.getProperty(STARTED_AT);
            long duration = System.currentTimeMillis() - startTime;

            final HttpMessageInfo httpMessageInfo = getLogMessage(messageContext, duration);
            logRequest(httpMessageInfo);

        } catch (Exception e) {
            log.error("Error while providing to log web-service-template http-info...", e);
        }
    }

    private HttpMessageInfo getLogMessage(MessageContext messageContext, long duration) throws URISyntaxException, IOException {
        final TransportContext context = TransportContextHolder.getTransportContext();
        final WebServiceConnection connection = context.getConnection();

        final String requestURL = connection.getUri().toString();
        final HttpInfoLogMessageSettings specificLogSettings = soapLogSettings.getRequestSettingsByUrl(requestURL);

        boolean shouldSkip = soapLogSettings.getExcludeRequestPatterns().stream()
                .anyMatch(requestURL::matches);
        if (shouldSkip) {
            return null;
        }
        final Supplier<Map<String, String>> requestHeadersSupplier = getHeadersSupplier(specificLogSettings.getRequestHeaders(), messageContext.getRequest(), getHttpRequestHeaders(connection));
        final Supplier<Map<String, String>> responseHeadersSupplier = getHeadersSupplier(specificLogSettings.getResponseHeaders(), messageContext.getResponse(), getHttpResponseHeaders(connection));

        final String originalRequestBodyString = convertDOMSourceToString(messageContext.getRequest().getPayloadSource());
        final Supplier<JsonNode> requestBodySupplier = () -> bodyParser.parseRequestBody(
                originalRequestBodyString,
                requestURL,
                MediaType.APPLICATION_XML,
                specificLogSettings.getHttpBodySettings()
        );

        final String originalResponseBodyString = messageContext.hasResponse() ? convertDOMSourceToString(messageContext.getResponse().getPayloadSource()) : null;
        final Supplier<JsonNode> responseBodySupplier = () -> messageContext.hasResponse() ?
                bodyParser.parseRequestBody(
                        originalResponseBodyString,
                        requestURL,
                        MediaType.APPLICATION_XML,
                        specificLogSettings.getHttpBodySettings()
                ) : null;
        final FieldVisibilityMap<HttpConfigurableField> visibilityMap = specificLogSettings.getVisibilitySettings();
        final String requestBodyString = getStringBody(visibilityMap.getVisible(REQUEST_BODY, requestBodySupplier));
        final String responseBodyString = getStringBody(visibilityMap.getVisible(RESPONSE_BODY, responseBodySupplier));


        return HttpMessageInfo.builder()
                .requestDirection(OUTGOING)
                .requestUrl(Boolean.TRUE.equals(visibilityMap.get(REQUEST_URL)) ? getMaskedRequestUrl(requestURL) : null)
                .elapsedTime(duration)
                .returnCode(getSoapRequestHttpStatus(connection))
                .requestHeaders(visibilityMap.getVisible(REQUEST_HEADERS, requestHeadersSupplier))
                .responseHeaders(visibilityMap.getVisible(RESPONSE_HEADERS, responseHeadersSupplier))
                .requestBody(requestBodyString)
                .requestBodyLength(originalRequestBodyString.length())
                .responseBody(responseBodyString)
                .responseBodyLength(originalResponseBodyString != null ? originalResponseBodyString.length() : 0)
                .build();
    }

    private Supplier<Map<String, String>> getHeadersSupplier(final Collection<String> neededHeaderNames, final WebServiceMessage webServiceMessage, HttpHeaders allHttpHeaders) {
        return () -> {
            final Map<String, String> headers = new LinkedHashMap<>();
            if (webServiceMessage instanceof SaajSoapMessage saaj) {
                headers.putAll(HeaderSelector.selectMime(neededHeaderNames, saaj.getSaajMessage().getMimeHeaders()));
            }
            if (allHttpHeaders != null) {
                headers.putAll(HeaderSelector.select(neededHeaderNames, allHttpHeaders));
            }
            return headers.isEmpty() ? null : headers;
        };
    }
    private HttpHeaders getHttpRequestHeaders(WebServiceConnection webServiceConnection) {
        if (webServiceConnection instanceof ClientHttpRequestConnection clientHttpRequestConnection) {
            return clientHttpRequestConnection.getClientHttpRequest().getHeaders();
        }
        return null;
    }
    private HttpHeaders getHttpResponseHeaders(WebServiceConnection webServiceConnection) {
        if (webServiceConnection instanceof ClientHttpRequestConnection clientHttpRequestConnection) {
            return clientHttpRequestConnection.getClientHttpResponse().getHeaders();
        }
        return null;
    }
    private Integer getSoapRequestHttpStatus(WebServiceConnection connection) {
        try {
            final AbstractHttpSenderConnection httpSenderConnection = (AbstractHttpSenderConnection) connection;
            Class<?> myClass = httpSenderConnection.getClass();
            Method method = myClass.getDeclaredMethod("getResponseCode");
            method.setAccessible(true);
            return (int) method.invoke(httpSenderConnection);
        } catch (Exception e) {
            log.error("Error while getting http status of soap request", e);
            return null;
        }
    }

    /**
     * Writes the populated {@link HttpMessageInfo} into MDC, emits the log
     * event, and clears MDC via try-with-resources.
     *
     * @param logMessage SOAP request/response info
     */
    private void logRequest(final HttpMessageInfo logMessage) throws IOException {
        final String stringLogMessage = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(logMessage.toMap(httpFieldNames));

        try (final MDC.MDCCloseable ignored = MDC.putCloseable(CUSTOM_LOG_MESSAGE, stringLogMessage)) {
            log.info("Captured outgoing soap request {}", getMaskedRequestUrl(logMessage.getRequestUrl()));
        }
    }

    private String getMaskedRequestUrl(String notMaskedUrl) {
        return soapLogSettings.isEnableUrlMasking() && urlMaskingHandler != null
                ? urlMaskingHandler.maskUrl(notMaskedUrl) : notMaskedUrl;
    }

    private String convertDOMSourceToString(Source domSource) {
        try {
            final TransformerFactory transformerFactory = TransformerFactory.newInstance();
            final Transformer transformer = transformerFactory.newTransformer();
            final StringWriter writer = new StringWriter();

            // Convert DOMSource into a string
            transformer.transform(domSource, new StreamResult(writer));

            return writer.toString();
        } catch (TransformerException e) {
            log.error("Error while transform dom xml object to xml string", e);
            return null;
        }
    }
}