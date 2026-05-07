package io.wiretap.http.incoming.provider.trace;

import ch.qos.logback.access.spi.IAccessEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractFieldJsonProvider;

import java.io.IOException;

/**
 * Logback-access provider that emits the {@code session_key} field, sourced from
 * a configurable inbound or response header. The header name is supplied by
 * {@link io.wiretap.configuration.WiretapHeadersProperties} at startup.
 */
public class SessionKeyProvider extends AbstractFieldJsonProvider<IAccessEvent> {

    private static final String SESSION_KEY = "session_key";

    /** Initialised by {@code WiretapHeadersProperties.apply()} from configuration. */
    public static String sessionKeyHeader = "x-session-key";

    @Override
    public void writeTo(JsonGenerator generator, IAccessEvent iAccessEvent) throws IOException {
        final String requestHeaderValue = iAccessEvent.getRequestHeaderMap().get(sessionKeyHeader);
        final String responseHeaderValue = iAccessEvent.getResponseHeaderMap().get(sessionKeyHeader);
        if (requestHeaderValue != null) {
            generator.writeFieldName(SESSION_KEY);
            generator.writeString(requestHeaderValue);
        } else if (responseHeaderValue != null) {
            generator.writeFieldName(SESSION_KEY);
            generator.writeString(responseHeaderValue);
        }
    }
}
