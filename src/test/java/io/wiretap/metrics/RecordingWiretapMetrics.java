package io.wiretap.metrics;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test double that records the arguments of the last
 * {@link #recordHttpRequest} call so tests can assert on the
 * {@code downstreamNanos} value the interceptors pass in. Every other recording
 * method is an inert no-op. {@code startSample()} keeps the default
 * {@code System.nanoTime()} behaviour so elapsed/downstream timings advance for
 * real during a test.
 */
public final class RecordingWiretapMetrics implements WiretapMetrics {

    public final AtomicInteger httpRequestCount = new AtomicInteger();
    public volatile long lastStartNanos = -1L;
    public volatile long lastDownstreamNanos = -1L;
    public volatile String lastOutcome;
    public volatile String lastStatus;

    @Override
    public boolean isDetailedTimingsEnabled() {
        return false;
    }

    @Override
    public void recordHttpRequest(long startNanos, long downstreamNanos, String direction, String client, String outcome, String status) {
        this.lastStartNanos = startNanos;
        this.lastDownstreamNanos = downstreamNanos;
        this.lastOutcome = outcome;
        this.lastStatus = status;
        httpRequestCount.incrementAndGet();
    }

    @Override
    public void recordHttpSkipped(String direction, String client, String reason) { }

    @Override
    public void recordHttpBodySize(String direction, String client, String contentTypeClass, String kind, long bytes) { }

    @Override
    public void recordHttpBodyCaptureFailure(String direction, String client, String phase) { }

    @Override
    public void recordKafkaMessage(long startNanos, String direction, String outcome, String topic) { }

    @Override
    public void recordKafkaSkipped(String direction, String reason) { }

    @Override
    public void recordKafkaMessageSize(String direction, long bytes, String topic) { }

    @Override
    public void recordKafkaBodyCaptureFailure(String direction, String phase) { }

    @Override
    public void recordPhase(long startNanos, BodyMetricsContext context, String phase) { }

    @Override
    public void recordJsonSerialization(long startNanos, String sink, String direction, String client) { }

    @Override
    public void recordBodyMaskerInvocation(long startNanos, String maskerClass, String direction) { }
}
