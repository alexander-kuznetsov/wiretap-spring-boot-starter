package io.wiretap.http.incoming.filter;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

import static io.wiretap.http.incoming.SleuthCorrelationId.SLEUTH_SPAN_ID;
import static io.wiretap.http.incoming.SleuthCorrelationId.SLEUTH_TRACE_ID;

/**
 * Forwards the active trace/span ids (provided by Spring Cloud Sleuth or
 * Micrometer Tracing) into request attributes so that the access-log providers
 * {@link io.wiretap.http.incoming.provider.trace.TraceIdProvider} and
 * {@link io.wiretap.http.incoming.provider.trace.SpanIdProvider} can pick them
 * up. The resulting access logs share trace ids with regular application logs.
 * <p>
 * This dance is necessary because logback-access does not have direct access to
 * the MDC context where tracing libraries normally store these values.
 */
@Slf4j
public class AccessLogTraceIdForwarder extends OncePerRequestFilter {

    private final Tracer tracer;

    public AccessLogTraceIdForwarder(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        setTraceContextToRequestAttributes(request);
        filterChain.doFilter(request, response);
    }

    private void setTraceContextToRequestAttributes(ServletRequest request) {
        try {
            // Stores Spring Cloud Sleuth attributes for logging by wiretap
            Optional.ofNullable(tracer.currentTraceContext().context())
                    .ifPresent(traceContext -> {
                        request.setAttribute(SLEUTH_TRACE_ID.getAttributeName(), traceContext.traceId());
                        request.setAttribute(SLEUTH_SPAN_ID.getAttributeName(), traceContext.spanId());
                    });

        } catch (Exception e) {
            log.error("Error while binding traces to request...", e);
        }
    }
}
