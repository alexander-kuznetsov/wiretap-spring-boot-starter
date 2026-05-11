package io.wiretap.http.incoming.interceptor;

import io.wiretap.configuration.WiretapHeadersProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Optional;

/**
 * Forwards configured request headers from inbound traffic into MDC so they are
 * available to downstream log appenders. The header set is driven by
 * {@link WiretapHeadersProperties#getForwardToMdc()}.
 */
public class CorrelationHeadersMdcForwarder implements HandlerInterceptor {

    private final List<String> headerNames;

    public CorrelationHeadersMdcForwarder(WiretapHeadersProperties properties) {
        this.headerNames = properties.getForwardToMdc();
    }

    @Override
    public boolean preHandle(
            @NotNull final HttpServletRequest request,
            @NotNull final HttpServletResponse response,
            @NotNull final Object handler
    ) {
        for (String headerName : headerNames) {
            Optional.ofNullable(request.getHeader(headerName))
                    .ifPresent(headerValue -> MDC.put(headerName, headerValue));
        }
        return true;
    }
}
