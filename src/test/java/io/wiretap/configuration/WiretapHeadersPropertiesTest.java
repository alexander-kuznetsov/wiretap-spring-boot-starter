package io.wiretap.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class WiretapHeadersPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void defaults_areAppliedWhenNothingConfigured() {
        runner.run(ctx -> {
            WiretapHeadersProperties props = ctx.getBean(WiretapHeadersProperties.class);
            assertThat(props.getForwardToMdc())
                    .containsExactly("x-request-id", "x-session-key", "lb-trace-id");
        });
    }

    @Test
    void overrides_replaceDefaultsCleanly() {
        runner
                .withPropertyValues(
                        "wiretap.headers.forward-to-mdc[0]=x-trace-id",
                        "wiretap.headers.forward-to-mdc[1]=eKassir-PointID"
                )
                .run(ctx -> {
                    WiretapHeadersProperties props = ctx.getBean(WiretapHeadersProperties.class);
                    assertThat(props.getForwardToMdc()).containsExactly("x-trace-id", "eKassir-PointID");
                });
    }

    @EnableConfigurationProperties(WiretapHeadersProperties.class)
    static class TestConfig {
    }
}
