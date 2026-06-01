package io.wiretap.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WiretapTeeFilterDefaultsEnvironmentPostProcessorTest {

    private final WiretapTeeFilterDefaultsEnvironmentPostProcessor processor =
            new WiretapTeeFilterDefaultsEnvironmentPostProcessor();

    @Test
    void enablesTheTeeFilterByDefault() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("logback.access.tee-filter.enabled")).isEqualTo("true");
    }

    @Test
    void explicitConfigurationOverridesTheDefault() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "user", Map.of("logback.access.tee-filter.enabled", "false")));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("logback.access.tee-filter.enabled")).isEqualTo("false");
    }
}
