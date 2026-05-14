package io.wiretap.configuration;

import io.wiretap.applog.message.LazyCustomMaskingMessageConverter;
import io.wiretap.applog.message.handler.MessageMaskingHandler;

/**
 * Injects the active {@link MessageMaskingHandler} bean into
 * {@link LazyCustomMaskingMessageConverter} during Spring startup so that
 * the logback converter can call user-defined masking logic.
 */
public class MessageMaskingInit {

    public MessageMaskingInit(MessageMaskingHandler messageMaskingHandler) {
        LazyCustomMaskingMessageConverter.handler = messageMaskingHandler;
    }
}
