package io.wiretap.http.incoming.provider.operationinfo;

import ch.qos.logback.access.spi.IAccessEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractFieldJsonProvider;

import java.io.IOException;

/**
 * Custom logback-access provider that adds extra request info to the access log.
 */
public class LazyExtraRequestInfoProvider extends AbstractFieldJsonProvider<IAccessEvent> {

    private static volatile AbstractFieldJsonProvider<IAccessEvent> provider;

    /** Called once by {@link io.wiretap.http.incoming.provider.operationinfo.ExtraRequestInfoProvider} on Spring startup. */
    public static void setProvider(AbstractFieldJsonProvider<IAccessEvent> p) {
        provider = p;
    }

    @Override
    public void writeTo(final JsonGenerator generator, final IAccessEvent iAccessEvent) throws IOException {
        AbstractFieldJsonProvider<IAccessEvent> p = provider;
        if (p != null) {
            p.writeTo(generator, iAccessEvent);
        }
    }
}
