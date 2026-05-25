package io.wiretap.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import org.jetbrains.annotations.NotNull;

/**
 * Domain-agnostic helpers for working with parsed JSON payloads.
 *
 * <p>Used by both HTTP and Kafka logging pipelines to decide whether a parsed
 * tree should be rendered as a pretty-printed JSON string (object / array /
 * POJO) or treated as a plain scalar.
 */
public final class JsonBodyUtils {

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private JsonBodyUtils() {
    }

    /**
     * @return {@code true} when {@code body} is a JSON container — object,
     *         array or POJO. Scalars (text, number, boolean, null, binary,
     *         missing) return {@code false} so callers can decide to log them
     *         verbatim instead of running them through a pretty-printer.
     */
    public static boolean isJsonBody(@NotNull JsonNode body) {
        return JsonNodeType.OBJECT == body.getNodeType()
                || JsonNodeType.POJO == body.getNodeType()
                || JsonNodeType.ARRAY == body.getNodeType();
    }

    /**
     * Renders {@code body} as a pretty-printed multi-line JSON string when it
     * is a container (see {@link #isJsonBody}), or returns {@code body.toString()}
     * for scalars. The {@code \n} characters in the result are what makes log
     * aggregators (Kibana / Splunk / Grafana Loki) display the payload as a
     * formatted block instead of a collapsed one-liner.
     */
    public static String getStringBody(JsonNode body) throws JsonProcessingException {
        if (body == null) {
            return null;
        }
        return isJsonBody(body)
                ? OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(body)
                : body.toString();
    }
}
