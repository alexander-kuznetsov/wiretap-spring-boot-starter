package io.wiretap.configuration;

import io.wiretap.http.incoming.provider.WiretapAccessFieldProvider;
import io.wiretap.http.incoming.provider.WiretapDelegatingFieldProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class WiretapAccessLogConfiguration {

    @Autowired
    public WiretapAccessLogConfiguration(List<WiretapAccessFieldProvider> providers) {
        WiretapDelegatingFieldProvider.setProviders(providers);
    }
}
