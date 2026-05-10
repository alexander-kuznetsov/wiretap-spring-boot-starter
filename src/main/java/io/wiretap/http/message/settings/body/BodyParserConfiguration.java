package io.wiretap.http.message.settings.body;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BodyParserConfiguration {

    @ConditionalOnMissingBean
    @Bean
    public BodyParser bodyParser() {
        return new DefaultBodyParser();
    }
}
