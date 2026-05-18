package io.wiretap.kafka.consumer;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;

/**
 * Extracts {@code traceId} / {@code spanId} from Kafka record headers so that
 * the wiretap consumer interceptor can restore the MDC context before
 * emitting the {@code kafka_info} log line. The interceptor runs inside
 * Kafka's poll-loop — that is, <em>before</em> Spring Kafka observability
 * wraps the listener method in a Micrometer observation — so MDC has to
 * be populated manually for this log line to carry the trace.
 *
 * <p>Two header formats are recognised:
 *
 * <ul>
 *   <li><b>B3 single header</b> {@code b3} — the default for
 *       {@code micrometer-tracing-bridge-brave}.
 *       Value layout: {@code <traceId>-<spanId>[-<sampled>[-<parentSpanId>]]}.</li>
 *   <li><b>W3C Trace Context</b> {@code traceparent} — the default for
 *       {@code micrometer-tracing-bridge-otel} and the direction Spring is
 *       moving toward.
 *       Value layout: {@code <version>-<traceId>-<spanId>-<flags>}.</li>
 * </ul>
 *
 * No external dependency on Brave or OTel runtimes — both formats are
 * trivial to parse.
 */
final class TraceContextExtractor {

    private TraceContextExtractor() {
    }

    @Nullable
    static TraceContext extract(@Nullable Headers headers) {
        if (headers == null) return null;
        TraceContext fromB3 = parseB3(stringValue(headers.lastHeader("b3")));
        if (fromB3 != null) return fromB3;
        return parseTraceparent(stringValue(headers.lastHeader("traceparent")));
    }

    @Nullable
    private static String stringValue(@Nullable Header header) {
        if (header == null || header.value() == null) return null;
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    @Nullable
    private static TraceContext parseB3(@Nullable String raw) {
        if (raw == null || raw.isEmpty() || "0".equals(raw)) return null;
        String[] parts = raw.split("-");
        if (parts.length < 2) return null;
        String traceId = parts[0];
        String spanId = parts[1];
        return traceId.isEmpty() || spanId.isEmpty() ? null : new TraceContext(traceId, spanId);
    }

    @Nullable
    private static TraceContext parseTraceparent(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String[] parts = raw.split("-");
        if (parts.length < 4) return null;
        String traceId = parts[1];
        String spanId = parts[2];
        return traceId.isEmpty() || spanId.isEmpty() ? null : new TraceContext(traceId, spanId);
    }

    record TraceContext(String traceId, String spanId) {
    }
}
