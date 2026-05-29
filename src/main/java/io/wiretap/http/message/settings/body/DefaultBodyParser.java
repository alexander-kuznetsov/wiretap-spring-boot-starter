package io.wiretap.http.message.settings.body;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import io.wiretap.metrics.BodyMetricsContext;
import io.wiretap.metrics.NoOpWiretapMetrics;
import io.wiretap.metrics.WiretapMetrics;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import io.wiretap.util.HttpBodyUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BinaryOperator;

import static org.springframework.util.FileCopyUtils.copyToByteArray;
import static io.wiretap.util.HttpBodyUtils.isSupportedContentType;

public class DefaultBodyParser implements BodyParser {
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Nullable
    private final HttpBodyFieldMaskingHandler fieldMaskingHandler;
    private final List<HttpBodyMaskingHandler> bodyMaskingHandlers;
    private final WiretapMetrics metrics;

    public DefaultBodyParser(@Nullable HttpBodyFieldMaskingHandler fieldMaskingHandler) {
        this(fieldMaskingHandler, Collections.emptyList(), new NoOpWiretapMetrics());
    }

    public DefaultBodyParser(@Nullable HttpBodyFieldMaskingHandler fieldMaskingHandler,
                             List<HttpBodyMaskingHandler> bodyMaskingHandlers) {
        this(fieldMaskingHandler, bodyMaskingHandlers, new NoOpWiretapMetrics());
    }

    public DefaultBodyParser(@Nullable HttpBodyFieldMaskingHandler fieldMaskingHandler,
                             List<HttpBodyMaskingHandler> bodyMaskingHandlers,
                             WiretapMetrics metrics) {
        this.fieldMaskingHandler = fieldMaskingHandler;
        this.bodyMaskingHandlers = bodyMaskingHandlers == null ? Collections.emptyList() : bodyMaskingHandlers;
        this.metrics = metrics == null ? new NoOpWiretapMetrics() : metrics;
    }
    private static final String NOT_SUPPORTED_TYPE = "Logging of content type %s is not supported";
    private static final String LIMIT_EXCEEDED = "body exceeds the configured limit of %d characters";

    /**
     * Post-processing step applied after parse/truncate. Mirrors the legacy
     * before/after hook pair but additionally carries the {@link BodyMetricsContext}
     * so masking phase timers can be tagged with the originating direction/client.
     */
    @FunctionalInterface
    private interface BodyPostProcessor {
        JsonNode apply(JsonNode body, String requestUrl, HttpBodySettings settings, BodyMetricsContext metricsContext);
    }

    @Override
    public final JsonNode parseRequestBody(final String body, final String requestUrl, final MediaType contentType, HttpBodySettings settings) {
        return parseBody(body, requestUrl, contentType, this::beforeParseRequestBody, this::afterParseRequestBody, settings, BodyMetricsContext.NONE);
    }

    @Override
    public final JsonNode parseResponseBody(final String body, final String requestUrl, final MediaType contentType, HttpBodySettings settings) {
        return parseBody(body, requestUrl, contentType, this::beforeParseResponseBody, this::afterParseResponseBody, settings, BodyMetricsContext.NONE);
    }

    @Override
    public JsonNode parseResponseBody(ClientHttpResponse bufferingResponse, String requestUrl, MediaType contentType, HttpBodySettings settings) throws IOException {
        return parseBody(bufferingResponse, requestUrl, contentType, this::beforeParseResponseBody, this::afterParseResponseBody, settings, BodyMetricsContext.NONE);
    }

    @Override
    public JsonNode parseRequestBody(String body, String requestUrl, MediaType contentType, HttpBodySettings settings, BodyMetricsContext metricsContext) {
        return parseBody(body, requestUrl, contentType, this::beforeParseRequestBody, this::afterParseRequestBody, settings,
                metricsContext == null ? BodyMetricsContext.NONE : metricsContext);
    }

    @Override
    public JsonNode parseResponseBody(String body, String requestUrl, MediaType contentType, HttpBodySettings settings, BodyMetricsContext metricsContext) {
        return parseBody(body, requestUrl, contentType, this::beforeParseResponseBody, this::afterParseResponseBody, settings,
                metricsContext == null ? BodyMetricsContext.NONE : metricsContext);
    }

    @Override
    public JsonNode parseResponseBody(ClientHttpResponse bufferingResponse, String requestUrl, MediaType contentType, HttpBodySettings settings, BodyMetricsContext metricsContext) throws IOException {
        return parseBody(bufferingResponse, requestUrl, contentType, this::beforeParseResponseBody, this::afterParseResponseBody, settings,
                metricsContext == null ? BodyMetricsContext.NONE : metricsContext);
    }


    private JsonNode parseBody(
            final ClientHttpResponse bufferingResponse,
            final String requestUrl,
            final MediaType contentType,
            final BinaryOperator<String> beforeParsingCallBack,
            final BodyPostProcessor afterParsingCallBack,
            final HttpBodySettings bodySettings,
            final BodyMetricsContext metricsContext
    ) throws IOException {
        if (!isSupportedContentType(contentType)) {
            return new TextNode(String.format(NOT_SUPPORTED_TYPE, contentType));
        }

        final String body = new String(
                copyToByteArray(bufferingResponse.getBody()),
                StandardCharsets.UTF_8
        );

        final JsonNode processedBody = afterParsingCallBack.apply(
                this.processBodyString(beforeParsingCallBack.apply(body, requestUrl), contentType, bodySettings, metricsContext),
                requestUrl,
                bodySettings,
                metricsContext
        );

        if (processedBody != null &&
                processedBody.toString().length() > bodySettings.getMaxBodyLength()) {
            return new TextNode(String.format(LIMIT_EXCEEDED, bodySettings.getMaxBodyLength()));
        }

        return processedBody;
    }
    private JsonNode parseBody(
            final String body,
            final String requestUrl,
            final MediaType contentType,
            final BinaryOperator<String> beforeParsingCallBack,
            final BodyPostProcessor afterParsingCallBack,
            final HttpBodySettings httpBodySettings,
            final BodyMetricsContext metricsContext
    ) {
        if (!isSupportedContentType(contentType)) {
            return new TextNode(String.format(NOT_SUPPORTED_TYPE, contentType));
        }

        final JsonNode processedBody = afterParsingCallBack.apply(
                this.processBodyString(beforeParsingCallBack.apply(body, requestUrl), contentType, httpBodySettings, metricsContext),
                requestUrl,
                httpBodySettings,
                metricsContext
        );

        if (processedBody != null &&
                processedBody.toString().length() > httpBodySettings.getMaxBodyLength()) {
            return new TextNode(String.format(LIMIT_EXCEEDED, httpBodySettings.getMaxBodyLength()));
        }

        return processedBody;
    }
    private JsonNode processBodyString(final String bodyString, final MediaType contentType, final HttpBodySettings httpBodySettings, final BodyMetricsContext metricsContext) {
        if (bodyString == null || bodyString.isEmpty()) {
            return null;
        }
        final boolean isTruncated = httpBodySettings.isEnableBodyTruncating();
        if (MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            return tryJson(bodyString, metricsContext)
                    .map(jsonNode -> {
                        if (!isTruncated) {
                            return jsonNode;
                        }
                        long truncStart = metrics.startSample();
                        JsonNode truncated = HttpBodyUtils.truncateAllHugeFieldsInJson(jsonNode, httpBodySettings.getMaxFieldLength());
                        metrics.recordPhase(truncStart, metricsContext, "truncate");
                        return truncated;
                    }).orElse(new TextNode(bodyString));
        } else {
            return new TextNode(bodyString);
        }
    }

    private Optional<JsonNode> tryJson(final String bodyString, final BodyMetricsContext metricsContext) {
        long parseStart = metrics.startSample();
        try {
            JsonNode node = objectMapper.readTree(bodyString);
            metrics.recordPhase(parseStart, metricsContext, "parse");
            return Optional.of(node);

        } catch (IOException e) {
            metrics.recordPhase(parseStart, metricsContext, "parse");
            return Optional.empty();
        }
    }

    protected String beforeParseRequestBody(final String body, final String requestUrl) {
        return body;
    }

    protected String beforeParseResponseBody(final String body, final String requestUrl) {
        return body;
    }

    /**
     * Post-processing hook for inbound request bodies. By the time it runs the body
     * has already been parsed and (optionally) truncated, which makes this the
     * right place to apply masking for performance reasons.
     */
    protected JsonNode afterParseRequestBody(final JsonNode body, final String requestUrl, HttpBodySettings settings, BodyMetricsContext metricsContext) {
        return applyMasking(body, requestUrl, settings, metricsContext);
    }

    /**
     * Post-processing hook for outbound response bodies. By the time it runs the body
     * has already been parsed and (optionally) truncated, which makes this the
     * right place to apply masking for performance reasons.
     */
    protected JsonNode afterParseResponseBody(final JsonNode body, final String requestUrl, HttpBodySettings settings, BodyMetricsContext metricsContext) {
        return applyMasking(body, requestUrl, settings, metricsContext);
    }

    private JsonNode applyMasking(final JsonNode body, final String requestUrl, HttpBodySettings settings, final BodyMetricsContext metricsContext) {
        if (body == null || !settings.isEnableBodyMasking()) {
            return body;
        }
        long maskStart = metrics.startSample();
        JsonNode masked = body;
        for (HttpBodyMaskingHandler h : bodyMaskingHandlers) {
            if (h.appliesTo(requestUrl)) {
                long maskerStart = metrics.startSample();
                masked = h.mask(masked);
                metrics.recordBodyMaskerInvocation(maskerStart, h.getClass().getName(), metricsContext.direction());
                break;
            }
        }
        if (fieldMaskingHandler != null) {
            masked = HttpBodyUtils.maskFieldsInBody(masked, fieldMaskingHandler::maskBodyField);
        }
        metrics.recordPhase(maskStart, metricsContext, "mask");
        return masked;
    }
}
