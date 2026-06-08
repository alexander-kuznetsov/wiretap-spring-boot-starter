package io.wiretap.http.incoming.filter;

import io.wiretap.configuration.WiretapHeadersProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Forwards configured request headers from inbound traffic into MDC so they are
 * available to downstream log appenders. The header set is driven by
 * {@link WiretapHeadersProperties#getForwardToMdc()}.
 * <p>
 * Registered as a servlet filter at {@link org.springframework.core.Ordered#HIGHEST_PRECEDENCE}
 * so the correlation context is populated before any other filter (including
 * Spring Security and logging filters) runs — a Spring MVC {@code HandlerInterceptor}
 * would only see it after the whole filter chain, inside the {@code DispatcherServlet}.
 * MDC is cleared in a {@code finally} block at the end of every request so values
 * never leak across pooled request threads.
 */
public class CorrelationHeadersMdcFilter extends OncePerRequestFilter {

    private final List<String> headerNames;

    public CorrelationHeadersMdcFilter(WiretapHeadersProperties properties) {
        this.headerNames = properties.getForwardToMdc();
    }

    @Override
    protected void doFilterInternal(
            @NotNull final HttpServletRequest request,
            @NotNull final HttpServletResponse response,
            @NotNull final FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            for (String headerName : headerNames) {
                Optional.ofNullable(request.getHeader(headerName))
                        .ifPresent(headerValue -> MDC.put(headerName, headerValue));
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
