package io.wiretap.http.incoming.provider.trace;

import ch.qos.logback.access.spi.IAccessEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractFieldJsonProvider;

import java.io.IOException;

import static io.wiretap.http.CorrelationHeaders.USER_SESSION_KEY;

public class SessionKeyProvider extends AbstractFieldJsonProvider<IAccessEvent> {

    private static final String SESSION_KEY = "session_key";

    @Override
    public void writeTo(JsonGenerator generator, IAccessEvent iAccessEvent) throws IOException {
        final String requestHeaderSessionKey = iAccessEvent.getRequestHeaderMap().get(USER_SESSION_KEY.toString());
        final String responseHeaderSessionKey = iAccessEvent.getResponseHeaderMap().get(USER_SESSION_KEY.toString());
        if (requestHeaderSessionKey != null) {
            generator.writeFieldName(SESSION_KEY);
            generator.writeString(requestHeaderSessionKey);
        } else if (responseHeaderSessionKey != null) {
            generator.writeFieldName(SESSION_KEY);
            generator.writeString(responseHeaderSessionKey);
        }
    }
}
