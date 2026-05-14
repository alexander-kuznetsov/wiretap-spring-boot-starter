package io.wiretap.http.incoming.encoder;

import ch.qos.logback.access.spi.IAccessEvent;
import net.logstash.logback.encoder.AccessEventCompositeJsonEncoder;
import io.wiretap.http.incoming.encoder.postprocessor.HttpAccessEventPostProcessHandler;

public class LazyJsonAccessEncoder extends AccessEventCompositeJsonEncoder {

    public static HttpAccessEventPostProcessHandler httpAccessEventPostProcessHandler;

    @Override
    public byte[] encode(IAccessEvent iAccessEvent) {
        byte[] encodedBytes = super.encode(iAccessEvent);
        if (httpAccessEventPostProcessHandler != null) {
            httpAccessEventPostProcessHandler.performPostProcess(encodedBytes);
        }
        return encodedBytes;
    }
}
