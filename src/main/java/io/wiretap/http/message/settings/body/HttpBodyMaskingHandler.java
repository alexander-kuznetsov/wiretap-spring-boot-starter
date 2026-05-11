package io.wiretap.http.message.settings.body;

/**
 * SPI for masking sensitive values inside HTTP request and response bodies.
 * <p>
 * Invoked once per text field value in JSON and XML bodies when
 * {@code http-body-settings.enable-body-masking=true} is configured.
 * Register a Spring bean implementing this interface to activate body masking.
 * <p>
 * Per-URL control is available through
 * {@code specific-http-info-settings[].http-body-settings.enable-body-masking}.
 */
public interface HttpBodyMaskingHandler {

    /**
     * @param fieldValue raw text value of a JSON or XML field
     * @return masked value to be written to the log
     */
    String maskBodyField(String fieldValue);
}
