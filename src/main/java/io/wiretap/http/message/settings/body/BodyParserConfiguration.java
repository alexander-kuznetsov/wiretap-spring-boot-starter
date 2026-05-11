package io.wiretap.http.message.settings.body;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class BodyParserConfiguration {

    @ConditionalOnMissingBean
    @Bean
    public BodyParser bodyParser(@Autowired Optional<HttpBodyMaskingHandler> maskingHandler) {
        return new DefaultBodyParser(maskingHandler.orElse(null));
    }
}
