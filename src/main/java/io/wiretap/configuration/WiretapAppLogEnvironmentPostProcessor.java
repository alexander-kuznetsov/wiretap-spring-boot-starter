package io.wiretap.configuration;

import io.wiretap.applog.provider.LazyStandardLogFieldsProvider;
import io.wiretap.applog.provider.WiretapStandardLogFieldsProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Hands Logback the standard app-log fields as soon as the environment is
 * prepared, before any bean exists.
 *
 * <p>The provider used to reach Logback only from a bean's {@code @PostConstruct},
 * so every record written while the context was still being refreshed — above all
 * {@code Application run failed} after a bean blew up — fell back to a bare
 * timestamp/level/logger/message set and lost {@code env}, {@code system} and
 * {@code inst}. At {@code ApplicationEnvironmentPreparedEvent} the environment
 * already holds the active profiles and {@code wiretap.app-log.*}, and Spring Boot
 * runs environment post-processors before it parses {@code logback-spring.xml}, so
 * the first JSON record already carries the full field set. Message masking joins
 * later, when {@link WiretapAppLogConfiguration} replaces this provider with the
 * bean-backed one.
 */
public final class WiretapAppLogEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        LazyStandardLogFieldsProvider.setProvider(new WiretapStandardLogFieldsProvider(
                Binder.get(environment)
                        .bind("wiretap.app-log", WiretapAppLogProperties.class)
                        .orElseGet(WiretapAppLogProperties::new),
                null,
                environment.getProperty("spring.profiles.active", ""),
                environment.getProperty("spring.application.name", ""),
                environment.getProperty("HOSTNAME", "")));
    }
}
