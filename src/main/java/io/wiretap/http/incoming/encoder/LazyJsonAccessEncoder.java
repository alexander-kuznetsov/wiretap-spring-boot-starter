package io.wiretap.http.incoming.encoder;

import ch.qos.logback.access.spi.IAccessEvent;
import net.logstash.logback.encoder.AccessEventCompositeJsonEncoder;
import io.wiretap.http.incoming.encoder.postprocessor.HttpAccessEventPostProcessHandler;

public class LazyJsonAccessEncoder extends AccessEventCompositeJsonEncoder {

    private static volatile HttpAccessEventPostProcessHandler httpAccessEventPostProcessHandler;

    /** Called by {@link io.wiretap.configuration.HttpAccessEventEncoderConfiguration} when the optional handler bean is present. */
    public static void setPostProcessHandler(HttpAccessEventPostProcessHandler handler) {
        httpAccessEventPostProcessHandler = handler;
    }

    @Override
    public byte[] encode(IAccessEvent iAccessEvent) {
        byte[] encodedBytes = super.encode(iAccessEvent);
        HttpAccessEventPostProcessHandler h = httpAccessEventPostProcessHandler;
        if (h != null) {
            h.performPostProcess(encodedBytes);
        }
        return encodedBytes;
    }
}
