package io.wiretap.http.incoming.provider.httpinfo;

import io.wiretap.http.message.BufferedHttpMessageInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.Nullable;

/**
 * Per-request buffer for HTTP request/response bodies that have to survive
 * across filter and exception-resolver boundaries before they reach the
 * logback-access encoder. Stored as a servlet request attribute, so the
 * buffer is naturally tied to the request lifecycle and works under both
 * platform and virtual threads.
 *
 * <p>Use this when the standard {@code TeeFilter} body capture misses the
 * body — typically when Spring Security writes the response after the
 * original output stream is no longer tracked, or when a custom
 * {@code ContentCachingResponseWrapper} is in play.
 *
 * <pre>
 * BufferedHttpBodyHolder.put(request, new BufferedHttpMessageInfo(
 *         requestBody, requestBody.length(),
 *         responseBody, responseBody.length()));
 * </pre>
 */
public final class BufferedHttpBodyHolder {

    static final String ATTRIBUTE = "io.wiretap.bufferedHttpBody";

    private BufferedHttpBodyHolder() {
    }

    public static void put(HttpServletRequest request, BufferedHttpMessageInfo info) {
        if (request != null) {
            request.setAttribute(ATTRIBUTE, info);
        }
    }

    @Nullable
    public static BufferedHttpMessageInfo get(@Nullable HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object v = request.getAttribute(ATTRIBUTE);
        return v instanceof BufferedHttpMessageInfo info ? info : null;
    }
}
