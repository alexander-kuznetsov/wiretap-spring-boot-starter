package io.wiretap.applog.provider;

public enum AppLogField {
    TIMESTAMP,
    ENV,
    SYSTEM,
    INSTANCE,
    TRACE_ID,
    SPAN_ID,
    LEVEL,
    THREAD_NAME,
    LOGGER_NAME,
    CALLER_CLASS,
    CALLER_METHOD,
    CALLER_LINE,
    CALLER_FILE,
    MESSAGE,
    HTTP_INFO,
    KAFKA_INFO,
    EXTRA
}
