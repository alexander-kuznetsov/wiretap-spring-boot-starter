package io.wiretap.kafka;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.wiretap.kafka.message.KafkaMessageInfo;
import io.wiretap.kafka.message.KafkaValueMaskingHandler;
import io.wiretap.kafka.message.settings.KafkaAccessFieldNames;
import io.wiretap.kafka.message.settings.KafkaInfoLogMessageSettings;
import io.wiretap.kafka.message.settings.KafkaProducerLogMessageSettings;
import io.wiretap.metrics.WiretapMetrics;
import io.wiretap.metrics.WiretapMetricsImpl;
import io.wiretap.metrics.WiretapMetricsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link KafkaLogSink#renderValue} emits the same
 * {@code wiretap.body.phase} (parse / mask / truncate) and
 * {@code wiretap.body.masker.invocation} timers that the HTTP body
 * pipeline does — under {@code wiretap.metrics.detailed-timings=true},
 * and that nothing leaks when the flag is off.
 */
class KafkaLogSinkMetricsTest {

    private SimpleMeterRegistry registry;
    private WiretapMetricsProperties props;
    private WiretapMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        props = new WiretapMetricsProperties();
        props.setDetailedTimings(true);
        metrics = new WiretapMetricsImpl(registry, props);
    }

    private KafkaMessageInfo info(String key, String value) {
        return KafkaMessageInfo.builder()
                .direction(KafkaMessageInfo.Direction.OUTGOING)
                .topic("demo.events")
                .key(key)
                .value(value)
                .status(KafkaMessageInfo.Status.SUCCESS)
                .build();
    }

    private long timerCount(String phase, String contentTypeClass) {
        io.micrometer.core.instrument.Timer t = registry.find("wiretap.body.phase")
                .tag("phase", phase)
                .tag("direction", "producer")
                .tag("client", "kafka")
                .tag("content_type_class", contentTypeClass)
                .timer();
        return t == null ? 0L : t.count();
    }

    @Test
    void parsePhaseIsRecorded_forJsonValue() {
        KafkaLogSink sink = new KafkaLogSink(
                new KafkaProducerLogMessageSettings(), new KafkaAccessFieldNames(),
                null, null, null, metrics);

        sink.emit(info("k", "{\"id\":42}"));

        // key "k" -> "other" + value "{...}" -> "json": both phases recorded.
        assertThat(timerCount("parse", "json")).isEqualTo(1L);
        assertThat(timerCount("parse", "other")).isEqualTo(1L);
    }

    @Test
    void parsePhaseIsRecorded_forNonJsonValue() {
        KafkaLogSink sink = new KafkaLogSink(
                new KafkaProducerLogMessageSettings(), new KafkaAccessFieldNames(),
                null, null, null, metrics);

        sink.emit(info("k", "not-json"));

        // both key and value classified as "other".
        assertThat(timerCount("parse", "other")).isEqualTo(2L);
        assertThat(timerCount("parse", "json")).isEqualTo(0L);
    }

    @Test
    void maskPhaseAndMaskerInvocationAreRecorded_whenMaskingActive() {
        KafkaValueMaskingHandler handler = (topic, value) -> value;
        KafkaProducerLogMessageSettings settings = new KafkaProducerLogMessageSettings();
        settings.getMessageBodySettings().setEnableValueMasking(true);
        KafkaLogSink sink = new KafkaLogSink(
                settings, new KafkaAccessFieldNames(),
                handler, null, null, metrics);

        sink.emit(info("k", "{\"id\":42}"));

        assertThat(timerCount("mask", "other")).isEqualTo(2L); // key + value
        long maskerCount = registry.find("wiretap.body.masker.invocation")
                .tag("masker_class", handler.getClass().getName())
                .tag("direction", "producer")
                .timer()
                .count();
        assertThat(maskerCount).isEqualTo(2L);
    }

    @Test
    void truncatePhaseIsRecorded_whenTruncationFires() {
        KafkaProducerLogMessageSettings settings = new KafkaProducerLogMessageSettings();
        settings.getMessageBodySettings().setEnableValueTruncating(true);
        settings.getMessageBodySettings().setMaxValueLength(20);
        KafkaLogSink sink = new KafkaLogSink(
                settings, new KafkaAccessFieldNames(),
                null, null, null, metrics);

        sink.emit(info("k", "{\"id\":42,\"name\":\"alice\",\"city\":\"berlin\"}"));

        // only value is long enough to trip the limit; key "k" stays under.
        assertThat(timerCount("truncate", "other")).isEqualTo(1L);
    }

    @Test
    void noPhaseTimersEmitted_whenDetailedTimingsDisabled() {
        props.setDetailedTimings(false);
        KafkaLogSink sink = new KafkaLogSink(
                new KafkaProducerLogMessageSettings(), new KafkaAccessFieldNames(),
                null, null, null, metrics);

        sink.emit(info("k", "{\"id\":42}"));

        assertThat(registry.find("wiretap.body.phase").meters()).isEmpty();
        assertThat(registry.find("wiretap.body.masker.invocation").meters()).isEmpty();
    }

    @Test
    void parsePhaseTaggedAsConsumer_forIncomingDirection() {
        KafkaLogSink sink = new KafkaLogSink(
                new KafkaProducerLogMessageSettings(), new KafkaAccessFieldNames(),
                null, null, null, metrics);

        KafkaMessageInfo incoming = KafkaMessageInfo.builder()
                .direction(KafkaMessageInfo.Direction.INCOMING)
                .topic("demo.events")
                .key("k")
                .value("{\"id\":1}")
                .status(KafkaMessageInfo.Status.SUCCESS)
                .build();
        sink.emit(incoming);

        long consumerJsonParse = registry.find("wiretap.body.phase")
                .tag("phase", "parse")
                .tag("direction", "consumer")
                .tag("client", "kafka")
                .tag("content_type_class", "json")
                .timer()
                .count();
        assertThat(consumerJsonParse).isEqualTo(1L);
    }

    @Test
    void allPhaseMetersUseClientKafka() {
        KafkaValueMaskingHandler handler = (topic, value) -> value;
        KafkaProducerLogMessageSettings settings = new KafkaProducerLogMessageSettings();
        settings.getMessageBodySettings().setEnableValueMasking(true);
        settings.getMessageBodySettings().setEnableValueTruncating(true);
        settings.getMessageBodySettings().setMaxValueLength(10);
        KafkaLogSink sink = new KafkaLogSink(
                settings, new KafkaAccessFieldNames(),
                handler, null, null, metrics);

        sink.emit(info("k", "{\"id\":42,\"name\":\"alice\"}"));

        for (Meter m : registry.find("wiretap.body.phase").meters()) {
            assertThat(m.getId().getTag("client")).isEqualTo("kafka");
        }
    }
}
