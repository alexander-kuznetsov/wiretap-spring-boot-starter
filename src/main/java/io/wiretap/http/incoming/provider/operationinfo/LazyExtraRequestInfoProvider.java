package io.wiretap.http.incoming.provider.operationinfo;

import ch.qos.logback.access.spi.IAccessEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractFieldJsonProvider;

import java.io.IOException;

/**
 * Custom logback-access provider that adds extra request info to the access log.
 */
public class LazyExtraRequestInfoProvider extends AbstractFieldJsonProvider<IAccessEvent> {
    protected static AbstractFieldJsonProvider<IAccessEvent> provider;

    @Override
    public void writeTo(final JsonGenerator generator, final IAccessEvent iAccessEvent) throws IOException {
        if (provider != null) {
            provider.writeTo(generator, iAccessEvent);
        }
    }
}
