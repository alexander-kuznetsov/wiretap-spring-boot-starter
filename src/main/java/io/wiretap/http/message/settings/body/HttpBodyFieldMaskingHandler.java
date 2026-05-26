package io.wiretap.http.message.settings.body;

/**
 * Per-field body-masking SPI. Invoked once per text leaf value in JSON
 * and XML request/response bodies when
 * {@code http-body-settings.enable-body-masking=true} is configured.
 * Register a Spring bean implementing this interface to activate
 * field-level masking; no URL context is provided — the same
 * transformation is applied to every leaf value of every body.
 *
 * <p>For structural (per-URL, per-payload) masking that needs to see
 * the entire JSON tree, see the sibling SPI
 * {@link HttpBodyMaskingHandler}; the two compose — structural runs
 * first, then this field-handler runs over the result if registered.
 *
 * <p>Per-URL control of the {@code enable-body-masking} flag is
 * available through
 * {@code specific-http-info-settings[].http-body-settings.enable-body-masking}.
 */
public interface HttpBodyFieldMaskingHandler {

    /**
     * @param fieldValue raw text value of a JSON or XML field
     * @return masked value to be written to the log
     */
    String maskBodyField(String fieldValue);
}
