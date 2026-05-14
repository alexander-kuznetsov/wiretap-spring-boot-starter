package io.wiretap.http;

/**
 * Default set of correlation headers that are forwarded into MDC for inbound requests.
 * Override the values via {@link io.wiretap.http.message.settings.HttpInfoLogMessageSettings}
 * if your infrastructure uses different header names.
 */
public enum CorrelationHeaders {

    USER_SESSION_KEY("x-session-key"),
    X_REQUEST_ID("x-request-id");

    private final String headerName;

    CorrelationHeaders(String headerName) {
        this.headerName = headerName;
    }

    @Override
    public String toString() {
        return headerName;
    }
}
