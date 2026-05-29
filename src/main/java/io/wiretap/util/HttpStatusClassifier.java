package io.wiretap.util;

/**
 * Maps an HTTP status code to the {@code outcome} / {@code status} tag values used
 * by the {@code wiretap.http.*} metrics. A negative status is treated as a sentinel
 * for "no response was received" (network failure / exception) and maps to
 * {@code exception}; callers that always hold a real status code never hit that
 * branch, so the same classifier is safe to share across incoming and outgoing.
 */
public final class HttpStatusClassifier {

    private HttpStatusClassifier() {
    }

    /** {@code success} / {@code client_error} / {@code server_error} / {@code other}, or {@code exception} for a negative status. */
    public static String outcome(int status) {
        if (status < 0) return "exception";
        if (status >= 200 && status < 400) return "success";
        if (status >= 400 && status < 500) return "client_error";
        if (status >= 500 && status < 600) return "server_error";
        return "other";
    }

    /** Grouped status bucket {@code 2xx} / {@code 3xx} / {@code 4xx} / {@code 5xx} / {@code other}, or {@code exception} for a negative status. */
    public static String statusGroup(int status) {
        if (status < 0) return "exception";
        if (status >= 200 && status < 300) return "2xx";
        if (status >= 300 && status < 400) return "3xx";
        if (status >= 400 && status < 500) return "4xx";
        if (status >= 500 && status < 600) return "5xx";
        return "other";
    }
}
