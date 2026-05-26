package io.wiretap.http.message.settings.body;

import io.wiretap.metrics.WiretapMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Optional;

@Configuration
public class BodyParserConfiguration {

    @ConditionalOnMissingBean
    @Bean
    public BodyParser bodyParser(
            @Autowired Optional<HttpBodyFieldMaskingHandler> fieldMaskingHandler,
            @Autowired List<HttpBodyMaskingHandler> bodyMaskingHandlers,
            @Autowired WiretapMetrics metrics
    ) {
        return new DefaultBodyParser(fieldMaskingHandler.orElse(null), bodyMaskingHandlers, metrics);
    }
}
