package io.wiretap.configuration;

import io.wiretap.applog.message.handler.MessageMaskingHandler;
import io.wiretap.applog.provider.WiretapDelegatingLogFieldProvider;
import io.wiretap.applog.provider.WiretapLogFieldProvider;
import io.wiretap.applog.provider.WiretapStandardLogFieldsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@EnableConfigurationProperties(WiretapAppLogProperties.class)
public class WiretapAppLogConfiguration {

    @Autowired
    public WiretapAppLogConfiguration(List<WiretapLogFieldProvider> providers) {
        WiretapDelegatingLogFieldProvider.setProviders(providers);
    }

    @Bean
    public WiretapStandardLogFieldsProvider wiretapStandardLogFieldsProvider(
            WiretapAppLogProperties props,
            MessageMaskingHandler maskingHandler,
            @Value("${spring.profiles.active:}") String activeProfile,
            @Value("${spring.application.name:}") String appName,
            @Value("${HOSTNAME:}") String hostname
    ) {
        return new WiretapStandardLogFieldsProvider(props, maskingHandler, activeProfile, appName, hostname);
    }
}
