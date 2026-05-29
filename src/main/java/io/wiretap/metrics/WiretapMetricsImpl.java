package io.wiretap.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Active {@link WiretapMetrics} implementation that backs every recording
 * method with the supplied Micrometer {@link MeterRegistry}. Loaded only when
 * {@code io.micrometer.core.instrument.MeterRegistry} is on the classpath
 * (see {@code WiretapMetricsConfiguration}).
 *
 * <p>Meter registrations are idempotent — Micrometer caches by name + tag set,
 * so {@code Timer.builder(...).register(registry)} called per request is
 * effectively a hash lookup.
 */
public final class WiretapMetricsImpl implements WiretapMetrics {

    private static final String M_HTTP_OVERHEAD = "wiretap.http.overhead";
    private static final String M_HTTP_REQUESTS = "wiretap.http.requests";
    private static final String M_HTTP_SKIPPED = "wiretap.http.skipped";
    private static final String M_HTTP_BODY_SIZE = "wiretap.http.body.size";
    private static final String M_HTTP_BODY_FAIL = "wiretap.http.body.capture.failures";
    private static final String M_KAFKA_OVERHEAD = "wiretap.kafka.overhead";
    private static final String M_KAFKA_MESSAGES = "wiretap.kafka.messages";
    private static final String M_KAFKA_SKIPPED = "wiretap.kafka.skipped";
    private static final String M_KAFKA_MSG_SIZE = "wiretap.kafka.message.size";
    private static final String M_KAFKA_BODY_FAIL = "wiretap.kafka.body.capture.failures";
    private static final String M_BODY_PHASE = "wiretap.body.phase";
    private static final String M_JSON_SERIALIZATION = "wiretap.json.serialization";
    private static final String M_BODY_MASKER = "wiretap.body.masker.invocation";

    private final MeterRegistry registry;
    private final WiretapMetricsProperties props;

    public WiretapMetricsImpl(MeterRegistry registry, WiretapMetricsProperties props) {
        this.registry = registry;
        this.props = props;
    }

    @Override
    public boolean isDetailedTimingsEnabled() {
        return props.isDetailedTimings();
    }

    // ----- HTTP -------------------------------------------------------------

    @Override
    public void recordHttpRequest(long startNanos, long downstreamNanos, String direction, String client, String outcome, String status) {
        Tags tags = Tags.of(
                Tag.of("direction", direction),
                Tag.of("client", client),
                Tag.of("outcome", outcome));
        if (props.getTags().isStatus()) {
            tags = tags.and("status", status);
        }
        // wiretap-attributable overhead only: subtract the downstream call time
        // (nanoseconds on both sides — no millisecond quantisation)
        long overheadNanos = elapsed(startNanos) - downstreamNanos;
        if (overheadNanos < 0L) {
            overheadNanos = 0L;
        }
        httpTimer(M_HTTP_OVERHEAD, tags).record(overheadNanos, TimeUnit.NANOSECONDS);
        Counter.builder(M_HTTP_REQUESTS).tags(tags).register(registry).increment();
    }

    @Override
    public void recordHttpSkipped(String direction, String client, String reason) {
        Counter.builder(M_HTTP_SKIPPED)
                .tags("direction", direction, "client", client, "reason", reason)
                .register(registry)
                .increment();
    }

    @Override
    public void recordHttpBodySize(String direction, String client, String contentTypeClass, String kind, long bytes) {
        if (bytes < 0) return;
        DistributionSummary.builder(M_HTTP_BODY_SIZE)
                .baseUnit("bytes")
                .tags("direction", direction, "client", client,
                        "content_type_class", contentTypeClass, "kind", kind)
                .publishPercentileHistogram(props.isHistograms())
                .register(registry)
                .record(bytes);
    }

    @Override
    public void recordHttpBodyCaptureFailure(String direction, String client, String phase) {
        Counter.builder(M_HTTP_BODY_FAIL)
                .tags("direction", direction, "client", client, "phase", phase)
                .register(registry)
                .increment();
    }

    // ----- Kafka ------------------------------------------------------------

    @Override
    public void recordKafkaMessage(long startNanos, String direction, String outcome, String topic) {
        Tags tags = Tags.of(
                Tag.of("direction", direction),
                Tag.of("outcome", outcome));
        if (props.getTags().isTopic() && topic != null) {
            tags = tags.and("topic", topic);
        }
        kafkaTimer(M_KAFKA_OVERHEAD, tags).record(elapsed(startNanos), TimeUnit.NANOSECONDS);
        Counter.builder(M_KAFKA_MESSAGES).tags(tags).register(registry).increment();
    }

    @Override
    public void recordKafkaSkipped(String direction, String reason) {
        Counter.builder(M_KAFKA_SKIPPED)
                .tags("direction", direction, "reason", reason)
                .register(registry)
                .increment();
    }

    @Override
    public void recordKafkaMessageSize(String direction, long bytes, String topic) {
        if (bytes < 0) return;
        Tags tags = Tags.of(Tag.of("direction", direction));
        if (props.getTags().isTopic() && topic != null) {
            tags = tags.and("topic", topic);
        }
        DistributionSummary.builder(M_KAFKA_MSG_SIZE)
                .baseUnit("bytes")
                .tags(tags)
                .publishPercentileHistogram(props.isHistograms())
                .register(registry)
                .record(bytes);
    }

    @Override
    public void recordKafkaBodyCaptureFailure(String direction, String phase) {
        Counter.builder(M_KAFKA_BODY_FAIL)
                .tags("direction", direction, "phase", phase)
                .register(registry)
                .increment();
    }

    // ----- Phase-level ------------------------------------------------------

    @Override
    public void recordPhase(long startNanos, BodyMetricsContext context, String phase) {
        if (!props.isDetailedTimings()) return;
        BodyMetricsContext ctx = context == null ? BodyMetricsContext.NONE : context;
        timer(M_BODY_PHASE, Tags.of(
                Tag.of("phase", phase),
                Tag.of("direction", ctx.direction()),
                Tag.of("client", ctx.client()),
                Tag.of("content_type_class", ctx.contentTypeClass())))
                .record(elapsed(startNanos), TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordJsonSerialization(long startNanos, String sink, String direction, String client) {
        if (!props.isDetailedTimings()) return;
        timer(M_JSON_SERIALIZATION, Tags.of(
                Tag.of("sink", sink),
                Tag.of("direction", direction),
                Tag.of("client", client)))
                .record(elapsed(startNanos), TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordBodyMaskerInvocation(long startNanos, String maskerClass, String direction) {
        if (!props.isDetailedTimings()) return;
        timer(M_BODY_MASKER, Tags.of(
                Tag.of("masker_class", maskerClass),
                Tag.of("direction", direction)))
                .record(elapsed(startNanos), TimeUnit.NANOSECONDS);
    }

    // ----- Helpers ----------------------------------------------------------

    private Timer httpTimer(String name, Tags tags) {
        return Timer.builder(name)
                .tags(tags)
                .publishPercentileHistogram(props.isHistograms())
                .publishPercentiles(props.isHistograms() ? new double[]{0.5, 0.95, 0.99} : new double[0])
                .minimumExpectedValue(Duration.ofNanos(10_000L))
                .maximumExpectedValue(Duration.ofSeconds(5))
                .register(registry);
    }

    private Timer kafkaTimer(String name, Tags tags) {
        return Timer.builder(name)
                .tags(tags)
                .publishPercentileHistogram(props.isHistograms())
                .publishPercentiles(props.isHistograms() ? new double[]{0.5, 0.95, 0.99} : new double[0])
                .minimumExpectedValue(Duration.ofNanos(10_000L))
                .maximumExpectedValue(Duration.ofSeconds(5))
                .register(registry);
    }

    private Timer timer(String name, Tags tags) {
        return Timer.builder(name)
                .tags(tags)
                .publishPercentileHistogram(props.isHistograms())
                .register(registry);
    }

    private static long elapsed(long startNanos) {
        long now = System.nanoTime();
        return Math.max(0L, now - startNanos);
    }
}
