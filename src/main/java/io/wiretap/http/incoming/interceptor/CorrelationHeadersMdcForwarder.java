package io.wiretap.http.incoming.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;
import io.wiretap.http.CorrelationHeaders;
import io.wiretap.http.incoming.provider.operationinfo.ExtraRequestInfoContextKeeper;

import java.util.Optional;

/**
 * Forwards correlation header values from inbound requests into MDC so they are
 * available to log appenders. The set of headers is defined by {@link CorrelationHeaders}.
 */
public class CorrelationHeadersMdcForwarder implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            @NotNull final HttpServletRequest request,
            @NotNull final HttpServletResponse response,
            @NotNull final Object handler
    ) {
        ExtraRequestInfoContextKeeper.clear();
        addToMdc(CorrelationHeaders.values(), request);
        return true;
    }

    private <E extends Enum<E>> void addToMdc(
            final E[] enumArr,
            final HttpServletRequest request
    ) {
        for (E enumValue : enumArr) {
            final String headerName = enumValue.toString();
            Optional.ofNullable(request.getHeader(headerName))
                    .ifPresent(headerValue -> MDC.put(headerName, headerValue));
        }
    }
}
