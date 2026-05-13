package io.wiretap.http.message;

/**
 * SPI for masking sensitive values in HTTP request query parameters.
 * <p>
 * Invoked once per {@code (name, value)} pair captured for the
 * {@code request_params} field, when {@code enable-request-params-masking=true}
 * is configured globally or for a specific URL pattern via
 * {@code specific-http-info-settings[].enable-request-params-masking}.
 * Register a Spring bean implementing this interface to activate masking.
 */
public interface HttpRequestParamsMaskingHandler {

    /**
     * @param name  query parameter name (e.g. {@code "phone"})
     * @param value original parameter value
     * @return masked value to be written to the log
     */
    String maskParamValue(String name, String value);
}
