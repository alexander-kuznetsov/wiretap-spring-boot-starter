package io.wiretap.configuration;

import io.wiretap.applog.provider.AppLogField;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "wiretap.app-log")
@Data
public class WiretapAppLogProperties {

    private Fields fields = new Fields();

    /**
     * Per-field visibility toggles. Keys are {@link AppLogField} names (e.g. {@code CALLER_CLASS}).
     * When a key is absent the field is visible by default.
     * Caller-data fields default to OFF because they require a stack-trace capture.
     */
    private Map<String, Boolean> visibilitySettings = new HashMap<>();

    public boolean isVisible(AppLogField field) {
        return visibilitySettings.getOrDefault(field.name(), defaultVisibility(field));
    }

    private static boolean defaultVisibility(AppLogField field) {
        return switch (field) {
            case CALLER_CLASS, CALLER_METHOD, CALLER_LINE, CALLER_FILE -> false;
            default -> true;
        };
    }

    @Data
    public static class Fields {
        private String timestamp = "@timestamp";
        private String env = "env";
        private String system = "system";
        private String instance = "inst";
        private String traceId = "trace_id";
        private String spanId = "span_id";
        private String level = "level";
        private String threadName = "thread_name";
        private String loggerName = "logger";
        private String callerClass = "caller_class";
        private String callerMethod = "caller_method";
        private String callerLine = "caller_line";
        private String callerFile = "caller_file";
        private String message = "message";
        private String httpInfo = "http_info";
        private String extra = "extra";
    }
}
