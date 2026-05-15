package io.wiretap.applog.provider;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wiretap.applog.message.handler.MessageMaskingHandler;
import io.wiretap.configuration.WiretapAppLogProperties;
import jakarta.annotation.PostConstruct;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;

/**
 * Spring-managed provider that writes all standard Wiretap fields to every
 * application log entry. Field names and visibility are controlled via
 * {@link WiretapAppLogProperties} ({@code wiretap.app-log.*}).
 * <p>
 * On startup the bean registers itself into {@link LazyStandardLogFieldsProvider}
 * so Logback can call it once the Spring context is ready.
 */
public class WiretapStandardLogFieldsProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WiretapAppLogProperties props;
    @Nullable
    private final MessageMaskingHandler maskingHandler;
    private final String activeProfile;
    private final String appName;
    private final String hostname;

    public WiretapStandardLogFieldsProvider(
            WiretapAppLogProperties props,
            @Nullable MessageMaskingHandler maskingHandler,
            @Value("${spring.profiles.active:}") String activeProfile,
            @Value("${spring.application.name:}") String appName,
            @Value("${HOSTNAME:}") String hostname
    ) {
        this.props = props;
        this.maskingHandler = maskingHandler;
        this.activeProfile = activeProfile;
        this.appName = appName;
        this.hostname = hostname;
    }

    @PostConstruct
    public void register() {
        LazyStandardLogFieldsProvider.setProvider(this);
    }

    public void writeTo(JsonGenerator gen, ILoggingEvent event) throws IOException {
        WiretapAppLogProperties.Fields f = props.getFields();

        if (props.isVisible(AppLogField.TIMESTAMP)) {
            gen.writeStringField(f.getTimestamp(),
                    formatTimestamp(event.getTimeStamp()));
        }
        if (props.isVisible(AppLogField.ENV) && !activeProfile.isEmpty()) {
            gen.writeStringField(f.getEnv(), activeProfile);
        }
        if (props.isVisible(AppLogField.SYSTEM) && !appName.isEmpty()) {
            gen.writeStringField(f.getSystem(), appName);
        }
        if (props.isVisible(AppLogField.INSTANCE) && !hostname.isEmpty()) {
            gen.writeStringField(f.getInstance(), hostname);
        }

        String traceId = mdcValue(event, "traceId");
        if (props.isVisible(AppLogField.TRACE_ID) && traceId != null) {
            gen.writeStringField(f.getTraceId(), traceId);
        }
        String spanId = mdcValue(event, "spanId");
        if (props.isVisible(AppLogField.SPAN_ID) && spanId != null) {
            gen.writeStringField(f.getSpanId(), spanId);
        }

        if (props.isVisible(AppLogField.LEVEL)) {
            gen.writeStringField(f.getLevel(), event.getLevel().toString());
        }
        if (props.isVisible(AppLogField.THREAD_NAME)) {
            gen.writeStringField(f.getThreadName(), event.getThreadName());
        }
        if (props.isVisible(AppLogField.LOGGER_NAME)) {
            gen.writeStringField(f.getLoggerName(), event.getLoggerName());
        }

        boolean needCaller = props.isVisible(AppLogField.CALLER_CLASS)
                || props.isVisible(AppLogField.CALLER_METHOD)
                || props.isVisible(AppLogField.CALLER_LINE)
                || props.isVisible(AppLogField.CALLER_FILE);
        if (needCaller) {
            StackTraceElement[] callerData = event.getCallerData();
            if (callerData != null && callerData.length > 0) {
                StackTraceElement caller = callerData[0];
                if (props.isVisible(AppLogField.CALLER_CLASS)) {
                    gen.writeStringField(f.getCallerClass(), caller.getClassName());
                }
                if (props.isVisible(AppLogField.CALLER_METHOD)) {
                    gen.writeStringField(f.getCallerMethod(), caller.getMethodName());
                }
                if (props.isVisible(AppLogField.CALLER_LINE)) {
                    gen.writeNumberField(f.getCallerLine(), caller.getLineNumber());
                }
                if (props.isVisible(AppLogField.CALLER_FILE)) {
                    gen.writeStringField(f.getCallerFile(), caller.getFileName());
                }
            }
        }

        if (props.isVisible(AppLogField.MESSAGE)) {
            String msg = event.getFormattedMessage();
            if (msg != null && maskingHandler != null) {
                msg = maskingHandler.maskMessage(msg);
            }
            gen.writeStringField(f.getMessage(), msg);
        }

        if (props.isVisible(AppLogField.HTTP_INFO)) {
            writeJsonMdcField(gen, f.getHttpInfo(), event, "HTTP-REQUEST-LOG");
        }
        if (props.isVisible(AppLogField.KAFKA_INFO)) {
            writeJsonMdcField(gen, f.getKafkaInfo(), event, "KAFKA-MESSAGE-LOG");
        }
        if (props.isVisible(AppLogField.EXTRA)) {
            writeJsonMdcField(gen, f.getExtra(), event, "LOG_EXTRA");
        }
    }

    private static String mdcValue(ILoggingEvent event, String key) {
        String v = event.getMDCPropertyMap().get(key);
        return (v == null || v.isEmpty()) ? null : v;
    }

    private static void writeJsonMdcField(JsonGenerator gen, String fieldName,
                                          ILoggingEvent event, String mdcKey) throws IOException {
        String raw = event.getMDCPropertyMap().get(mdcKey);
        if (raw == null || raw.isEmpty()) {
            return;
        }
        try {
            Object parsed = MAPPER.readValue(raw, Object.class);
            gen.writeFieldName(fieldName);
            gen.writeObject(parsed);
        } catch (Exception ignored) {
            // not valid JSON — skip the field rather than write garbage
        }
    }

    private static String formatTimestamp(long epochMillis) {
        return java.time.Instant.ofEpochMilli(epochMillis)
                .atOffset(java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
    }
}
