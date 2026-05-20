package io.wiretap.integrationtests.accessfield;

import ch.qos.logback.access.spi.IAccessEvent;
import io.wiretap.http.incoming.provider.WiretapAccessFieldProvider;
import org.springframework.stereotype.Component;

/**
 * Adds a {@code tenant} field to every access-log entry, sourced from the
 * {@code X-Demo-Tenant} request header. Normalizes the logback-access
 * "missing" marker {@code "-"} to {@code null} so the field is omitted
 * instead of emitted as {@code "tenant":"-"}.
 */
@Component
public class TenantAccessFieldProvider implements WiretapAccessFieldProvider {

    @Override
    public String fieldName() {
        return "tenant";
    }

    @Override
    public Object value(IAccessEvent event) {
        String raw = event.getRequestHeader("X-Demo-Tenant");
        if (raw == null || raw.isEmpty() || "-".equals(raw)) {
            return null;
        }
        return raw;
    }
}
