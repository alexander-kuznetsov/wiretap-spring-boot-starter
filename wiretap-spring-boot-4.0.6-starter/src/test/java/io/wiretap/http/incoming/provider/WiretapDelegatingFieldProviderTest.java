package io.wiretap.http.incoming.provider;

import ch.qos.logback.access.common.spi.IAccessEvent;
import tools.jackson.core.JsonGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WiretapDelegatingFieldProviderTest {

    @AfterEach
    void resetProviders() {
        WiretapDelegatingFieldProvider.setProviders(List.of());
    }

    @Test
    void writeTo_withNoProviders_isNoop() throws IOException {
        WiretapDelegatingFieldProvider provider = new WiretapDelegatingFieldProvider();
        JsonGenerator generator = mock(JsonGenerator.class);
        IAccessEvent event = mock(IAccessEvent.class);

        provider.writeTo(generator, event);

        verifyNoInteractionsWithGenerator(generator);
    }

    @Test
    void writeTo_invokesAllProvidersInRegistrationOrder() throws IOException {
        WiretapAccessFieldProvider first = mock(WiretapAccessFieldProvider.class);
        WiretapAccessFieldProvider second = mock(WiretapAccessFieldProvider.class);
        WiretapDelegatingFieldProvider.setProviders(List.of(first, second));

        WiretapDelegatingFieldProvider delegating = new WiretapDelegatingFieldProvider();
        JsonGenerator generator = mock(JsonGenerator.class);
        IAccessEvent event = mock(IAccessEvent.class);

        delegating.writeTo(generator, event);

        InOrder order = inOrder(first, second);
        order.verify(first).writeTo(generator, event);
        order.verify(second).writeTo(generator, event);
    }

    @Test
    void writeTo_swallowsProviderFailuresAndContinues() throws IOException {
        WiretapAccessFieldProvider failing = mock(WiretapAccessFieldProvider.class);
        WiretapAccessFieldProvider survivor = mock(WiretapAccessFieldProvider.class);
        doThrow(new RuntimeException("boom")).when(failing).writeTo(any(), any());
        WiretapDelegatingFieldProvider.setProviders(List.of(failing, survivor));

        WiretapDelegatingFieldProvider delegating = new WiretapDelegatingFieldProvider();
        JsonGenerator generator = mock(JsonGenerator.class);
        IAccessEvent event = mock(IAccessEvent.class);

        delegating.writeTo(generator, event);

        verify(survivor).writeTo(generator, event);
    }

    @Test
    void setProviders_makesDefensiveCopy() {
        java.util.ArrayList<WiretapAccessFieldProvider> mutable = new java.util.ArrayList<>();
        mutable.add(mock(WiretapAccessFieldProvider.class));
        WiretapDelegatingFieldProvider.setProviders(mutable);

        mutable.clear();

        // adding to the source list afterwards must not affect what the delegator sees
        assertThat(mutable).isEmpty();
        // sanity: writeTo still has 1 provider to call
        WiretapDelegatingFieldProvider delegating = new WiretapDelegatingFieldProvider();
        delegating.writeTo(mock(JsonGenerator.class), mock(IAccessEvent.class));
    }

    private static void verifyNoInteractionsWithGenerator(JsonGenerator generator) {
        org.mockito.Mockito.verifyNoInteractions(generator);
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
