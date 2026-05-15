package io.wiretap.kafka.message.settings;

import lombok.Getter;
import lombok.Setter;
import org.springframework.util.CollectionUtils;

/**
 * Per-topic override of the common Kafka log settings.
 * Applied only when the topic name matches {@link #matchTopicPattern}.
 */
public class SpecificKafkaInfoLogMessageSettings extends KafkaInfoLogMessageSettings {

    @Getter
    @Setter
    private String matchTopicPattern;

    /**
     * Merges this override with the common settings: any field explicitly customised
     * here wins, anything left at its default value falls back to the common settings.
     */
    public KafkaInfoLogMessageSettings getIntersectionSettings(KafkaInfoLogMessageSettings common) {
        final KafkaInfoLogMessageSettings defaults = new KafkaInfoLogMessageSettings();
        final KafkaInfoLogMessageSettings merged = new KafkaInfoLogMessageSettings();

        merged.setMessageBodySettings(
                this.getMessageBodySettings().equals(defaults.getMessageBodySettings())
                        ? common.getMessageBodySettings() : this.getMessageBodySettings()
        );
        merged.setHeaders(
                CollectionUtils.isEmpty(this.getHeaders()) ? common.getHeaders() : this.getHeaders()
        );
        merged.setVisibilitySettings(
                this.getVisibilitySettings().equals(defaults.getVisibilitySettings())
                        ? common.getVisibilitySettings() : this.getVisibilitySettings()
        );
        merged.setEnableValueMasking(this.isEnableValueMasking() && common.isEnableValueMasking());
        merged.setEnableHeadersMasking(this.isEnableHeadersMasking() && common.isEnableHeadersMasking());
        merged.setEnableTopicMasking(this.isEnableTopicMasking() && common.isEnableTopicMasking());

        return merged;
    }
}
