package io.wiretap.http.incoming.provider.trace;

import ch.qos.logback.access.spi.IAccessEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractFieldJsonProvider;

import java.io.IOException;

import static io.wiretap.http.incoming.SleuthCorrelationId.SLEUTH_TRACE_ID;

/**
 * Custom logback-access provider that writes the trace_id field.
 * Plugged in directly via the logback-access XML configuration.
 */
public class TraceIdProvider extends AbstractFieldJsonProvider<IAccessEvent> {

    private static final String TRACE_ID = "trace_id";

    @Override
    public void writeTo(JsonGenerator generator, IAccessEvent iAccessEvent) throws IOException {
        generator.writeFieldName(TRACE_ID);
        generator.writeString((String) iAccessEvent.getAttribute(SLEUTH_TRACE_ID.getAttributeName()));
    }
}
