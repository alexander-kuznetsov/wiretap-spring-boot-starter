package io.wiretap.http.incoming.provider.httpinfo;

import ch.qos.logback.access.common.spi.IAccessEvent;
import tools.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractFieldJsonProvider;

import java.io.IOException;

/**
 * Logback-access provider that emits inbound HTTP request information.
 * <p>
 * Initialisation happens in two steps:
 * <ol>
 *     <li>logback instantiates this class while parsing its XML config — at this
 *         point Spring is not yet available, so logback can only call the
 *         no-args constructor;</li>
 *     <li>once the Spring context is up, the actual provider bean is plugged
 *         into the static {@link #provider} field and routes the call through
 *         to the real implementation.</li>
 * </ol>
 */
public class LazyHttpInfoMessageProvider extends AbstractFieldJsonProvider<IAccessEvent> {

    private static volatile AbstractFieldJsonProvider<IAccessEvent> provider;

    /** Called once by {@link io.wiretap.http.incoming.provider.httpinfo.HttpInfoMessageProvider} on Spring startup. */
    public static void setProvider(AbstractFieldJsonProvider<IAccessEvent> p) {
        provider = p;
    }

    @Override
    public void writeTo(final JsonGenerator generator, final IAccessEvent iAccessEvent) {
        AbstractFieldJsonProvider<IAccessEvent> p = provider;
        if (p != null) {
            p.writeTo(generator, iAccessEvent);
        }
    }
}
