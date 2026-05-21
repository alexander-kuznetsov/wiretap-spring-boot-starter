package io.wiretap.applog.provider;

import ch.qos.logback.classic.spi.ILoggingEvent;
import tools.jackson.core.JsonGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class WiretapDelegatingLogFieldProviderTest {

    @AfterEach
    void resetProviders() {
        WiretapDelegatingLogFieldProvider.setProviders(List.of());
    }

    @Test
    void writeTo_withNoProviders_isNoop() throws IOException {
        WiretapDelegatingLogFieldProvider provider = new WiretapDelegatingLogFieldProvider();
        JsonGenerator generator = mock(JsonGenerator.class);
        ILoggingEvent event = mock(ILoggingEvent.class);

        provider.writeTo(generator, event);

        verifyNoInteractions(generator);
    }

    @Test
    void writeTo_invokesAllProvidersInRegistrationOrder() throws IOException {
        WiretapLogFieldProvider first = mock(WiretapLogFieldProvider.class);
        WiretapLogFieldProvider second = mock(WiretapLogFieldProvider.class);
        WiretapDelegatingLogFieldProvider.setProviders(List.of(first, second));

        WiretapDelegatingLogFieldProvider delegating = new WiretapDelegatingLogFieldProvider();
        JsonGenerator generator = mock(JsonGenerator.class);
        ILoggingEvent event = mock(ILoggingEvent.class);

        delegating.writeTo(generator, event);

        InOrder order = inOrder(first, second);
        order.verify(first).writeTo(generator, event);
        order.verify(second).writeTo(generator, event);
    }

    @Test
    void writeTo_swallowsProviderFailuresAndContinues() throws IOException {
        WiretapLogFieldProvider failing = mock(WiretapLogFieldProvider.class);
        WiretapLogFieldProvider survivor = mock(WiretapLogFieldProvider.class);
        doThrow(new RuntimeException("boom")).when(failing).writeTo(any(), any());
        WiretapDelegatingLogFieldProvider.setProviders(List.of(failing, survivor));

        WiretapDelegatingLogFieldProvider delegating = new WiretapDelegatingLogFieldProvider();
        JsonGenerator generator = mock(JsonGenerator.class);
        ILoggingEvent event = mock(ILoggingEvent.class);

        delegating.writeTo(generator, event);

        verify(survivor).writeTo(generator, event);
    }

    @Test
    void setProviders_makesDefensiveCopy() throws IOException {
        ArrayList<WiretapLogFieldProvider> mutable = new ArrayList<>();
        WiretapLogFieldProvider provider = mock(WiretapLogFieldProvider.class);
        mutable.add(provider);
        WiretapDelegatingLogFieldProvider.setProviders(mutable);

        mutable.clear();

        WiretapDelegatingLogFieldProvider delegating = new WiretapDelegatingLogFieldProvider();
        JsonGenerator generator = mock(JsonGenerator.class);
        ILoggingEvent event = mock(ILoggingEvent.class);
        delegating.writeTo(generator, event);

        verify(provider).writeTo(generator, event);
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
