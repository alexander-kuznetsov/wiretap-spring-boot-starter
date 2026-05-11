package io.wiretap.http.incoming.provider;

import ch.qos.logback.access.spi.IAccessEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import io.wiretap.http.incoming.provider.httpinfo.LazyHttpInfoMessageProvider;
import io.wiretap.http.incoming.provider.message.LazyMessageProvider;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Static state on the Lazy* providers must remain null-safe (logback evaluates
 * its XML before Spring wires the delegate). After Spring sets the delegate,
 * the provider must forward writeTo calls to it.
 */
class LazyDelegatesTest {

    @BeforeEach
    @AfterEach
    void clearStatics() {
        LazyMessageProvider.setProvider(null);
        LazyHttpInfoMessageProvider.setProvider(null);
    }

    @Test
    void lazyMessageProvider_isNoopUntilWired() throws IOException {
        JsonGenerator gen = mock(JsonGenerator.class);
        IAccessEvent event = mock(IAccessEvent.class);

        new LazyMessageProvider().writeTo(gen, event);

        verifyNoInteractions(gen);
    }

    @Test
    void lazyMessageProvider_delegatesToWiredProvider() throws IOException {
        @SuppressWarnings("unchecked")
        AbstractFieldJsonProvider<IAccessEvent> real = mock(AbstractFieldJsonProvider.class);
        LazyMessageProvider.setProvider(real);
        JsonGenerator gen = mock(JsonGenerator.class);
        IAccessEvent event = mock(IAccessEvent.class);

        new LazyMessageProvider().writeTo(gen, event);

        verify(real).writeTo(gen, event);
    }

    @Test
    void lazyHttpInfoMessageProvider_delegatesToWiredProvider() throws IOException {
        @SuppressWarnings("unchecked")
        AbstractFieldJsonProvider<IAccessEvent> real = mock(AbstractFieldJsonProvider.class);
        LazyHttpInfoMessageProvider.setProvider(real);
        JsonGenerator gen = mock(JsonGenerator.class);
        IAccessEvent event = mock(IAccessEvent.class);

        new LazyHttpInfoMessageProvider().writeTo(gen, event);

        verify(real).writeTo(gen, event);
    }

}
