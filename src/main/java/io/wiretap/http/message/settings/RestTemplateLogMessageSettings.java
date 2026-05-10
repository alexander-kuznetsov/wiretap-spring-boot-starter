package io.wiretap.http.message.settings;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wiretap.rest-template-interceptor")
public class RestTemplateLogMessageSettings extends RestLogMessageSettings {
}
