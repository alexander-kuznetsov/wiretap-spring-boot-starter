package io.wiretap.metrics;

/**
 * Inert implementation installed when no {@code MeterRegistry} bean is present
 * (typically: no {@code spring-boot-starter-actuator}) or when
 * {@code wiretap.metrics.enabled=false}. Every recording method is a no-op so
 * interceptors can call them unconditionally without branching.
 *
 * <p>This class is intentionally free of any {@code io.micrometer.*} reference,
 * so it stays loadable even when the consumer never pulled Micrometer in.
 */
public final class NoOpWiretapMetrics implements WiretapMetrics {

    @Override
    public boolean isDetailedTimingsEnabled() {
        return false;
    }

    @Override
    public void recordHttpRequest(long startNanos, String direction, String client, String outcome, String status) { }

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
    public void recordPhase(long startNanos, BodyMetricsContext context, String phase) { }

    @Override
    public void recordJsonSerialization(long startNanos, String sink, String direction, String client) { }

    @Override
    public void recordBodyMaskerInvocation(long startNanos, String maskerClass, String direction) { }
}
