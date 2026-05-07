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

    private static volatile RestControllerLogMessageSettings httpInfoLogMessageSettings;

    /** Called by {@link io.wiretap.http.message.settings.RestControllerLogMessageSettings} on Spring startup. */
    public static void setSettings(RestControllerLogMessageSettings settings) {
        httpInfoLogMessageSettings = settings;
    }

    @Override
    public FilterReply decide(IAccessEvent iAccessEvent) {
        RestControllerLogMessageSettings settings = httpInfoLogMessageSettings;
        if (settings == null) {
            return FilterReply.NEUTRAL;
        }

        final String requestURL = iAccessEvent.getRequestURL();
        final boolean shouldSkip = settings.getExcludeRequestPatterns().stream()
                .anyMatch(requestURL::matches);

        return shouldSkip ? FilterReply.DENY : FilterReply.ACCEPT;
    }
}
