package io.wiretap.configuration;

import io.wiretap.applog.message.handler.MessageMaskingHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessageMaskingConfiguration {

    @Bean
    @ConditionalOnBean(MessageMaskingHandler.class)
    public MessageMaskingInit messageMaskingInit(MessageMaskingHandler messageMaskingHandler) {
        return new MessageMaskingInit(messageMaskingHandler);
    }
}
