package io.wiretap.http.message.settings;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wiretap.web-client-interceptor")
public class WebClientLogMessageSettings extends RestLogMessageSettings {
}
