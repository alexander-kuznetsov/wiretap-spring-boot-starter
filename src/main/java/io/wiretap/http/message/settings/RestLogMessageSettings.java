package io.wiretap.http.message.settings;

import lombok.Getter;
import lombok.Setter;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
public class RestLogMessageSettings extends HttpInfoLogMessageSettings {
    /* Request URL patterns to skip from logging by default */
    private Collection<String> excludeRequestPatterns = Collections.emptyList();
    private List<AdditionalRequestHeaders> additionalRequestHeaders = Collections.emptyList();
}
