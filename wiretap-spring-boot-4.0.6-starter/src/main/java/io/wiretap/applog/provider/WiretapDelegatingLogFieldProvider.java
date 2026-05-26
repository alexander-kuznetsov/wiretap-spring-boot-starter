package io.wiretap.applog.provider;

import ch.qos.logback.classic.spi.ILoggingEvent;
import tools.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Logback provider that fans out every log event to all
 * {@link WiretapLogFieldProvider} beans registered in the Spring context.
 * <p>
 * Plugged into {@code logback-console-appender.xml} and
 * {@code logback-file-appender.xml} once via {@code <provider class="..."/>} —
 * individual SPI providers do not need their own XML entry.
 * <p>
 * Failures in one provider are logged but never block other providers.
 */
public class WiretapDelegatingLogFieldProvider extends AbstractFieldJsonProvider<ILoggingEvent> {

    private static final Logger log = LoggerFactory.getLogger(WiretapDelegatingLogFieldProvider.class);

    /** Initialised by {@link io.wiretap.configuration.WiretapAppLogConfiguration} on Spring startup. */
    private static volatile List<WiretapLogFieldProvider> providers = List.of();

    /** Replaces the active provider set. Called once during Spring context initialisation. */
    public static void setProviders(List<WiretapLogFieldProvider> registered) {
        providers = List.copyOf(registered);
    }

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) {
        for (WiretapLogFieldProvider provider : providers) {
            try {
                provider.writeTo(generator, event);
            } catch (Throwable t) {
                log.warn("Wiretap log field provider {} failed", provider.getClass().getName(), t);
            }
        }
    }
}
