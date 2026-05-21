package io.wiretap.metrics;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Micrometer metrics that Wiretap publishes about its
 * own processing overhead (HTTP / Kafka capture &rarr; mask &rarr; serialise
 * &rarr; emit pipeline).
 * <p>
 * Metrics turn on automatically when a {@code MeterRegistry} bean exists in
 * the context (typically supplied by {@code spring-boot-starter-actuator}).
 * Set {@code wiretap.metrics.enabled=false} to skip registration entirely.
 *
 * <pre>
 * wiretap:
 *   metrics:
 *     enabled: true              # master switch (default true)
 *     detailed-timings: false    # parse/mask/truncate/serialise phase timers
 *     histograms: false          # publish percentile-histogram + p50/p95/p99
 *     tags:
 *       topic: false             # include Kafka topic in tags (cardinality risk)
 *       status: true             # include grouped HTTP status (2xx/3xx/4xx/5xx)
 *     async-appender:
 *       enabled: true            # publish AsyncAppender queue gauges when active
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "wiretap.metrics")
public class WiretapMetricsProperties {

    /** Master switch. When {@code false}, a no-op {@code WiretapMetrics} bean is installed. */
    private boolean enabled = true;

    /**
     * Publish per-phase timers ({@code wiretap.body.phase},
     * {@code wiretap.json.serialization}, {@code wiretap.body.masker.invocation}).
     * Adds an extra {@code System.nanoTime()} per processing phase — leave off
     * unless you are drill-down debugging.
     */
    private boolean detailedTimings = false;

    /**
     * Publish percentile-histograms (Prometheus-style heat-maps) plus
     * {@code p50}/{@code p95}/{@code p99} percentiles for every timer.
     * Costs roughly 60 KB of heap per series — leave off unless your backend
     * needs server-side percentiles.
     */
    private boolean histograms = false;

    private final Tags tags = new Tags();
    private final AsyncAppender asyncAppender = new AsyncAppender();

    @Getter
    @Setter
    public static class Tags {
        /**
         * Include the Kafka topic name as a tag. Off by default because every
         * new topic creates a new metric series — fine for fixed topic sets,
         * dangerous for dynamic ones.
         */
        private boolean topic = false;

        /**
         * Include the HTTP status group ({@code 2xx} / {@code 3xx} / {@code 4xx}
         * / {@code 5xx} / {@code other} / {@code exception}) as a tag. Bounded
         * cardinality.
         */
        private boolean status = true;
    }

    @Getter
    @Setter
    public static class AsyncAppender {
        /**
         * Publish queue-size / queue-capacity / queue-remaining gauges for any
         * {@code AsyncAppender} found on the Logback context. Only takes effect
         * when {@code wiretap.async-logging.enabled=true} so that the appender
         * actually exists.
         */
        private boolean enabled = true;
    }
}
