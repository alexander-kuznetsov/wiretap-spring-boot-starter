package io.wiretap.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.wiretap.applog.message.handler.DefaultMessageMaskingHandler;
import io.wiretap.applog.message.handler.MessageMaskingHandler;

@Configuration
@ConditionalOnProperty(value = "wiretap.message-masking", havingValue = "true", matchIfMissing = true)
public class MessageMaskingConfiguration {

    @ConditionalOnMissingBean
    @Bean
    public MessageMaskingHandler messageMaskingHandler() {
        return new DefaultMessageMaskingHandler();
    }

    @Bean
    public MessageMaskingInit messageMaskingInit(MessageMaskingHandler messageMaskingHandler) {
        return new MessageMaskingInit(messageMaskingHandler);
    }
}
