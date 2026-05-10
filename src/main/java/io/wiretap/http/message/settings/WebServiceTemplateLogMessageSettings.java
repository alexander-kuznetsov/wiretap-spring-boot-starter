package io.wiretap.http.message.settings;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@ConfigurationProperties(prefix = "wiretap.web-service-template-interceptor")
public class WebServiceTemplateLogMessageSettings extends HttpInfoLogMessageSettings {
    /* Request URL patterns to skip from logging by default */
    @Getter
    @Setter
    private Collection<String> excludeRequestPatterns = Collections.emptyList();

    @Getter
    @Setter
    private List<AdditionalRequestHeaders> additionalRequestHeaders = Collections.emptyList();
}
