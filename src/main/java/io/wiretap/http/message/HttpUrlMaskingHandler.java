package io.wiretap.http.message;

/**
 * SPI for masking sensitive data in request URLs (path and query string).
 * <p>
 * Invoked on the full URL string when {@code enable-url-masking=true} is
 * configured globally or for a specific URL pattern via
 * {@code specific-http-info-settings[].enable-url-masking}.
 * Register a Spring bean implementing this interface to activate URL masking.
 */
public interface HttpUrlMaskingHandler {

    /**
     * @param url full request URL including path and query string
     * @return masked URL to be written to the log
     */
    String maskUrl(String url);
}
