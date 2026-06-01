package io.wiretap.configuration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Turns inbound HTTP request/response body capture on out of the box.
 *
 * <p>Wiretap logs incoming bodies through logback-access' {@code TeeFilter},
 * which the logback-access Spring Boot starter leaves disabled by default
 * ({@code logback.access.tee-filter.enabled=false}). Without it
 * {@code request_body}/{@code response_body} come out empty, which is
 * surprising for a logging library — so wiretap contributes the property as a
 * default here.
 *
 * <p>The default is added at the lowest precedence
 * ({@link org.springframework.core.env.MutablePropertySources#addLast}), so any
 * explicit {@code logback.access.tee-filter.enabled} in the application's own
 * configuration still wins — set it to {@code false} to opt out of buffering
 * inbound bodies.
 */
public class WiretapTeeFilterDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        environment.getPropertySources().addLast(new MapPropertySource(
                "wiretapDefaults",
                Map.of("logback.access.tee-filter.enabled", "true")));
    }
}
