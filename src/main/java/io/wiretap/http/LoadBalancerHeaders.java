package io.wiretap.http;

/**
 * Default set of headers expected to be injected by an upstream load balancer
 * for end-to-end request correlation. Override values if your LB uses different names.
 */
public enum LoadBalancerHeaders {

    LB_TRACE_ID("lb-trace-id"),
    USER_SESSION_KEY("x-session-key");

    private final String headerName;

    LoadBalancerHeaders(String headerName) {
        this.headerName = headerName;
    }

    @Override
    public String toString() {
        return headerName;
    }
}
