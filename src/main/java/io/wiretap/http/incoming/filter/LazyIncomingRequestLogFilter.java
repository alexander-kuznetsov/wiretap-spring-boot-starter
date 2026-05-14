package io.wiretap.http.incoming.filter;

import ch.qos.logback.access.spi.IAccessEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import io.wiretap.http.message.settings.RestControllerLogMessageSettings;

/**
 * Logback-access filter that fully suppresses logging for requests whose URL
 * matches one of the configured exclude regex patterns.
 */
public class LazyIncomingRequestLogFilter extends Filter<IAccessEvent> {

    /*
     * Initialisation happens in two steps:
     *   1. logback instantiates this filter while parsing its XML config (no Spring context yet),
     *   2. once the Spring context is up, the active settings bean is plugged into this static
     *      reference so the already-built filter can consult it at request time.
     */
    public static RestControllerLogMessageSettings httpInfoLogMessageSettings;

    @Override
    public FilterReply decide(IAccessEvent iAccessEvent) {
        if (httpInfoLogMessageSettings == null) {
            throw new RuntimeException("Missed RestControllerLogMessageSettings in request filter!");
        }

        final String requestURL = iAccessEvent.getRequestURL();
        final boolean shouldSkip = httpInfoLogMessageSettings.getExcludeRequestPatterns().stream()
                .anyMatch(requestURL::matches);

        if (shouldSkip) {
            return FilterReply.DENY;
        } else {
            return FilterReply.ACCEPT;
        }
    }
}
