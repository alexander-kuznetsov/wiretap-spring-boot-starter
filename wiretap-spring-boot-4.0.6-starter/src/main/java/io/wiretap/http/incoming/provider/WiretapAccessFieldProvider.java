package io.wiretap.http.incoming.provider;

import ch.qos.logback.access.common.spi.IAccessEvent;
import tools.jackson.core.JsonGenerator;

import java.io.IOException;

/**
 * SPI for plugging arbitrary fields into the JSON access-log output.
 * <p>
 * Register a Spring bean implementing this interface and Wiretap will include
 * its output in every HTTP access log entry, alongside the built-in fields.
 * <p>
 * Most providers only need to override {@link #fieldName()} and
 * {@link #value(IAccessEvent)} — the default {@link #writeTo} skips
 * {@code null} values and emits the field via Jackson's {@code writeObject}.
 * Override {@link #writeTo} directly for full control over serialisation
 * (multiple fields, nested objects, raw JSON, etc.).
 *
 * <h3>Example: re-add an {@code atm_id} field sourced from a custom header</h3>
 * <pre>
 * &#64;Component
 * public class AtmIdFieldProvider implements WiretapAccessFieldProvider {
 *     &#64;Override public String fieldName() { return "atm_id"; }
 *     &#64;Override public Object value(IAccessEvent event) {
 *         String raw = event.getRequestHeaderMap().get("eKassir-PointID");
 *         return raw == null ? null : raw.replaceFirst("^0+", "");
 *     }
 * }
 * </pre>
 *
 * <h3>Watch out for the logback-access "missing" marker</h3>
 * {@link IAccessEvent#getRequestHeader(String)} follows the Apache
 * access-log convention and returns the literal string {@code "-"} when
 * the header is absent, not {@code null}. A naive
 * {@code return event.getRequestHeader("...")} will therefore emit
 * {@code "field": "-"} on every request that didn't send the header,
 * which looks like the provider broke even though it ran. Either go
 * through {@link IAccessEvent#getRequestHeaderMap()} (returns
 * {@code null} for missing keys), or normalize {@code "-"} to
 * {@code null} yourself in {@link #value(IAccessEvent)}.
 */
public interface WiretapAccessFieldProvider {

    /** JSON field name produced by this provider. */
    String fieldName();

    /**
     * Computes the field value for the current event.
     * Return {@code null} to skip the field for this event.
     */
    default Object value(IAccessEvent event) {
        return null;
    }

    /**
     * Writes this provider's contribution to the JSON output.
     * The default implementation calls {@link #value(IAccessEvent)} and emits
     * a single field, skipping the field entirely when the value is {@code null}.
     */
    default void writeTo(JsonGenerator generator, IAccessEvent event) {
        Object v = value(event);
        if (v == null) {
            return;
        }
        generator.writeName(fieldName());
        generator.writePOJO(v);
    }
}
