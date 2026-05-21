package io.wiretap.applog.provider;

import ch.qos.logback.classic.spi.ILoggingEvent;
import tools.jackson.core.JsonGenerator;

import java.io.IOException;

/**
 * SPI for plugging arbitrary fields into the JSON application log output
 * (i.e. regular {@code log.info(...)}, {@code log.error(...)}, etc.).
 * <p>
 * Register a Spring bean implementing this interface and Wiretap will include
 * its output in every application log entry, alongside the built-in fields.
 * <p>
 * Most providers only need to override {@link #fieldName()} and
 * {@link #value(ILoggingEvent)} — the default {@link #writeTo} skips
 * {@code null} values and emits the field via Jackson's {@code writeObject}.
 * Override {@link #writeTo} directly for full control over serialisation
 * (multiple fields, nested objects, raw JSON, etc.).
 *
 * <h3>Example: add a {@code tenant_id} field from MDC</h3>
 * <pre>
 * &#64;Component
 * public class TenantIdLogFieldProvider implements WiretapLogFieldProvider {
 *     &#64;Override public String fieldName() { return "tenant_id"; }
 *     &#64;Override public Object value(ILoggingEvent event) {
 *         return event.getMDCPropertyMap().get("tenant-id");
 *     }
 * }
 * </pre>
 */
public interface WiretapLogFieldProvider {

    /** JSON field name produced by this provider. */
    String fieldName();

    /**
     * Computes the field value for the current event.
     * Return {@code null} to skip the field for this event.
     */
    default Object value(ILoggingEvent event) {
        return null;
    }

    /**
     * Writes this provider's contribution to the JSON output.
     * The default implementation calls {@link #value(ILoggingEvent)} and emits
     * a single field, skipping the field entirely when the value is {@code null}.
     */
    default void writeTo(JsonGenerator generator, ILoggingEvent event) {
        Object v = value(event);
        if (v == null) {
            return;
        }
        generator.writeName(fieldName());
        generator.writePOJO(v);
    }
}
