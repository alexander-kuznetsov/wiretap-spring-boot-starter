package io.wiretap.applog.message;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.wiretap.applog.message.handler.MessageMaskingHandler;

/**
 * Custom logback converter that applies optional masking and truncation to the
 * {@code message} field of regular log events (i.e. messages produced by manual
 * {@code log.info(...)} / {@code log.warn(...)} calls).
 */
public class LazyCustomMaskingMessageConverter extends MessageConverter {

    private static volatile MessageMaskingHandler handler;

    /** Called by {@link io.wiretap.configuration.MessageMaskingInit} on Spring startup. */
    public static void setHandler(MessageMaskingHandler h) {
        handler = h;
    }

    private static final int MAX_LENGTH = 7000;
    private static final String INFO = "...truncated. Message size exceeds limit!";

    @Override
    public String convert(final ILoggingEvent event) {
        final String message = event.getFormattedMessage();
        if (message != null && message.length() > MAX_LENGTH) {
            final String truncatedMessage = message.substring(0, MAX_LENGTH - INFO.length());
            return String.format("%s %s", truncatedMessage, INFO);
        }

        if (handler != null) {
            return handler.maskMessage(message);
        }
        return message;
    }
}
