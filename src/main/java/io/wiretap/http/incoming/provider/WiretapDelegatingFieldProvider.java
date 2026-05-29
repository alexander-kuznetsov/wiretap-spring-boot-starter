package io.wiretap.http.incoming.provider;

import ch.qos.logback.access.spi.IAccessEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Logback-access provider that fans out every event to all
 * {@link WiretapAccessFieldProvider} beans registered in the Spring context.
 * <p>
 * Plugged into {@code logback-access-*-appender.xml} once via
 * {@code <provider class="..."/>} — individual SPI providers do not need their
 * own XML entry.
 * <p>
 * Failures in one provider are logged but never block other providers from
 * contributing their fields.
 */
public class WiretapDelegatingFieldProvider extends AbstractFieldJsonProvider<IAccessEvent> {

    private static final Logger log = LoggerFactory.getLogger(WiretapDelegatingFieldProvider.class);

    /** Initialised by {@code WiretapAccessLogConfiguration} on Spring startup. */
    private static volatile List<WiretapAccessFieldProvider> providers = List.of();

    /** Replaces the active provider set. Called once during Spring context initialisation. */
    public static void setProviders(List<WiretapAccessFieldProvider> registered) {
        providers = List.copyOf(registered);
    }

    @Override
    public void writeTo(JsonGenerator generator, IAccessEvent event) throws IOException {
        for (WiretapAccessFieldProvider provider : providers) {
            try {
                provider.writeTo(generator, event);
            } catch (Throwable t) {
                log.warn("Wiretap field provider {} failed", provider.getClass().getName(), t);
            }
        }
    }
}
