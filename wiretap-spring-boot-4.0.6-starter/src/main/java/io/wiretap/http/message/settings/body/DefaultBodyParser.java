package io.wiretap.http.message.settings.body;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.StringNode;
import org.apache.commons.lang3.function.TriFunction;
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
import java.util.function.Function;

import static org.springframework.util.FileCopyUtils.copyToByteArray;
import static io.wiretap.util.HttpBodyUtils.isSupportedContentType;

public class DefaultBodyParser implements BodyParser {
    private final ObjectMapper objectMapper = tools.jackson.databind.json.JsonMapper.builder().build();
    @Nullable
    private final HttpBodyMaskingHandler maskingHandler;
    private final List<HttpBodyMasker> bodyMaskers;

    public DefaultBodyParser(@Nullable HttpBodyMaskingHandler maskingHandler) {
        this(maskingHandler, Collections.emptyList());
    }

    public DefaultBodyParser(@Nullable HttpBodyMaskingHandler maskingHandler,
                             List<HttpBodyMasker> bodyMaskers) {
        this.maskingHandler = maskingHandler;
        this.bodyMaskers = bodyMaskers == null ? Collections.emptyList() : bodyMaskers;
    }
    private static final String NOT_SUPPORTED_TYPE = "Logging of content type %s is not supported";
    private static final String LIMIT_EXCEEDED = "body exceeds the configured limit of %d characters";


    @Override
    public final JsonNode parseRequestBody(final String body, final String requestUrl, final MediaType contentType, HttpBodySettings settings) {
        return parseBody(body, requestUrl, contentType, this::beforeParseRequestBody, this::afterParseRequestBody, settings);
    }

    @Override
    public final JsonNode parseResponseBody(final String body, final String requestUrl, final MediaType contentType, HttpBodySettings settings) {
        return parseBody(body, requestUrl, contentType, this::beforeParseResponseBody, this::afterParseResponseBody, settings);
    }

    @Override
    public JsonNode parseResponseBody(ClientHttpResponse bufferingResponse, String requestUrl, MediaType contentType, HttpBodySettings settings) throws IOException {
        return parseBody(bufferingResponse, requestUrl, contentType, this::beforeParseResponseBody, this::afterParseResponseBody, settings);
    }


    private JsonNode parseBody(
            final ClientHttpResponse bufferingResponse,
            final String requestUrl,
            final MediaType contentType,
            final BinaryOperator<String> beforeParsingCallBack,
            final TriFunction<JsonNode, String, HttpBodySettings, JsonNode> afterParsingCallBack,
            final HttpBodySettings bodySettings
    ) throws IOException {
        if (!isSupportedContentType(contentType)) {
            return StringNode.valueOf(String.format(NOT_SUPPORTED_TYPE, contentType));
        }

        final String body = new String(
                copyToByteArray(bufferingResponse.getBody()),
                StandardCharsets.UTF_8
        );

        final JsonNode processedBody = afterParsingCallBack.apply(
                this.processBodyString(beforeParsingCallBack.apply(body, requestUrl), contentType, bodySettings),
                requestUrl,
                bodySettings
        );

        if (processedBody != null &&
                processedBody.toString().length() > bodySettings.getMaxBodyLength()) {
            return StringNode.valueOf(String.format(LIMIT_EXCEEDED, bodySettings.getMaxBodyLength()));
        }

        return processedBody;
    }
    private JsonNode parseBody(
            final String body,
            final String requestUrl,
            final MediaType contentType,
            final BinaryOperator<String> beforeParsingCallBack,
            final TriFunction<JsonNode, String, HttpBodySettings, JsonNode> afterParsingCallBack,
            final HttpBodySettings httpBodySettings
    ) {
        if (!isSupportedContentType(contentType)) {
            return StringNode.valueOf(String.format(NOT_SUPPORTED_TYPE, contentType));
        }

        final JsonNode processedBody = afterParsingCallBack.apply(
                this.processBodyString(beforeParsingCallBack.apply(body, requestUrl), contentType, httpBodySettings),
                requestUrl,
                httpBodySettings
        );

        if (processedBody != null &&
                processedBody.toString().length() > httpBodySettings.getMaxBodyLength()) {
            return StringNode.valueOf(String.format(LIMIT_EXCEEDED, httpBodySettings.getMaxBodyLength()));
        }

        return processedBody;
    }
    private JsonNode processBodyString(final String bodyString, final MediaType contentType, final HttpBodySettings httpBodySettings) {
        if (bodyString == null || bodyString.isEmpty()) {
            return null;
        }
        final boolean isTruncated = httpBodySettings.isEnableBodyTruncating();
        if (MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
            return tryJson(bodyString)
                    .map(jsonNode -> isTruncated ?
                            HttpBodyUtils.truncateAllHugeFieldsInJson(jsonNode, httpBodySettings.getMaxFieldLength()) : jsonNode
                    ).orElse(StringNode.valueOf(bodyString));
        } else {
            return StringNode.valueOf(bodyString);
        }
    }

    private Optional<JsonNode> tryJson(final String bodyString) {
        try {
            return Optional.of(objectMapper.readTree(bodyString));

        } catch (JacksonException e) {
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
    protected JsonNode afterParseRequestBody(final JsonNode body, final String requestUrl, HttpBodySettings settings) {
        return applyMasking(body, requestUrl, settings);
    }

    /**
     * Post-processing hook for outbound response bodies. By the time it runs the body
     * has already been parsed and (optionally) truncated, which makes this the
     * right place to apply masking for performance reasons.
     */
    protected JsonNode afterParseResponseBody(final JsonNode body, final String requestUrl, HttpBodySettings settings) {
        return applyMasking(body, requestUrl, settings);
    }

    private JsonNode applyMasking(final JsonNode body, final String requestUrl, HttpBodySettings settings) {
        if (body == null || !settings.isEnableBodyMasking()) {
            return body;
        }
        JsonNode masked = body;
        for (HttpBodyMasker m : bodyMaskers) {
            if (m.appliesTo(requestUrl)) {
                masked = m.mask(masked);
                break;
            }
        }
        if (maskingHandler != null) {
            masked = HttpBodyUtils.maskFieldsInBody(masked, maskingHandler::maskBodyField);
        }
        return masked;
    }
}