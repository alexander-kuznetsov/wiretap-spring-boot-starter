package io.wiretap.configuration;

import io.wiretap.applog.provider.WiretapDelegatingLogFieldProvider;
import io.wiretap.applog.provider.WiretapLogFieldProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wires every {@link WiretapLogFieldProvider} bean into
 * {@link WiretapDelegatingLogFieldProvider} during Spring startup so the
 * logback appenders can fan out events to user-supplied providers.
 */
@Configuration
public class WiretapLogFieldProvidersInit {

    @Autowired
    public WiretapLogFieldProvidersInit(List<WiretapLogFieldProvider> providers) {
        WiretapDelegatingLogFieldProvider.setProviders(providers);
    }
}
