package io.wiretap.applog.message.handler;

public interface MessageMaskingHandler {

    /**
     * Defines the masking logic applied to the {@code message} field of log events.
     * To plug in custom masking, register a Spring bean implementing this interface;
     * it will be wired into the message converter during configuration.
     *
     * @param message original {@code message} field value from the log event
     * @return the masked value to be written to the log
     */
    String maskMessage(String message);
}
