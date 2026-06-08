package io.wiretap.http.incoming.filter;

import io.wiretap.configuration.WiretapHeadersProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CorrelationHeadersMdcFilter}.
 * <p>
 * The filter is exercised through its protected {@code doFilterInternal} (callable
 * from this same-package test), which bypasses the {@code OncePerRequestFilter}
 * dispatcher-type guards that would otherwise NPE on a bare Mockito request.
 */
class CorrelationHeadersMdcFilterTest {

    private final WiretapHeadersProperties props = new WiretapHeadersProperties();

    @BeforeEach
    void clearMdc() {
        MDC.clear();
    }

    @AfterEach
    void cleanupMdc() {
        MDC.clear();
    }

    @Test
    void populatesMdcBeforeChain() throws ServletException, java.io.IOException {
        props.setForwardToMdc(List.of("x-request-id", "x-session-key"));
        CorrelationHeadersMdcFilter filter = new CorrelationHeadersMdcFilter(props);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("x-request-id")).thenReturn("req-1");
        when(request.getHeader("x-session-key")).thenReturn("sess-1");

        Map<String, String> seenInsideChain = new HashMap<>();
        FilterChain chain = (req, res) -> {
            seenInsideChain.put("x-request-id", MDC.get("x-request-id"));
            seenInsideChain.put("x-session-key", MDC.get("x-session-key"));
        };

        filter.doFilterInternal(request, mock(HttpServletResponse.class), chain);

        assertThat(seenInsideChain.get("x-request-id")).isEqualTo("req-1");
        assertThat(seenInsideChain.get("x-session-key")).isEqualTo("sess-1");
    }

    @Test
    void clearsMdcAfterRequest() throws ServletException, java.io.IOException {
        props.setForwardToMdc(List.of("x-request-id"));
        CorrelationHeadersMdcFilter filter = new CorrelationHeadersMdcFilter(props);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("x-request-id")).thenReturn("req-1");

        filter.doFilterInternal(request, mock(HttpServletResponse.class), (req, res) -> {
            // value is present mid-request
            assertThat(MDC.get("x-request-id")).isEqualTo("req-1");
        });

        assertThat(MDC.get("x-request-id")).isNull();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void clearsMdcEvenWhenChainThrows() {
        props.setForwardToMdc(List.of("x-request-id"));
        CorrelationHeadersMdcFilter filter = new CorrelationHeadersMdcFilter(props);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("x-request-id")).thenReturn("req-1");

        FilterChain throwingChain = (req, res) -> {
            throw new ServletException("boom");
        };

        assertThatThrownBy(() ->
                filter.doFilterInternal(request, mock(HttpServletResponse.class), throwingChain))
                .isInstanceOf(ServletException.class);

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void skipsHeadersThatAreAbsent() throws ServletException, java.io.IOException {
        props.setForwardToMdc(List.of("x-trace-id", "x-missing"));
        CorrelationHeadersMdcFilter filter = new CorrelationHeadersMdcFilter(props);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("x-trace-id")).thenReturn("t-1");
        when(request.getHeader("x-missing")).thenReturn(null);

        Map<String, String> seenInsideChain = new HashMap<>();
        FilterChain chain = (req, res) -> {
            seenInsideChain.put("x-trace-id", MDC.get("x-trace-id"));
            seenInsideChain.put("x-missing", MDC.get("x-missing"));
        };

        filter.doFilterInternal(request, mock(HttpServletResponse.class), chain);

        assertThat(seenInsideChain.get("x-trace-id")).isEqualTo("t-1");
        assertThat(seenInsideChain.get("x-missing")).isNull();
    }
}
