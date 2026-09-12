package io.wiretap.configuration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.wiretap.applog.provider.LazyStandardLogFieldsProvider;
import net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the moment the standard app-log fields become available: as soon as the
 * environment is prepared, before a single bean exists. A context that dies in
 * {@code preInstantiateSingletons} used to report its own failure without
 * {@code env}/{@code system}/{@code inst}, because the provider was registered
 * from a bean's {@code @PostConstruct} and the record fell back to a bare
 * timestamp/level/logger/message set.
 */
final class WiretapAppLogEnvironmentPostProcessorTest {

    @Test
    void writesTheProfileUnderItsConfiguredNameBeforeAnyBeanExists() {
        assertThat(encoded(Map.of(
                "spring.profiles.active", "qa-17",
                "wiretap.app-log.fields.env", "environment")))
                .as("a record written before the context refreshes must carry the active profile under the configured field name")
                .contains("\"environment\":\"qa-17\"");
    }

    @Test
    void readsNameAndHostFromTheSameKeysAsTheBean() {
        assertThat(encoded(Map.of(
                "spring.application.name", "billing",
                "HOSTNAME", "billing-7f9c4")))
                .as("early records must take system and inst from the keys the Spring bean reads later")
                .contains("\"system\":\"billing\"", "\"inst\":\"billing-7f9c4\"");
    }

    @Test
    void honoursVisibilitySettings() {
        assertThat(encoded(Map.of(
                "spring.application.name", "billing",
                "wiretap.app-log.visibility-settings.SYSTEM", "false")))
                .as("a field hidden through visibility-settings must stay hidden in early records too")
                .doesNotContain("billing");
    }

    private static String encoded(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        new WiretapAppLogEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());
        LoggerContext context = new LoggerContext();
        LoggingEventCompositeJsonEncoder encoder = new LoggingEventCompositeJsonEncoder();
        encoder.setContext(context);
        encoder.getProviders().addProvider(new LazyStandardLogFieldsProvider());
        encoder.start();
        return new String(encoder.encode(event(context)), StandardCharsets.UTF_8);
    }

    private static LoggingEvent event(LoggerContext context) {
        LoggingEvent event = new LoggingEvent("", context.getLogger("test"), Level.INFO, "hello", null, null);
        event.setMDCPropertyMap(Map.of());
        return event;
    }
}
