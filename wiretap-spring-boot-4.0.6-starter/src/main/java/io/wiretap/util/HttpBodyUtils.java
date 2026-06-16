package io.wiretap.util;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.MediaType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Helpers for trimming oversized fields in JSON payloads and for plain-string
 * truncation. Used while processing HTTP bodies to keep image / PDF / other
 * binary content (typically base64-encoded inside JSON) out of the logs.
 */
@Slf4j
public final class HttpBodyUtils {

    private static final String REPLACE_MESSAGE = "...truncated...";

    private HttpBodyUtils() {
    }

    public static String replace(final String bodyString, final int maxStringLength) {
        return bodyString.length() > maxStringLength ? REPLACE_MESSAGE : bodyString;
    }

    public static JsonNode truncateAllHugeFieldsInJson(final JsonNode root, final int maxStringLength) {
        truncateProcess(root, maxStringLength);
        return root;
    }

    private static void truncateProcess(final JsonNode node, final int maxStringLength) {
        if (node.isObject()) {
            final ObjectNode object = (ObjectNode) node;
            for (final Map.Entry<String, JsonNode> field : object.properties()) {
                final JsonNode nodeToCheck = field.getValue();
                if (nodeToCheck.isContainer()) {
                    truncateProcess(nodeToCheck, maxStringLength);
                } else if (replaceCondition(nodeToCheck, maxStringLength)) {
                    object.set(field.getKey(), StringNode.valueOf(REPLACE_MESSAGE));
                }
            }
        } else if (node.isArray()) {
            final ArrayNode array = (ArrayNode) node;
            for (final JsonNode element : array.values()) {
                truncateProcess(element, maxStringLength);
            }
        }
    }

    private static boolean replaceCondition(final JsonNode node, final int maxStringLength) {
        final int stringLength = node.asString().length();
        return stringLength > maxStringLength;
    }

    /**
     * Walks a JSON tree and applies {@code maskFunction} to every textual leaf.
     *
     * @param root         JSON tree to mask
     * @param maskFunction string-level masking function
     * @return the same tree with all leaves masked
     */
    public static JsonNode maskFieldsInBody(final JsonNode root, Function<String, String> maskFunction) {
        if (root != null) {
            if (root.isString()) {
                return StringNode.valueOf(maskFunction.apply(root.asString()));
            }
            maskProcess(root, maskFunction);
        }

        return root;
    }

    private static void maskProcess(final JsonNode node, Function<String, String> maskFunction) {
        if (node.isObject()) {
            final ObjectNode object = (ObjectNode) node;
            for (final Map.Entry<String, JsonNode> field : object.properties()) {
                final JsonNode nodeToCheck = field.getValue();
                if (nodeToCheck.isContainer()) {
                    maskProcess(nodeToCheck, maskFunction);
                } else {
                    final String maskedValue = maskFunction.apply(nodeToCheck.asString());
                    object.set(field.getKey(), StringNode.valueOf(maskedValue));
                }
            }
        } else if (node.isArray()) {
            final ArrayNode array = (ArrayNode) node;
            for (final JsonNode element : array.values()) {
                maskProcess(element, maskFunction);
            }
        }
    }

    public static String getXmlRequestType(@Nullable String xmlBody) throws ParserConfigurationException, IOException, SAXException {
        if (xmlBody == null) {
            return null;
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(
                new ByteArrayInputStream(xmlBody.getBytes(StandardCharsets.UTF_8))
        );

        Element rootElement = document.getDocumentElement();
        return rootElement.getAttribute("xsi:type");
    }

    public static String getStringBody(JsonNode body) throws JacksonException {
        return JsonBodyUtils.getStringBody(body);
    }

    public static boolean isJsonBody(@NotNull JsonNode body) {
        return JsonBodyUtils.isJsonBody(body);
    }

    public static boolean isXmlBody(MediaType contentType) {
        return contentType != null &&
                (contentType.toString().contains(MediaType.TEXT_XML_VALUE) ||
                        contentType.toString().contains(MediaType.APPLICATION_XML_VALUE));
    }

    /**
     * Whether a body of this content type is worth <em>parsing</em> into the log.
     * Guards the parse layer for any captured body string — inbound request and
     * response bodies as well as the outgoing-client buffered paths. This is a
     * different concern from {@link #shouldBypassTeeBuffering}, which decides
     * whether the inbound request stream is buffered at all; do not merge them
     * (this one must keep rejecting e.g. binary response bodies and form-urlencoded).
     */
    public static boolean isSupportedContentType(final MediaType contentType) {
        if (contentType == null) {
            return true;
        }

        boolean isSupported = true;
        final List<MediaType> unsupportedTypes = Arrays.asList(
                MediaType.MULTIPART_FORM_DATA,
                MediaType.APPLICATION_FORM_URLENCODED,
                MediaType.APPLICATION_PDF,
                MediaType.APPLICATION_OCTET_STREAM,
                MediaType.IMAGE_GIF,
                MediaType.IMAGE_JPEG,
                MediaType.IMAGE_PNG,
                MediaType.TEXT_EVENT_STREAM,
                new MediaType("binary", "octet-stream")
        );
        for (MediaType unsupportedType : unsupportedTypes) {
            final boolean isInclude = contentType.includes(unsupportedType);
            if (isInclude) {
                isSupported = false;
                break;
            }
        }
        return isSupported;
    }

    /**
     * Whether logback-access teeing (inbound request-body buffering) must be skipped
     * for this request content type. Buffering a {@code multipart/*} stream drains it
     * so {@code request.getParts()} / {@code @RequestPart} see nothing and uploads
     * break; buffering large binary / streaming uploads is also pointless. Note this
     * is a narrower, buffering-time concern than {@link #isSupportedContentType}:
     * {@code application/x-www-form-urlencoded} is intentionally NOT bypassed here —
     * logback-access already avoids draining its parameters, and its response stays
     * loggable.
     */
    public static boolean shouldBypassTeeBuffering(final MediaType contentType) {
        if (contentType == null) {
            return false;
        }
        final String type = contentType.getType();
        if ("multipart".equalsIgnoreCase(type) || "image".equalsIgnoreCase(type)) {
            return true;
        }
        final List<MediaType> bypassTypes = Arrays.asList(
                MediaType.APPLICATION_OCTET_STREAM,
                MediaType.APPLICATION_PDF,
                MediaType.TEXT_EVENT_STREAM,
                new MediaType("binary", "octet-stream")
        );
        for (MediaType bypassType : bypassTypes) {
            if (bypassType.includes(contentType)) {
                return true;
            }
        }
        return false;
    }
}
