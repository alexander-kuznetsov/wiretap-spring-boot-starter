package io.wiretap.http.message.settings;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wiretap.rest-client-interceptor")
public class RestClientLogMessageSettings extends RestLogMessageSettings {
}
