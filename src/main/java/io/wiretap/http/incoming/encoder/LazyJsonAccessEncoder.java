package io.wiretap.http.incoming.encoder;

import ch.qos.logback.access.spi.IAccessEvent;
import net.logstash.logback.encoder.AccessEventCompositeJsonEncoder;
import io.wiretap.http.incoming.encoder.postprocessor.HttpAccessEventPostProcessHandler;
import io.wiretap.metrics.NoOpWiretapMetrics;
import io.wiretap.metrics.WiretapMetrics;
import io.wiretap.util.HttpStatusClassifier;

public class LazyJsonAccessEncoder extends AccessEventCompositeJsonEncoder {

    private static final String DIRECTION = "incoming";
    private static final String CLIENT = "servlet";

    private static volatile HttpAccessEventPostProcessHandler httpAccessEventPostProcessHandler;

    /**
     * Initialised by {@code WiretapAccessLogConfiguration} on Spring startup;
     * defaults to a no-op so the encoder stays usable when Logback fires events
     * before / after the Spring lifecycle.
     */
    private static volatile WiretapMetrics metrics = new NoOpWiretapMetrics();

    /** Called by {@link io.wiretap.configuration.HttpAccessEventEncoderConfiguration} when the optional handler bean is present. */
    public static void setPostProcessHandler(HttpAccessEventPostProcessHandler handler) {
        httpAccessEventPostProcessHandler = handler;
    }

    /** Called by {@code WiretapAccessLogConfiguration} on Spring startup. */
    public static void setMetrics(WiretapMetrics registered) {
        metrics = registered == null ? new NoOpWiretapMetrics() : registered;
    }

    @Override
    public byte[] encode(IAccessEvent iAccessEvent) {
        long startNanos = metrics.startSample();
        byte[] encodedBytes = render(iAccessEvent);
        recordOverhead(startNanos, iAccessEvent);
        HttpAccessEventPostProcessHandler h = httpAccessEventPostProcessHandler;
        if (h != null) {
            h.performPostProcess(encodedBytes);
        }
        return encodedBytes;
    }

    /**
     * Renders the full access-log JSON — every JsonProvider, including
     * {@code HttpInfoMessageProvider} (body parse / mask / serialise). On failure
     * records a {@code serialize} body-capture failure and rethrows: without the
     * metric it would have thrown anyway, so recording must not mask it.
     */
    private byte[] render(IAccessEvent event) {
        try {
            return super.encode(event);
        } catch (RuntimeException e) {
            recordSerializeFailure();
            throw e;
        }
    }

    /**
     * Records {@code wiretap.http.overhead} for incoming. The request is already
     * complete by the time logback-access fires, so there is no downstream call to
     * subtract. Never throws — metrics must not break access-log encoding.
     */
    private void recordOverhead(long startNanos, IAccessEvent event) {
        try {
            int status = event.getStatusCode();
            metrics.recordHttpRequest(startNanos, 0L, DIRECTION, CLIENT,
                    HttpStatusClassifier.outcome(status), HttpStatusClassifier.statusGroup(status));
        } catch (Throwable ignored) {
            // metrics must never break access-log encoding
        }
    }

    private void recordSerializeFailure() {
        try {
            metrics.recordHttpBodyCaptureFailure(DIRECTION, CLIENT, "serialize");
        } catch (Throwable ignored) {
            // metrics must never break access-log encoding
        }
    }
}
