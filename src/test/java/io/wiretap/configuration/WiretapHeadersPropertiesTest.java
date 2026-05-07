package io.wiretap.configuration;

import io.wiretap.http.incoming.provider.trace.SessionKeyProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class WiretapHeadersPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @AfterEach
    void resetSessionKey() {
        SessionKeyProvider.setSessionKeyHeader("x-session-key");
    }

    @Test
    void defaults_areAppliedWhenNothingConfigured() {
        runner.run(ctx -> {
            WiretapHeadersProperties props = ctx.getBean(WiretapHeadersProperties.class);
            assertThat(props.getForwardToMdc())
                    .containsExactly("x-request-id", "x-session-key", "lb-trace-id");
            assertThat(props.getSessionKeyHeader()).isEqualTo("x-session-key");
        });
    }

    @Test
    void overrides_replaceDefaultsCleanly() {
        runner
                .withPropertyValues(
                        "wiretap.headers.session-key-header=tcs-session-key",
                        "wiretap.headers.forward-to-mdc[0]=x-trace-id",
                        "wiretap.headers.forward-to-mdc[1]=eKassir-PointID"
                )
                .run(ctx -> {
                    WiretapHeadersProperties props = ctx.getBean(WiretapHeadersProperties.class);
                    assertThat(props.getSessionKeyHeader()).isEqualTo("tcs-session-key");
                    assertThat(props.getForwardToMdc()).containsExactly("x-trace-id", "eKassir-PointID");
                });
    }

    @EnableConfigurationProperties(WiretapHeadersProperties.class)
    static class TestConfig {
    }
}
