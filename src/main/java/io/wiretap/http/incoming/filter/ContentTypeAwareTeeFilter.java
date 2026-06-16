package io.wiretap.http.incoming.filter;

import ch.qos.logback.access.servlet.TeeFilter;
import io.wiretap.util.HttpBodyUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;

import java.io.IOException;

/**
 * Drop-in replacement for the logback-access {@link TeeFilter} that skips teeing
 * (request-body buffering) for content types whose stream must not be drained, or
 * is pointless to buffer. The stock {@code TeeFilter} eagerly reads the whole
 * request body into a buffer for everything except {@code form-urlencoded}; for
 * {@code multipart/form-data} that drains the stream before the controller can call
 * {@code request.getParts()} / read {@code @RequestPart}, so file uploads break.
 * <p>
 * For the types reported by {@link HttpBodyUtils#shouldBypassTeeBuffering} (all
 * {@code multipart/*} plus binary / streaming) this filter passes the original
 * request straight down the chain, leaving the stream intact. Everything else is
 * teed exactly as before by delegating to {@code super}.
 */
public class ContentTypeAwareTeeFilter extends TeeFilter {

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest && shouldBypass(httpRequest)) {
            chain.doFilter(request, response);
        } else {
            super.doFilter(request, response, chain);
        }
    }

    private boolean shouldBypass(final HttpServletRequest request) {
        final String contentType = request.getContentType();
        if (contentType == null) {
            return false;
        }
        try {
            return HttpBodyUtils.shouldBypassTeeBuffering(MediaType.parseMediaType(contentType));
        } catch (Exception e) {
            // Unparseable Content-Type — fall back to teeing as usual.
            return false;
        }
    }
}
