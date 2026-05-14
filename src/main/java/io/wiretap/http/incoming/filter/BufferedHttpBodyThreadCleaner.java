package io.wiretap.http.incoming.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.filter.OncePerRequestFilter;
import io.wiretap.http.incoming.provider.httpinfo.BufferedHttpBodyThreadKeeper;

import java.io.IOException;

public class BufferedHttpBodyThreadCleaner extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        BufferedHttpBodyThreadKeeper.clear();
        filterChain.doFilter(request, response);
    }
}
