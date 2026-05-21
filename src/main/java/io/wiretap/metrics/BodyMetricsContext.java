package io.wiretap.metrics;

import org.springframework.http.MediaType;

/**
 * Small DTO carrying the {@code direction} / {@code client} /
 * {@code content_type_class} tags down into the body-processing path so that
 * {@link WiretapMetrics#recordPhase} calls inside
 * {@code DefaultBodyParser} / {@code HttpBodyUtils} can be properly attributed
 * to the originating HTTP client without resorting to {@code ThreadLocal}
 * (which doesn't survive Reactor async boundaries).
 *
 * <p>Construct one per request in the interceptor where the direction and
 * client are known, and pass it explicitly into the body-processing API.
 *
 * @param direction         either {@code "incoming"} or {@code "outgoing"}
 * @param client            servlet / webclient / restclient / resttemplate /
 *                          feign / webservicetemplate
 * @param contentTypeClass  json / xml / text / binary / other — bounded set,
 *                          built from the raw {@code Content-Type} via
 *                          {@link #classify(MediaType)}
 */
public record BodyMetricsContext(String direction, String client, String contentTypeClass) {

    public static final BodyMetricsContext NONE = new BodyMetricsContext("unknown", "unknown", "other");

    public static String classify(MediaType mediaType) {
        if (mediaType == null) return "other";
        if (mediaType.includes(MediaType.APPLICATION_JSON)
                || "json".equalsIgnoreCase(mediaType.getSubtype())) {
            return "json";
        }
        if (mediaType.includes(MediaType.APPLICATION_XML)
                || mediaType.includes(MediaType.TEXT_XML)
                || "xml".equalsIgnoreCase(mediaType.getSubtype())
                || mediaType.getSubtype().endsWith("+xml")) {
            return "xml";
        }
        if ("text".equalsIgnoreCase(mediaType.getType())) {
            return "text";
        }
        if (MediaType.APPLICATION_OCTET_STREAM.includes(mediaType)) {
            return "binary";
        }
        return "other";
    }
}
