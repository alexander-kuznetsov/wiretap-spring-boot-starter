package io.wiretap.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
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
import java.util.Iterator;
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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

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
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();

            while (fields.hasNext()) {

                final Map.Entry<String, JsonNode> field = fields.next();
                final JsonNode nodeToCheck = field.getValue();

                if (nodeToCheck.isContainerNode()) {
                    truncateProcess(nodeToCheck, maxStringLength);
                } else if (replaceCondition(nodeToCheck, maxStringLength)) {
                    field.setValue(new TextNode(REPLACE_MESSAGE));
                }
            }
        } else if (node.isArray()) {
            final ArrayNode array = (ArrayNode) node;
            array.elements().forEachRemaining(element -> truncateProcess(element, maxStringLength));
        }
    }

    private static boolean replaceCondition(final JsonNode node, final int maxStringLength) {
        final int stringLength = node.asText().length();
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
            if (root.isTextual()) {
                return new TextNode(maskFunction.apply(root.textValue()));
            }
            maskProcess(root, maskFunction);
        }

        return root;
    }

    private static void maskProcess(final JsonNode node, Function<String, String> maskFunction) {
        if (node.isObject()) {

            final ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();

            while (fields.hasNext()) {

                final Map.Entry<String, JsonNode> field = fields.next();
                final JsonNode nodeToCheck = field.getValue();

                if (nodeToCheck.isContainerNode()) {
                    maskProcess(nodeToCheck, maskFunction);
                } else {
                    final String maskedValue = maskFunction.apply(field.getValue().asText());
                    field.setValue(new TextNode(maskedValue));
                }
            }
        } else if (node.isArray()) {
            final ArrayNode array = (ArrayNode) node;
            array.elements().forEachRemaining(element -> maskProcess(element, maskFunction));
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

    public static String getStringBody(JsonNode body) throws JsonProcessingException {
        if (body != null) {
            return isJsonBody(body) ?
                    OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(body) :
                    body.toString();
        }
        return null;
    }

    public static boolean isJsonBody(@NotNull JsonNode body) {
        return (JsonNodeType.OBJECT == body.getNodeType() ||
                JsonNodeType.POJO == body.getNodeType() ||
                JsonNodeType.ARRAY == body.getNodeType());
    }

    public static boolean isXmlBody(MediaType contentType) {
        return contentType != null &&
                (contentType.toString().contains(MediaType.TEXT_XML_VALUE) ||
                        contentType.toString().contains(MediaType.APPLICATION_XML_VALUE));
    }

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
}