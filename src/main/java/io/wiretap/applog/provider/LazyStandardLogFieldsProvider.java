package io.wiretap.applog.provider;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractFieldJsonProvider;

import java.io.IOException;

/**
 * Logback-instantiated wrapper that delegates to {@link WiretapStandardLogFieldsProvider}
 * once the Spring context has initialised it.
 * <p>
 * Logback reads the XML config and instantiates this class before Spring starts,
 * so the real (Spring-managed) provider is wired in later via the static setter.
 * Until then, {@link #writeTo} is a no-op.
 */
public class LazyStandardLogFieldsProvider extends AbstractFieldJsonProvider<ILoggingEvent> {

    private static volatile WiretapStandardLogFieldsProvider provider;

    /** Called once by {@link WiretapStandardLogFieldsProvider} on Spring startup. */
    public static void setProvider(WiretapStandardLogFieldsProvider p) {
        provider = p;
    }

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        WiretapStandardLogFieldsProvider p = provider;
        if (p != null) {
            p.writeTo(generator, event);
        }
    }
}
