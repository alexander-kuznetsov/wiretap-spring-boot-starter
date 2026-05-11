package io.wiretap.applog.extra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Utility for managing additional (extra) structured logging fields via SLF4J MDC.
 * <p>
 * All extra fields are stored in MDC under a single {@code LOG_EXTRA} key as a JSON
 * string and are emitted in the log output as a nested {@code extra} object.
 * <p>
 * The context is bound to the current thread.
 * <p>
 * <b>Important:</b>
 * <ul>
 *   <li>MDC is not propagated automatically across threads
 *       (e.g. {@code @Async}, {@code CompletableFuture}). Copy and restore MDC manually
 *       or use a {@code TaskDecorator} when crossing thread boundaries.</li>
 *   <li>Extra fields are written only to regular logback application logs;
 *       they do not appear in HTTP access logs handled by logback-access.</li>
 * </ul>
 */
@Slf4j
public final class ExtraAppLogContextKeeper {
    private static final String MDC_KEY = "LOG_EXTRA";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ExtraAppLogContextKeeper() {}

    /**
     * Adds or updates an extra field in the current thread's logging context.
     * <p>
     * The field is written to MDC and will appear as part of the {@code extra}
     * object in subsequent log events. If serialization fails, MDC is left unchanged.
     *
     * @param key   extra field name (must not be {@code null})
     * @param value extra field value (will be serialized as JSON)
     */
    public static void putExtraField(@NotNull String key, String value) {
        Map<String, String> extraParamsMap = getMdcExtraParamsMap();
        extraParamsMap.put(key, value);
        getExtraParamsAsJsonString(extraParamsMap)
                .ifPresent(extraParams -> MDC.put(MDC_KEY, extraParams));
    }

    /**
     * Removes a single extra field from the current thread's logging context.
     * <p>
     * If the removed entry was the last one, the {@code LOG_EXTRA} key is
     * removed from MDC entirely. On unexpected failures the whole extra
     * context is cleared so logging itself is not disrupted.
     *
     * @param key extra field name to remove
     */
    public static void removeExtraField(String key) {
        Map<String, String> extraParamsMap = getMdcExtraParamsMap();
        extraParamsMap.remove(key);
        if (extraParamsMap.isEmpty()) {
            MDC.remove(MDC_KEY);
            return;
        }
        getExtraParamsAsJsonString(extraParamsMap)
                .ifPresentOrElse(
                        extraParams -> MDC.put(MDC_KEY, extraParams),
                        () -> MDC.remove(MDC_KEY)
                );
    }
    /**
     * Clears the entire extra logging context for the current thread.
     * After this call no extra fields are present in subsequent log events.
     */
    public static void clearExtraContext() {
        MDC.remove(MDC_KEY);
    }
    private static Optional<String> getExtraParamsAsJsonString(Map<String, String> current) {
        try {
            return Optional.of(MAPPER.writeValueAsString(current));
        } catch (JsonProcessingException e) {
            log.error("Error while converting map to string", e);
            return Optional.empty();
        }
    }

    private static Map<String, String> getMdcExtraParamsMap() {
        String jsonParamsString = MDC.get(MDC_KEY);
        if (jsonParamsString == null || jsonParamsString.isBlank()) {
            return new HashMap<>();
        }
        try {
            return MAPPER.readValue(jsonParamsString, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Error while converting string to map", e);
            return new HashMap<>();
        }
    }
}
