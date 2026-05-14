package io.wiretap.http.message.settings;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "wiretap.feign-client-interceptor")
public class FeignClientMessageSettings extends HttpInfoLogMessageSettings {
    /* Request URL patterns to skip from logging by default */
    @Getter
    @Setter
    private Collection<String> excludeRequestPatterns = Collections.emptyList();

    @Getter
    @Setter
    private List<AdditionalRequestHeaders> additionalRequestHeaders = Collections.emptyList();
}
