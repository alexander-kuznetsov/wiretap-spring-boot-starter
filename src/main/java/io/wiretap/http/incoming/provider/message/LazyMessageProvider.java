package io.wiretap.http.incoming.provider.message;

import ch.qos.logback.access.spi.IAccessEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractFieldJsonProvider;

import java.io.IOException;

public class LazyMessageProvider extends AbstractFieldJsonProvider<IAccessEvent> {
    protected static AbstractFieldJsonProvider<IAccessEvent> provider;

    @Override
    public void writeTo(JsonGenerator generator, IAccessEvent iAccessEvent) throws IOException {
        if (provider != null) {
            provider.writeTo(generator, iAccessEvent);
        }
    }
}
