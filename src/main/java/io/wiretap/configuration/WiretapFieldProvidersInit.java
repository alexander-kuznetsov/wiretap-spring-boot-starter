package io.wiretap.configuration;

import io.wiretap.http.incoming.provider.WiretapAccessFieldProvider;
import io.wiretap.http.incoming.provider.WiretapDelegatingFieldProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wires every {@link WiretapAccessFieldProvider} bean into
 * {@link WiretapDelegatingFieldProvider} during Spring startup so the
 * logback-access appender can fan out events to user-supplied providers.
 */
@Configuration
public class WiretapFieldProvidersInit {

    @Autowired
    public WiretapFieldProvidersInit(List<WiretapAccessFieldProvider> providers) {
        WiretapDelegatingFieldProvider.setProviders(providers);
    }
}
