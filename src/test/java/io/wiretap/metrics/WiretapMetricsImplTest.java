package io.wiretap.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tag values and metric names form the public contract — every change here
 * should be reflected in the README "Metrics" section.
 */
class WiretapMetricsImplTest {

    private SimpleMeterRegistry registry;
    private WiretapMetricsProperties properties;
    private WiretapMetricsImpl metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        properties = new WiretapMetricsProperties();
        metrics = new WiretapMetricsImpl(registry, properties);
    }

    @Test
    void recordHttpRequest_emitsTimerAndCounterWithExpectedTags() {
        long start = metrics.startSample();

        metrics.recordHttpRequest(start, 0L, "outgoing", "webclient", "success", "2xx");

        Timer timer = registry.find("wiretap.http.overhead")
                .tags("direction", "outgoing", "client", "webclient", "outcome", "success", "status", "2xx")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);

        Counter counter = registry.find("wiretap.http.requests")
                .tags("direction", "outgoing", "client", "webclient", "outcome", "success", "status", "2xx")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordHttpSkipped_incrementsCounter() {
        metrics.recordHttpSkipped("incoming", "servlet", "exclude_pattern");

        Counter counter = registry.find("wiretap.http.skipped")
                .tags("direction", "incoming", "client", "servlet", "reason", "exclude_pattern")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordHttpBodySize_emitsDistributionSummaryInBytes() {
        metrics.recordHttpBodySize("outgoing", "restclient", "json", "request", 4096L);

        DistributionSummary summary = registry.find("wiretap.http.body.size")
                .tags("direction", "outgoing", "client", "restclient",
                        "content_type_class", "json", "kind", "request")
                .summary();
        assertThat(summary).isNotNull();
        assertThat(summary.count()).isEqualTo(1);
        assertThat(summary.totalAmount()).isEqualTo(4096.0);
        assertThat(summary.getId().getBaseUnit()).isEqualTo("bytes");
    }

    @Test
    void recordHttpBodySize_dropsNegativeValues() {
        metrics.recordHttpBodySize("outgoing", "feign", "json", "response", -1L);

        assertThat(registry.find("wiretap.http.body.size").summary()).isNull();
    }

    @Test
    void recordHttpBodyCaptureFailure_incrementsCounterWithPhaseTag() {
        metrics.recordHttpBodyCaptureFailure("outgoing", "feign", "capture");

        Counter counter = registry.find("wiretap.http.body.capture.failures")
                .tags("direction", "outgoing", "client", "feign", "phase", "capture")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordKafkaBodyCaptureFailure_incrementsCounterWithPhaseTag() {
        metrics.recordKafkaBodyCaptureFailure("consumer", "serialize");

        Counter counter = registry.find("wiretap.kafka.body.capture.failures")
                .tags("direction", "consumer", "phase", "serialize")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordKafkaMessage_dropsTopicTagByDefault() {
        long start = metrics.startSample();
        metrics.recordKafkaMessage(start, "producer", "success", "orders.events");

        Timer timer = registry.find("wiretap.kafka.overhead")
                .tags("direction", "producer", "outcome", "success")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.getId().getTag("topic")).as("topic tag must be off by default").isNull();
    }

    @Test
    void recordKafkaMessage_includesTopicWhenTagEnabled() {
        properties.getTags().setTopic(true);
        long start = metrics.startSample();

        metrics.recordKafkaMessage(start, "consumer", "success", "orders.events");

        Timer timer = registry.find("wiretap.kafka.overhead")
                .tags("direction", "consumer", "outcome", "success", "topic", "orders.events")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void recordHttpRequest_omitsStatusTagWhenDisabled() {
        properties.getTags().setStatus(false);
        long start = metrics.startSample();

        metrics.recordHttpRequest(start, 0L, "outgoing", "webclient", "success", "2xx");

        Timer timer = registry.find("wiretap.http.overhead")
                .tags("direction", "outgoing", "client", "webclient", "outcome", "success")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.getId().getTag("status")).isNull();
    }

    @Test
    void recordHttpRequest_subtractsDownstreamFromOverhead() {
        // total ≈ 100ms (startNanos pushed 100ms into the past); downstream = 30ms
        long start = metrics.startSample() - TimeUnit.MILLISECONDS.toNanos(100);

        metrics.recordHttpRequest(start, TimeUnit.MILLISECONDS.toNanos(30), "outgoing", "webclient", "success", "2xx");

        Timer timer = registry.find("wiretap.http.overhead")
                .tags("direction", "outgoing", "client", "webclient").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        // overhead = total(~100ms) - downstream(30ms) ≈ 70ms: strictly between 0 and total
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(0.0).isLessThan(100.0);
    }

    @Test
    void recordHttpRequest_clampsNegativeOverheadToZero() {
        // downstream larger than total → guard clamps overhead to 0
        long start = metrics.startSample();

        metrics.recordHttpRequest(start, TimeUnit.MILLISECONDS.toNanos(5000), "outgoing", "webclient", "success", "2xx");

        Timer timer = registry.find("wiretap.http.overhead")
                .tags("direction", "outgoing", "client", "webclient").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(0.0);
    }

    @Test
    void recordHttpRequest_overheadIsNanosecondPrecise_notMillisFloored() {
        // total ≈ 10ms; downstream = 9_900_000 ns (9.9ms) → real overhead ≈ 0.1ms.
        // If the downstream were floored to whole milliseconds (9ms) the reported
        // overhead would balloon past 1ms; nanosecond subtraction keeps it sub-ms.
        long start = metrics.startSample() - TimeUnit.MILLISECONDS.toNanos(10);

        metrics.recordHttpRequest(start, 9_900_000L, "outgoing", "feign", "success", "2xx");

        Timer timer = registry.find("wiretap.http.overhead")
                .tags("direction", "outgoing", "client", "feign").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isLessThan(1.0);
    }

    @Test
    void recordPhase_doesNothingUnlessDetailedTimingsEnabled() {
        metrics.recordPhase(metrics.startSample(),
                new BodyMetricsContext("outgoing", "webclient", "json"), "parse");

        assertThat(registry.find("wiretap.body.phase").timer()).isNull();
    }

    @Test
    void recordPhase_emitsTimerWhenDetailedTimingsEnabled() {
        properties.setDetailedTimings(true);

        metrics.recordPhase(metrics.startSample(),
                new BodyMetricsContext("outgoing", "webclient", "json"), "parse");

        Timer timer = registry.find("wiretap.body.phase")
                .tags("phase", "parse", "direction", "outgoing",
                        "client", "webclient", "content_type_class", "json")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void recordJsonSerialization_respectsDetailedTimingsFlag() {
        long start = metrics.startSample();

        metrics.recordJsonSerialization(start, "http", "outgoing", "webclient");
        assertThat(registry.find("wiretap.json.serialization").timer()).isNull();

        properties.setDetailedTimings(true);
        metrics.recordJsonSerialization(start, "http", "outgoing", "webclient");
        Timer timer = registry.find("wiretap.json.serialization")
                .tags("sink", "http", "direction", "outgoing", "client", "webclient")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void histogramsFlag_addsPercentileHistogramToTimers() {
        properties.setHistograms(true);
        metrics.recordHttpRequest(metrics.startSample(), 0L, "outgoing", "webclient", "success", "2xx");

        Timer timer = registry.find("wiretap.http.overhead").timer();
        assertThat(timer).isNotNull();
        List<Meter> meters = registry.getMeters().stream()
                .filter(m -> m.getId().getName().equals("wiretap.http.overhead"))
                .toList();
        assertThat(meters).hasSize(1);
        // SimpleMeterRegistry doesn't itself expose histogram buckets, but we
        // can still assert that the request percentiles snapshot is non-empty
        // when the flag is on.
        assertThat(timer.takeSnapshot().percentileValues()).isNotEmpty();
    }

    @Test
    void noOp_doesNotTouchRegistry() {
        WiretapMetrics noOp = new NoOpWiretapMetrics();

        noOp.recordHttpRequest(noOp.startSample(), 0L, "outgoing", "webclient", "success", "2xx");
        noOp.recordKafkaMessage(noOp.startSample(), "producer", "success", "topic");
        noOp.recordHttpBodySize("outgoing", "webclient", "json", "request", 100L);
        noOp.recordHttpSkipped("outgoing", "webclient", "streaming");
        noOp.recordKafkaSkipped("consumer", "exclude_topic");
        noOp.recordKafkaMessageSize("consumer", 1024L, "topic");
        noOp.recordPhase(noOp.startSample(), BodyMetricsContext.NONE, "parse");
        noOp.recordJsonSerialization(noOp.startSample(), "http", "outgoing", "webclient");
        noOp.recordBodyMaskerInvocation(noOp.startSample(), "X", "outgoing");
        noOp.recordHttpBodyCaptureFailure("outgoing", "webclient", "parse");
        noOp.recordKafkaBodyCaptureFailure("consumer", "capture");

        assertThatThrownBy(() -> registry.get("wiretap.http.overhead").timer())
                .isInstanceOf(MeterNotFoundException.class);
        assertThat(registry.getMeters()).isEmpty();
        assertThat(noOp.isDetailedTimingsEnabled()).isFalse();
    }
}
