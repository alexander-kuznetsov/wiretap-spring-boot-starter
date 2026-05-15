package io.wiretap.kafka.message.settings;

import io.wiretap.kafka.message.settings.body.MessageBodySettings;
import io.wiretap.util.FieldVisibilityMap;
import lombok.Data;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Data
public class KafkaInfoLogMessageSettings {

    private MessageBodySettings messageBodySettings = new MessageBodySettings();

    /** Toggles masking applied to {@code key} and {@code value}. */
    private boolean enableValueMasking = true;

    /** Toggles masking applied to logged record header values. */
    private boolean enableHeadersMasking = true;

    /** Toggles masking applied to the logged topic name. */
    private boolean enableTopicMasking = true;

    /** Record header names logged by default. */
    private Collection<String> headers = Arrays.asList("x-trace-id", "x-request-id");

    /** Topic patterns to skip from logging entirely. */
    private List<String> excludeTopicPatterns = Collections.emptyList();

    private FieldVisibilityMap<KafkaConfigurableField> visibilitySettings = getDefaultLogSettings();

    private List<SpecificKafkaInfoLogMessageSettings> specificTopicSettings = Collections.emptyList();

    public enum KafkaConfigurableField {
        TOPIC,
        PARTITION,
        OFFSET,
        CLIENT_ID,
        GROUP_ID,
        KEY,
        VALUE,
        HEADERS,
        TIMESTAMP,
        DURATION,
        STATUS
    }

    private FieldVisibilityMap<KafkaConfigurableField> getDefaultLogSettings() {
        final FieldVisibilityMap<KafkaConfigurableField> defaults = new FieldVisibilityMap<>(KafkaConfigurableField.class);
        for (KafkaConfigurableField field : KafkaConfigurableField.values()) {
            defaults.put(field, Boolean.TRUE);
        }
        return defaults;
    }

    /**
     * Returns the effective settings for a given topic.
     * Picks the first matching per-topic override and merges it with the common
     * settings; if no override matches, common settings are returned unchanged.
     */
    public KafkaInfoLogMessageSettings getSettingsByTopic(String topic) {
        return specificTopicSettings.stream()
                .filter(settings -> topic.matches(settings.getMatchTopicPattern()))
                .findFirst()
                .map(settings -> settings.getIntersectionSettings(this))
                .orElse(this);
    }
}
