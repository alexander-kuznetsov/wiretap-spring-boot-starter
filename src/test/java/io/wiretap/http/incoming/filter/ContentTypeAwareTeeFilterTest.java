package io.wiretap.http.incoming.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the bypass decision of {@link ContentTypeAwareTeeFilter}. The teeing
 * path for ordinary requests is exercised end-to-end by the integration test
 * {@code HttpInboundBodyCaptureTest} (delegating to {@code super.doFilter} on a bare
 * mock would NPE inside logback-access, so it is not unit-tested here).
 */
class ContentTypeAwareTeeFilterTest {

    @Test
    void bypassesTeeingForMultipartLeavingStreamIntact() throws Exception {
        ContentTypeAwareTeeFilter filter = new ContentTypeAwareTeeFilter();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentType()).thenReturn("multipart/form-data;boundary=abc");
        HttpServletResponse response = mock(HttpServletResponse.class);

        AtomicReference<ServletRequest> passedDownstream = new AtomicReference<>();
        FilterChain chain = (req, res) -> passedDownstream.set(req);

        filter.doFilter(request, response, chain);

        // Original request flows downstream unwrapped — the stream is never read here.
        assertThat(passedDownstream.get()).isSameAs(request);
        verify(request, never()).getInputStream();
        verify(request, never()).getReader();
    }
}
