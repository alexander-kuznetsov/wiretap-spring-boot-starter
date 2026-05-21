package io.wiretap.http.incoming.provider;

import ch.qos.logback.access.spi.IAccessEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import io.wiretap.metrics.NoOpWiretapMetrics;
import io.wiretap.metrics.WiretapMetrics;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Logback-access provider that fans out every event to all
 * {@link WiretapAccessFieldProvider} beans registered in the Spring context.
 * <p>
 * Plugged into {@code logback-access-*-appender.xml} once via
 * {@code <provider class="..."/>} — individual SPI providers do not need their
 * own XML entry.
 * <p>
 * Failures in one provider are logged but never block other providers from
 * contributing their fields.
 */
public class WiretapDelegatingFieldProvider extends AbstractFieldJsonProvider<IAccessEvent> {

    private static final Logger log = LoggerFactory.getLogger(WiretapDelegatingFieldProvider.class);
    private static final String DIRECTION = "incoming";
    private static final String CLIENT = "servlet";

    /** Initialised by {@code WiretapAccessLogConfiguration} on Spring startup. */
    private static volatile List<WiretapAccessFieldProvider> providers = List.of();

    /**
     * Initialised by {@code WiretapAccessLogConfiguration} on Spring startup;
     * defaults to a no-op until the Spring context is up so that the provider
     * remains usable when Logback fires events before / after Spring lifecycle.
     */
    private static volatile WiretapMetrics metrics = new NoOpWiretapMetrics();

    /** Replaces the active provider set. Called once during Spring context initialisation. */
    public static void setProviders(List<WiretapAccessFieldProvider> registered) {
        providers = List.copyOf(registered);
    }

    /** Replaces the metrics facade. Called once during Spring context initialisation. */
    public static void setMetrics(WiretapMetrics registered) {
        metrics = registered == null ? new NoOpWiretapMetrics() : registered;
    }

    @Override
    public void writeTo(JsonGenerator generator, IAccessEvent event) throws IOException {
        long startNanos = metrics.startSample();
        boolean failed = false;
        for (WiretapAccessFieldProvider provider : providers) {
            try {
                provider.writeTo(generator, event);
            } catch (Throwable t) {
                failed = true;
                log.warn("Wiretap field provider {} failed", provider.getClass().getName(), t);
            }
        }
        try {
            int status = event.getStatusCode();
            String outcome = failed ? "exception" : outcomeOf(status);
            String statusGroup = failed ? "exception" : statusGroup(status);
            metrics.recordHttpRequest(startNanos, DIRECTION, CLIENT, outcome, statusGroup);
            try {
                long requestBytes = event.getRequestContent() == null ? 0L : event.getRequestContent().length();
                metrics.recordHttpBodySize(DIRECTION, CLIENT, "other", "request", requestBytes);
            } catch (Throwable ignored) { /* metrics must not break the hot path */ }
            try {
                long responseBytes = event.getResponseContent() == null ? 0L : event.getResponseContent().length();
                metrics.recordHttpBodySize(DIRECTION, CLIENT, "other", "response", responseBytes);
            } catch (Throwable ignored) { /* metrics must not break the hot path */ }
        } catch (Throwable ignored) {
            // never let metrics recording break logback-access serialisation
        }
    }

    private static String outcomeOf(int status) {
        if (status >= 200 && status < 400) return "success";
        if (status >= 400 && status < 500) return "client_error";
        if (status >= 500 && status < 600) return "server_error";
        return "other";
    }

    private static String statusGroup(int status) {
        if (status >= 200 && status < 300) return "2xx";
        if (status >= 300 && status < 400) return "3xx";
        if (status >= 400 && status < 500) return "4xx";
        if (status >= 500 && status < 600) return "5xx";
        return "other";
    }
}
