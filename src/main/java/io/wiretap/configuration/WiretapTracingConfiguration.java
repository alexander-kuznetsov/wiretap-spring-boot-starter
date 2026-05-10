package io.wiretap.configuration;

import brave.Tracing;
import brave.propagation.B3Propagation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WiretapTracingConfiguration {

    /** Enables legacy 64-bit B3 single-header trace-id format for backward compatibility. */
    @ConditionalOnProperty(name = "wiretap.tracing.propagation.type.b3.enabled", havingValue = "true", matchIfMissing = true)
    @Bean
    public Tracing braveTracing() {
        return Tracing.newBuilder()
                .propagationFactory(B3Propagation.newFactoryBuilder()
                        .injectFormat(B3Propagation.Format.SINGLE_NO_PARENT)
                        .build())
                .build();
    }
}
