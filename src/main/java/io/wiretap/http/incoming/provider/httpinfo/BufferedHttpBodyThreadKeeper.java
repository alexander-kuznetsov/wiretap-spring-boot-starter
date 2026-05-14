package io.wiretap.http.incoming.provider.httpinfo;

import io.wiretap.http.message.BufferedHttpMessageInfo;

/**
 * Thread-local buffer for HTTP request/response bodies that have to survive across
 * filter and exception-resolver boundaries before they reach the logback-access
 * encoder.
 *
 * <p>Standard logback-access body capture (via {@code TeeFilter}) does not see the
 * body when the filter chain is interrupted, when a custom {@code ContentCachingResponseWrapper}
 * is used, or when Spring Security writes the response after the original output
 * stream is no longer tracked.
 *
 * <p>To work around this, the user code (typically in a filter or exception resolver)
 * stashes the request/response body here, and the access-log provider reads it back
 * later when the encoder runs. The thread-local is reset by
 * {@link io.wiretap.http.incoming.filter.BufferedHttpBodyThreadCleaner} on every
 * request.
 *
 * <p>Example usage from a filter or exception resolver:
 * <pre>
 * BufferedHttpBodyThreadKeeper.put(new BufferedHttpMessageInfo(
 *         requestBody, requestBody.length(),
 *         responseBody, responseBody.length()));
 * </pre>
 */
public class BufferedHttpBodyThreadKeeper {

    private static final ThreadLocal<BufferedHttpMessageInfo> BUFFERED_HTTP_MESSAGE_INFO = new ThreadLocal<>();

    public static void put(BufferedHttpMessageInfo bufferedHttpMessageInfo) {
        BUFFERED_HTTP_MESSAGE_INFO.set(bufferedHttpMessageInfo);
    }

    public static BufferedHttpMessageInfo get() {
        return BUFFERED_HTTP_MESSAGE_INFO.get();
    }

    public static String getRequestBody() {
        BufferedHttpMessageInfo bufferedHttpMessageInfo = BUFFERED_HTTP_MESSAGE_INFO.get();
        if (bufferedHttpMessageInfo != null) {
            return bufferedHttpMessageInfo.requestBody();
        }
        return null;
    }

    public static long getRequestBodyLength() {
        BufferedHttpMessageInfo bufferedHttpMessageInfo = BUFFERED_HTTP_MESSAGE_INFO.get();
        if (bufferedHttpMessageInfo != null) {
            return bufferedHttpMessageInfo.requestBodyLength();
        }
        return 0;
    }

    public static String getResponseBody() {
        BufferedHttpMessageInfo bufferedHttpMessageInfo = BUFFERED_HTTP_MESSAGE_INFO.get();
        if (bufferedHttpMessageInfo != null) {
            return bufferedHttpMessageInfo.responseBody();
        }
        return null;
    }

    public static long getResponseBodyLength() {
        BufferedHttpMessageInfo bufferedHttpMessageInfo = BUFFERED_HTTP_MESSAGE_INFO.get();
        if (bufferedHttpMessageInfo != null) {
            return bufferedHttpMessageInfo.responseBodyLength();
        }
        return 0;
    }

    public static void clear() {
        BUFFERED_HTTP_MESSAGE_INFO.remove();
    }
}
