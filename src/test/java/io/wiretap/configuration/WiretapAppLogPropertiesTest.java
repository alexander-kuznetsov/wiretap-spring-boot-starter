package io.wiretap.configuration;

import io.wiretap.applog.provider.AppLogField;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class WiretapAppLogPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void defaults_matchExpectedSchema() {
        runner.run(ctx -> {
            WiretapAppLogProperties props = ctx.getBean(WiretapAppLogProperties.class);
            WiretapAppLogProperties.Fields f = props.getFields();

            assertThat(f.getTimestamp()).isEqualTo("@timestamp");
            assertThat(f.getEnv()).isEqualTo("env");
            assertThat(f.getSystem()).isEqualTo("system");
            assertThat(f.getInstance()).isEqualTo("inst");
            assertThat(f.getTraceId()).isEqualTo("trace_id");
            assertThat(f.getSpanId()).isEqualTo("span_id");
            assertThat(f.getLevel()).isEqualTo("level");
            assertThat(f.getThreadName()).isEqualTo("thread_name");
            assertThat(f.getLoggerName()).isEqualTo("logger");
            assertThat(f.getMessage()).isEqualTo("message");
            assertThat(f.getHttpInfo()).isEqualTo("http_info");
            assertThat(f.getExtra()).isEqualTo("extra");
        });
    }

    @Test
    void callerFields_areOffByDefault() {
        runner.run(ctx -> {
            WiretapAppLogProperties props = ctx.getBean(WiretapAppLogProperties.class);
            assertThat(props.isVisible(AppLogField.CALLER_CLASS)).isFalse();
            assertThat(props.isVisible(AppLogField.CALLER_METHOD)).isFalse();
            assertThat(props.isVisible(AppLogField.CALLER_LINE)).isFalse();
            assertThat(props.isVisible(AppLogField.CALLER_FILE)).isFalse();
        });
    }

    @Test
    void standardFields_areOnByDefault() {
        runner.run(ctx -> {
            WiretapAppLogProperties props = ctx.getBean(WiretapAppLogProperties.class);
            assertThat(props.isVisible(AppLogField.TIMESTAMP)).isTrue();
            assertThat(props.isVisible(AppLogField.LEVEL)).isTrue();
            assertThat(props.isVisible(AppLogField.MESSAGE)).isTrue();
            assertThat(props.isVisible(AppLogField.LOGGER_NAME)).isTrue();
        });
    }

    @Test
    void visibilitySettings_canEnableCallerFields() {
        runner
                .withPropertyValues(
                        "wiretap.app-log.visibility-settings.CALLER_CLASS=true",
                        "wiretap.app-log.visibility-settings.CALLER_LINE=true"
                )
                .run(ctx -> {
                    WiretapAppLogProperties props = ctx.getBean(WiretapAppLogProperties.class);
                    assertThat(props.isVisible(AppLogField.CALLER_CLASS)).isTrue();
                    assertThat(props.isVisible(AppLogField.CALLER_LINE)).isTrue();
                    assertThat(props.isVisible(AppLogField.CALLER_METHOD)).isFalse();
                });
    }

    @Test
    void fieldNameOverrides_areBound() {
        runner
                .withPropertyValues(
                        "wiretap.app-log.fields.logger-name=class",
                        "wiretap.app-log.fields.thread-name=thread"
                )
                .run(ctx -> {
                    WiretapAppLogProperties.Fields f = ctx.getBean(WiretapAppLogProperties.class).getFields();
                    assertThat(f.getLoggerName()).isEqualTo("class");
                    assertThat(f.getThreadName()).isEqualTo("thread");
                    assertThat(f.getMessage()).isEqualTo("message");
                });
    }

    @EnableConfigurationProperties(WiretapAppLogProperties.class)
    static class TestConfig {
    }
}
