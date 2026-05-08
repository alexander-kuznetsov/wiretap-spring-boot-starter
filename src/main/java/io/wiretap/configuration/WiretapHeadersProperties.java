package io.wiretap.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configuration of header names recognised by Wiretap.
 * <p>
 * Override the defaults via {@code application.yml} when your infrastructure uses
 * different header conventions:
 *
 * <pre>
 * wiretap:
 *   headers:
 *     forward-to-mdc:
 *       - x-request-id
 *       - x-session-key
 *       - eKassir-PointID
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "wiretap.headers")
@Data
public class WiretapHeadersProperties {

    /**
     * Names of inbound request headers that should be copied into MDC under the same key.
     * Used by {@link io.wiretap.http.incoming.interceptor.CorrelationHeadersMdcForwarder}.
     */
    private List<String> forwardToMdc = List.of("x-request-id", "x-session-key", "lb-trace-id");
}
