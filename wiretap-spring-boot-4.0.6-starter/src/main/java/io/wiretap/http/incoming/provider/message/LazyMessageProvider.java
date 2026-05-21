package io.wiretap.http.incoming.provider.message;

import ch.qos.logback.access.common.spi.IAccessEvent;
import tools.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractFieldJsonProvider;

import java.io.IOException;

public class LazyMessageProvider extends AbstractFieldJsonProvider<IAccessEvent> {

    private static volatile AbstractFieldJsonProvider<IAccessEvent> provider;

    /** Called once by {@link io.wiretap.http.incoming.provider.message.MessageProvider} on Spring startup. */
    public static void setProvider(AbstractFieldJsonProvider<IAccessEvent> p) {
        provider = p;
    }

    @Override
    public void writeTo(JsonGenerator generator, IAccessEvent iAccessEvent) {
        AbstractFieldJsonProvider<IAccessEvent> p = provider;
        if (p != null) {
            p.writeTo(generator, iAccessEvent);
        }
    }
}
