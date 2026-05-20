package io.wiretap.integrationtests.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.springframework.boot.test.system.CapturedOutput;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Parses JSON log lines out of {@link CapturedOutput}. Wiretap emits one JSON
 * object per event to stdout (when {@code pretty-print: false}); we treat any
 * line that begins with {@code "{"} as a candidate and round-trip it through
 * Jackson.
 */
public final class JsonLogCapture {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private JsonLogCapture() {}

    public static List<Map<String, Object>> all(CapturedOutput out) {
        return out.getAll().lines()
            .map(String::trim)
            .filter(line -> line.startsWith("{"))
            .map(JsonLogCapture::tryParse)
            .filter(Objects::nonNull)
            .toList();
    }

    public static List<Map<String, Object>> httpInfo(CapturedOutput out) {
        return all(out).stream().filter(m -> m.containsKey("http_info")).toList();
    }

    public static List<Map<String, Object>> kafkaInfo(CapturedOutput out) {
        return all(out).stream().filter(m -> m.containsKey("kafka_info")).toList();
    }

    public static Map<String, Object> awaitMatching(CapturedOutput out,
                                                    Predicate<Map<String, Object>> predicate) {
        return Awaitility.await()
            .atMost(DEFAULT_TIMEOUT)
            .pollInterval(Duration.ofMillis(100))
            .until(
                () -> all(out).stream().filter(predicate).findFirst().orElse(null),
                Objects::nonNull
            );
    }

    /**
     * Navigates a dotted path through nested maps. Returns {@code null} on any
     * missing or non-map segment — callers assert presence explicitly.
     */
    @SuppressWarnings("unchecked")
    public static <T> T at(Map<String, Object> log, String path) {
        Object current = log;
        for (String key : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(key);
        }
        return (T) current;
    }

    private static Map<String, Object> tryParse(String line) {
        try {
            return MAPPER.readValue(line, new TypeReference<>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
