package io.wiretap.http.message.settings.body;

import tools.jackson.databind.JsonNode;
import io.wiretap.metrics.BodyMetricsContext;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * SPI for converting raw HTTP request/response payloads into the structured form
 * that ends up in {@code http_info.request_body} / {@code http_info.response_body}.
 * <p>
 * Provide a custom Spring bean to override the {@link DefaultBodyParser} when you
 * need request-URL-aware processing — e.g. additional anonymisation on a subset
 * of endpoints, or custom truncation rules.
 */
public interface BodyParser {

    /**
     * Parses the body of an inbound HTTP message.
     *
     * @param body        raw request body as a string
     * @param requestUrl  end-point being called; can be used to apply per-URL parsing
     *                    rules (e.g. masking sensitive payloads on specific controllers)
     * @param contentType payload media type — used for selective logging
     *                    (e.g. skipping base64-encoded images and similar binary content)
     * @param settings    body settings (truncation/masking thresholds)
     * @return inbound body parsed into a {@link JsonNode}
     */
    JsonNode parseRequestBody(String body, String requestUrl, MediaType contentType, HttpBodySettings settings);

    /**
     * Parses the body of an outbound HTTP message.
     *
     * @param body        raw response body as a string
     * @param requestUrl  end-point being called; can be used for per-URL parsing rules
     * @param contentType payload media type
     * @param settings    body settings (truncation/masking thresholds)
     * @return outbound body parsed into a {@link JsonNode}
     */
    JsonNode parseResponseBody(String body, String requestUrl, MediaType contentType, HttpBodySettings settings);

    /**
     * Parses the body of an outbound HTTP response straight from a buffered
     * {@link ClientHttpResponse}.
     */
    JsonNode parseResponseBody(
            ClientHttpResponse bufferingResponse,
            String requestUrl,
            MediaType contentType,
            HttpBodySettings settings
    ) throws IOException;

    /**
     * Metrics-aware variant of {@link #parseRequestBody(String, String, MediaType, HttpBodySettings)}.
     * The {@code metricsContext} (direction / client / content-type class) tags the
     * {@code wiretap.body.phase} timers emitted while parsing. The default implementation
     * ignores it and delegates, so existing {@link BodyParser} implementations keep working.
     */
    default JsonNode parseRequestBody(String body, String requestUrl, MediaType contentType, HttpBodySettings settings, BodyMetricsContext metricsContext) {
        return parseRequestBody(body, requestUrl, contentType, settings);
    }

    /** Metrics-aware variant of {@link #parseResponseBody(String, String, MediaType, HttpBodySettings)}. */
    default JsonNode parseResponseBody(String body, String requestUrl, MediaType contentType, HttpBodySettings settings, BodyMetricsContext metricsContext) {
        return parseResponseBody(body, requestUrl, contentType, settings);
    }

    /** Metrics-aware variant of {@link #parseResponseBody(ClientHttpResponse, String, MediaType, HttpBodySettings)}. */
    default JsonNode parseResponseBody(ClientHttpResponse bufferingResponse, String requestUrl, MediaType contentType, HttpBodySettings settings, BodyMetricsContext metricsContext) throws IOException {
        return parseResponseBody(bufferingResponse, requestUrl, contentType, settings);
    }
}
