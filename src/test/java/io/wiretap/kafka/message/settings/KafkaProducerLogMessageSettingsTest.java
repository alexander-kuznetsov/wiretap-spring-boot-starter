package io.wiretap.kafka.message.settings;

import io.wiretap.kafka.message.settings.KafkaInfoLogMessageSettings.KafkaConfigurableField;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaProducerLogMessageSettingsTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void defaults_visibilityAllOn_logsEverything() {
        runner.run(ctx -> {
            KafkaProducerLogMessageSettings props = ctx.getBean(KafkaProducerLogMessageSettings.class);
            assertThat(props.getVisibilitySettings())
                    .containsEntry(KafkaConfigurableField.TOPIC, Boolean.TRUE)
                    .containsEntry(KafkaConfigurableField.VALUE, Boolean.TRUE)
                    .containsEntry(KafkaConfigurableField.HEADERS, Boolean.TRUE);
            assertThat(props.isEnableValueMasking()).isTrue();
            assertThat(props.getMessageBodySettings().getMaxValueLength()).isEqualTo(2000);
            assertThat(props.getExcludeTopicPatterns()).isEmpty();
        });
    }

    @Test
    void overrides_bindCleanly() {
        runner
                .withPropertyValues(
                        "wiretap.kafka-producer-interceptor.visibility-settings.VALUE=false",
                        "wiretap.kafka-producer-interceptor.message-body-settings.max-value-length=500",
                        "wiretap.kafka-producer-interceptor.exclude-topic-patterns[0]=.*\\.internal\\..*",
                        "wiretap.kafka-producer-interceptor.headers[0]=x-trace-id",
                        "wiretap.kafka-producer-interceptor.headers[1]=x-tenant"
                )
                .run(ctx -> {
                    KafkaProducerLogMessageSettings props = ctx.getBean(KafkaProducerLogMessageSettings.class);
                    assertThat(props.getVisibilitySettings().get(KafkaConfigurableField.VALUE)).isFalse();
                    assertThat(props.getMessageBodySettings().getMaxValueLength()).isEqualTo(500);
                    assertThat(props.getExcludeTopicPatterns()).containsExactly(".*\\.internal\\..*");
                    assertThat(props.getHeaders()).containsExactly("x-trace-id", "x-tenant");
                });
    }

    @Test
    void specificTopicSettings_overrideCommon() {
        runner
                .withPropertyValues(
                        "wiretap.kafka-producer-interceptor.specific-topic-settings[0].match-topic-pattern=orders\\..*",
                        "wiretap.kafka-producer-interceptor.specific-topic-settings[0].visibility-settings.VALUE=false"
                )
                .run(ctx -> {
                    KafkaProducerLogMessageSettings props = ctx.getBean(KafkaProducerLogMessageSettings.class);
                    assertThat(props.getSpecificTopicSettings()).hasSize(1);
                    KafkaInfoLogMessageSettings effective = props.getSettingsByTopic("orders.events");
                    assertThat(effective.getVisibilitySettings().get(KafkaConfigurableField.VALUE)).isFalse();
                    assertThat(props.getSettingsByTopic("payments.events").getVisibilitySettings().get(KafkaConfigurableField.VALUE)).isTrue();
                });
    }

    @EnableConfigurationProperties(KafkaProducerLogMessageSettings.class)
    static class TestConfig {
    }
}
