package io.wiretap.http.incoming.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import io.wiretap.http.incoming.provider.operationinfo.ExtraRequestInfoContextKeeper;

import java.io.IOException;

/**
 * Resets {@link ExtraRequestInfoContextKeeper} at the very start of every request.
 * <p>
 * Clearing at the {@code HandlerInterceptor} level is not enough: requests that
 * fail authorisation never reach the handler chain, so authorisation filters
 * (which run earlier) could otherwise pick up the previous request's context.
 */
public class ExtraRequestInfoThreadCleaner extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        ExtraRequestInfoContextKeeper.clear();
        filterChain.doFilter(request, response);
    }
}
