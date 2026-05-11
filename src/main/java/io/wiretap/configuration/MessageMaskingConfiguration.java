package io.wiretap.configuration;

import io.wiretap.applog.message.handler.MessageMaskingHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(value = "wiretap.message-masking", havingValue = "true", matchIfMissing = true)
public class MessageMaskingConfiguration {

    @Bean
    @ConditionalOnBean(MessageMaskingHandler.class)
    public MessageMaskingInit messageMaskingInit(MessageMaskingHandler messageMaskingHandler) {
        return new MessageMaskingInit(messageMaskingHandler);
    }
}
