package io.wiretap.http.incoming.provider.trace;

import ch.qos.logback.access.common.spi.IAccessEvent;
import tools.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractFieldJsonProvider;

import java.io.IOException;

import static io.wiretap.http.incoming.SleuthCorrelationId.SLEUTH_TRACE_ID;

/**
 * Custom logback-access provider that writes the trace_id field.
 * Plugged in directly via the logback-access XML configuration.
 */
public class TraceIdProvider extends AbstractFieldJsonProvider<IAccessEvent> {

    private static volatile String fieldName = "trace_id";

    /** Called by {@link io.wiretap.configuration.WiretapFieldNamesProperties} on Spring startup. */
    public static void configureFieldName(String name) {
        fieldName = name;
    }

    @Override
    public void writeTo(JsonGenerator generator, IAccessEvent iAccessEvent) {
        generator.writeName(fieldName);
        generator.writeString((String) iAccessEvent.getAttribute(SLEUTH_TRACE_ID.getAttributeName()));
    }
}
