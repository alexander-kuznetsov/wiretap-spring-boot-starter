package io.wiretap.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.wiretap.kafka.message.KafkaHeaderMaskingHandler;
import io.wiretap.kafka.message.KafkaMessageInfo;
import io.wiretap.kafka.message.KafkaTopicMaskingHandler;
import io.wiretap.kafka.message.KafkaValueMaskingHandler;
import io.wiretap.kafka.message.settings.KafkaAccessFieldNames;
import io.wiretap.kafka.message.settings.KafkaInfoLogMessageSettings;
import io.wiretap.kafka.message.settings.KafkaInfoLogMessageSettings.KafkaConfigurableField;
import io.wiretap.kafka.message.settings.body.MessageBodySettings;
import io.wiretap.util.FieldVisibilityMap;
import io.wiretap.util.HeaderSelector;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.header.Headers;
import org.jetbrains.annotations.Nullable;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring-managed collector that turns a {@link KafkaMessageInfo} into a JSON line
 * routed through MDC + SLF4J. Applies masking, truncation and visibility settings
 * coming from {@link KafkaInfoLogMessageSettings}.
 *
 * <p>One sink per direction (producer / consumer) — the corresponding Spring
 * configuration wires the proper settings and registers the sink with the
 * Kafka-instantiated interceptor through the {@code setSink(...)} static method
 * on the interceptor class.
 */
@Slf4j
public class KafkaLogSink {

    public static final String MDC_KEY = "KAFKA-MESSAGE-LOG";

    private final KafkaInfoLogMessageSettings settings;
    private final KafkaAccessFieldNames fieldNames;
    @Nullable
    private final KafkaValueMaskingHandler valueMaskingHandler;
    @Nullable
    private final KafkaHeaderMaskingHandler headerMaskingHandler;
    @Nullable
    private final KafkaTopicMaskingHandler topicMaskingHandler;
    private final ObjectMapper mapper = new ObjectMapper();

    public KafkaLogSink(
            KafkaInfoLogMessageSettings settings,
            KafkaAccessFieldNames fieldNames,
            @Nullable KafkaValueMaskingHandler valueMaskingHandler,
            @Nullable KafkaHeaderMaskingHandler headerMaskingHandler,
            @Nullable KafkaTopicMaskingHandler topicMaskingHandler
    ) {
        this.settings = settings;
        this.fieldNames = fieldNames;
        this.valueMaskingHandler = valueMaskingHandler;
        this.headerMaskingHandler = headerMaskingHandler;
        this.topicMaskingHandler = topicMaskingHandler;
    }

    /**
     * @return {@code true} if logging for this topic is allowed (i.e. no exclude
     *         pattern matched). Caller should skip log emission when {@code false}.
     */
    public boolean isTopicLogged(String topic) {
        if (topic == null) return false;
        return settings.getExcludeTopicPatterns().stream().noneMatch(topic::matches);
    }

    /**
     * Emits a {@code kafka_info} JSON object via MDC + {@code log.info(...)}.
     * Applies masking, truncation and per-topic overrides.
     */
    public void emit(KafkaMessageInfo info) {
        try {
            if (info.getTopic() == null || !isTopicLogged(info.getTopic())) {
                return;
            }

            final KafkaInfoLogMessageSettings effective = settings.getSettingsByTopic(info.getTopic());
            final FieldVisibilityMap<KafkaConfigurableField> visibility = effective.getVisibilitySettings();

            final KafkaMessageInfo masked = applyVisibilityAndMasking(info, effective, visibility);
            final String json = mapper.writeValueAsString(masked.toMap(fieldNames));

            try (MDC.MDCCloseable ignored = MDC.putCloseable(MDC_KEY, json)) {
                String topic = masked.getTopic();
                if (info.getDirection() == KafkaMessageInfo.Direction.OUTGOING) {
                    log.info("Captured outgoing kafka message {}", topic);
                } else {
                    log.info("Captured incoming kafka message {}", topic);
                }
            }
        } catch (Exception e) {
            log.error("Error while logging kafka info", e);
        }
    }

    private KafkaMessageInfo applyVisibilityAndMasking(
            KafkaMessageInfo info,
            KafkaInfoLogMessageSettings effective,
            FieldVisibilityMap<KafkaConfigurableField> v
    ) {
        final String topic = info.getTopic();
        final MessageBodySettings body = effective.getMessageBodySettings();

        return KafkaMessageInfo.builder()
                .direction(info.getDirection())
                .topic(visible(v, KafkaConfigurableField.TOPIC) ? maskTopic(topic, effective) : null)
                .partition(visible(v, KafkaConfigurableField.PARTITION) ? info.getPartition() : null)
                .offset(visible(v, KafkaConfigurableField.OFFSET) ? info.getOffset() : null)
                .clientId(visible(v, KafkaConfigurableField.CLIENT_ID) ? info.getClientId() : null)
                .groupId(visible(v, KafkaConfigurableField.GROUP_ID) ? info.getGroupId() : null)
                .key(visible(v, KafkaConfigurableField.KEY)
                        ? renderValue(topic, info.getKey(), effective, body) : null)
                .keyLength(info.getKeyLength())
                .value(visible(v, KafkaConfigurableField.VALUE)
                        ? renderValue(topic, info.getValue(), effective, body) : null)
                .valueLength(info.getValueLength())
                .headers(visible(v, KafkaConfigurableField.HEADERS)
                        ? maskHeaders(topic, info.getHeaders(), effective) : null)
                .timestamp(visible(v, KafkaConfigurableField.TIMESTAMP) ? info.getTimestamp() : null)
                .timestampType(visible(v, KafkaConfigurableField.TIMESTAMP) ? info.getTimestampType() : null)
                .duration(visible(v, KafkaConfigurableField.DURATION) ? info.getDuration() : null)
                .status(visible(v, KafkaConfigurableField.STATUS) ? info.getStatus() : null)
                .errorClass(visible(v, KafkaConfigurableField.STATUS) ? info.getErrorClass() : null)
                .errorMessage(visible(v, KafkaConfigurableField.STATUS) ? info.getErrorMessage() : null)
                .build();
    }

    private static boolean visible(FieldVisibilityMap<KafkaConfigurableField> v, KafkaConfigurableField f) {
        return Boolean.TRUE.equals(v.get(f));
    }

    private String maskTopic(String topic, KafkaInfoLogMessageSettings effective) {
        if (topic == null) return null;
        return effective.isEnableTopicMasking() && topicMaskingHandler != null
                ? topicMaskingHandler.maskTopic(topic)
                : topic;
    }

    private String renderValue(String topic, String raw,
                               KafkaInfoLogMessageSettings effective,
                               MessageBodySettings body) {
        if (raw == null) return null;
        String result = raw;
        if (effective.isEnableValueMasking()
                && body.isEnableValueMasking()
                && valueMaskingHandler != null) {
            result = valueMaskingHandler.maskValue(topic, result);
        }
        if (body.isEnableValueTruncating() && result.length() > body.getMaxValueLength()) {
            result = result.substring(0, body.getMaxValueLength()) + "...[truncated]";
        }
        return result;
    }

    private Map<String, String> maskHeaders(String topic, Map<String, String> headers,
                                            KafkaInfoLogMessageSettings effective) {
        if (headers == null || headers.isEmpty()) return headers;
        if (!effective.isEnableHeadersMasking() || headerMaskingHandler == null) {
            return headers;
        }
        Map<String, String> masked = new LinkedHashMap<>(headers.size());
        for (Map.Entry<String, String> e : headers.entrySet()) {
            masked.put(e.getKey(), headerMaskingHandler.maskHeaderValue(topic, e.getKey(), e.getValue()));
        }
        return masked;
    }

    /** Helper for interceptors: collect configured headers from a Kafka {@link Headers} bag. */
    public Map<String, String> collectHeaders(Headers headers) {
        if (headers == null) return null;
        Map<String, String> out = HeaderSelector.selectKafka(settings.getHeaders(), headers);
        return out.isEmpty() ? null : out;
    }

    public static String formatTimestamp(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).toString();
    }
}
