package io.wiretap.http.message.settings;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import io.wiretap.http.incoming.filter.LazyIncomingRequestLogFilter;

import java.util.Collection;
import java.util.Collections;

@Component
@ConfigurationProperties(prefix = "wiretap.rest-controllers")
public class RestControllerLogMessageSettings extends HttpInfoLogMessageSettings {

    /* Request URL patterns to skip from logging by default */
    @Getter
    @Setter
    private Collection<String> excludeRequestPatterns = Collections.singletonList("/actuator/.*");

    @PostConstruct
    public void init() {
        LazyIncomingRequestLogFilter.setSettings(this);
    }
}
