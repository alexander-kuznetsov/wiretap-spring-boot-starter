package io.wiretap.metrics;

/**
 * Facade for the Micrometer metrics Wiretap publishes about its own processing
 * overhead. Implemented by {@code WiretapMetricsImpl} (active) and
 * {@link NoOpWiretapMetrics} (fallback).
 *
 * <p>Interceptors and providers depend on this interface only — it carries no
 * Micrometer types in its signatures, so the rest of Wiretap stays loadable
 * even when {@code io.micrometer:micrometer-core} is absent from the runtime
 * classpath. Sample handles are passed as raw {@code long} nanosecond
 * timestamps (i.e. {@code System.nanoTime()}); the active implementation
 * converts them into Micrometer {@code Timer} records, the no-op implementation
 * ignores them.
 *
 * <p>The full metric catalogue is documented in
 * {@code README.md#metrics} and in {@link WiretapMetricsProperties}.
 *
 * <h2>Tag value contract</h2>
 * <ul>
 *   <li>{@code direction}: {@code incoming} / {@code outgoing} (HTTP),
 *       {@code producer} / {@code consumer} (Kafka).</li>
 *   <li>{@code client}: {@code servlet} / {@code webclient} / {@code restclient}
 *       / {@code resttemplate} / {@code feign} / {@code webservicetemplate}.</li>
 *   <li>{@code outcome}: {@code success} / {@code client_error} / {@code server_error}
 *       / {@code exception} (HTTP), {@code success} / {@code error} (Kafka).</li>
 *   <li>{@code status}: {@code 2xx} / {@code 3xx} / {@code 4xx} / {@code 5xx}
 *       / {@code other} / {@code exception} (never the raw status code).</li>
 *   <li>{@code reason}: {@code exclude_pattern} / {@code exclude_topic}
 *       / {@code streaming} / {@code unsupported_content_type}
 *       / {@code visibility_disabled} / {@code null_topic} / {@code null_record}.</li>
 *   <li>{@code phase}: {@code capture} / {@code parse} / {@code mask}
 *       / {@code truncate} / {@code serialize} / {@code emit}.</li>
 *   <li>{@code content_type_class}: {@code json} / {@code xml} / {@code text}
 *       / {@code binary} / {@code other}.</li>
 * </ul>
 */
public interface WiretapMetrics {

    /**
     * Snapshot the monotonic clock to mark the start of a measured region.
     * Cheap wrapper around {@link System#nanoTime()} — the no-op implementation
     * still returns a valid timestamp so callers don't need to branch.
     */
    default long startSample() {
        return System.nanoTime();
    }

    /**
     * Whether {@code wiretap.metrics.detailed-timings} is on. Callers can
     * short-circuit phase-level instrumentation in the body-processing hot
     * path when this returns {@code false}.
     */
    boolean isDetailedTimingsEnabled();

    // ----- HTTP -------------------------------------------------------------

    /** Record {@code wiretap.http.overhead} + {@code wiretap.http.requests} for a completed HTTP request. */
    void recordHttpRequest(long startNanos, String direction, String client, String outcome, String status);

    /** Record {@code wiretap.http.skipped} — a request matched a skip rule and produced no log line. */
    void recordHttpSkipped(String direction, String client, String reason);

    /** Record {@code wiretap.http.body.size} — total bytes of a captured request or response body. */
    void recordHttpBodySize(String direction, String client, String contentTypeClass, String kind, long bytes);

    /** Record {@code wiretap.http.body.capture.failures} — an exception escaped a body-processing phase. */
    void recordHttpBodyCaptureFailure(String direction, String client, String phase);

    // ----- Kafka ------------------------------------------------------------

    /** Record {@code wiretap.kafka.overhead} + {@code wiretap.kafka.messages} for a logged Kafka message. */
    void recordKafkaMessage(long startNanos, String direction, String outcome, String topic);

    /** Record {@code wiretap.kafka.skipped} — a record matched a skip rule and produced no log line. */
    void recordKafkaSkipped(String direction, String reason);

    /** Record {@code wiretap.kafka.message.size} — total bytes of the message value. */
    void recordKafkaMessageSize(String direction, long bytes, String topic);

    // ----- Phase-level (detailed-timings flag) ------------------------------

    /**
     * Record {@code wiretap.body.phase} — time spent in a single body-processing
     * step (parse / mask / truncate / serialize). No-op when detailed-timings
     * is off; the caller can still call it unconditionally.
     */
    void recordPhase(long startNanos, BodyMetricsContext context, String phase);

    /**
     * Record {@code wiretap.json.serialization} — time spent serialising a
     * single MDC payload (HTTP or Kafka info JSON) to a string.
     */
    void recordJsonSerialization(long startNanos, String sink, String direction, String client);

    /** Record {@code wiretap.body.masker.invocation} — time spent inside a single {@code HttpBodyMasker} SPI call. */
    void recordBodyMaskerInvocation(long startNanos, String maskerClass, String direction);
}
