package io.wiretap.configuration;

import io.wiretap.http.incoming.provider.trace.SpanIdProvider;
import io.wiretap.http.incoming.provider.trace.TraceIdProvider;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configurable JSON field names for all Wiretap-emitted log events.
 * All names default to the original Wiretap schema.
 *
 * <pre>
 * wiretap:
 *   fields:
 *     http-info: http_info       # rename the outer wrapper field
 *     http:
 *       duration: elapsed_ms     # rename a field inside http_info
 *       return-code: status
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "wiretap.fields")
@Data
public class WiretapFieldNamesProperties {

    /** Top-level timestamp field (default: {@code @timestamp}). */
    private String timestamp = "@timestamp";
    private String env = "env";
    private String system = "system";
    private String instance = "inst";
    private String lbTraceId = "lb_trace_id";
    private String traceId = "trace_id";
    private String spanId = "span_id";
    private String level = "level";
    private String message = "message";
    private String httpInfo = "http_info";

    /** Names of fields nested inside the {@code http_info} object. */
    private HttpAccessFieldNames http = new HttpAccessFieldNames();

    @PostConstruct
    public void apply() {
        TraceIdProvider.configureFieldName(traceId);
        SpanIdProvider.configureFieldName(spanId);
    }
}
