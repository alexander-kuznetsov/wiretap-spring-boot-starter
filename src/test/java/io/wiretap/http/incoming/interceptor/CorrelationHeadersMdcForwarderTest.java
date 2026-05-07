package io.wiretap.http.incoming.interceptor;

import io.wiretap.configuration.WiretapHeadersProperties;
import io.wiretap.http.incoming.provider.operationinfo.ExtraRequestInfoContextKeeper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CorrelationHeadersMdcForwarderTest {

    private final WiretapHeadersProperties props = new WiretapHeadersProperties();

    @BeforeEach
    void clearMdc() {
        MDC.clear();
        ExtraRequestInfoContextKeeper.clear();
    }

    @AfterEach
    void cleanupMdc() {
        MDC.clear();
    }

    @Test
    void preHandle_copiesPresentHeadersToMdc() {
        props.setForwardToMdc(List.of("x-request-id", "x-session-key"));
        CorrelationHeadersMdcForwarder filter = new CorrelationHeadersMdcForwarder(props);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("x-request-id")).thenReturn("req-1");
        when(request.getHeader("x-session-key")).thenReturn("sess-1");

        filter.preHandle(request, mock(HttpServletResponse.class), new Object());

        assertThat(MDC.get("x-request-id")).isEqualTo("req-1");
        assertThat(MDC.get("x-session-key")).isEqualTo("sess-1");
    }

    @Test
    void preHandle_skipsHeadersThatAreAbsent() {
        props.setForwardToMdc(List.of("x-trace-id", "x-missing"));
        CorrelationHeadersMdcForwarder filter = new CorrelationHeadersMdcForwarder(props);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("x-trace-id")).thenReturn("t-1");
        when(request.getHeader("x-missing")).thenReturn(null);

        filter.preHandle(request, mock(HttpServletResponse.class), new Object());

        assertThat(MDC.get("x-trace-id")).isEqualTo("t-1");
        assertThat(MDC.get("x-missing")).isNull();
    }

    @Test
    void preHandle_returnsTrueToContinueDispatcherChain() {
        CorrelationHeadersMdcForwarder filter = new CorrelationHeadersMdcForwarder(props);

        boolean keepGoing = filter.preHandle(mock(HttpServletRequest.class), mock(HttpServletResponse.class), new Object());

        assertThat(keepGoing).isTrue();
    }
}
