package io.wiretap.configuration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the property keys the logback fragments bind file logging to.
 * The access fragment once read the dash-separated `wiretap.file-logging-enabled`
 * while the application fragment and both READMEs used `wiretap.file-logging.enabled`,
 * so `HTTP_FILE-ROLLING` never activated for the documented configuration.
 */
final class LogbackFileLoggingKeysTest {

    @Test
    void accessFragmentBindsFileLoggingToggleToTheDocumentedKey() {
        assertThat(new LogbackFragment("logback-access-properties.xml").source("isLoggingFile"))
                .as("access fragment must read the file-logging toggle the README documents")
                .isEqualTo("wiretap.file-logging.enabled");
    }

    @Test
    void accessFragmentBindsFileLoggingPathToTheDocumentedKey() {
        assertThat(new LogbackFragment("logback-access-properties.xml").source("loggingPath"))
                .as("access fragment must read the file-logging path the README documents")
                .isEqualTo("wiretap.file-logging.path");
    }

    @Test
    void bothFragmentsAgreeOnTheFileLoggingToggle() {
        assertThat(new LogbackFragment("logback-access-properties.xml").source("isLoggingFile"))
                .as("one toggle has to switch application and access file appenders together")
                .isEqualTo(new LogbackFragment("logback-properties.xml").source("isLoggingFile"));
    }

    /**
     * A logback fragment on the classpath, addressed by the `springProperty`
     * declarations it carries.
     */
    private static final class LogbackFragment {

        private final String resource;

        private LogbackFragment(String resource) {
            this.resource = resource;
        }

        private String source(String property) {
            Matcher declaration = Pattern
                    .compile("<springProperty[^>]*\\bname=\"" + property + "\"[^>]*\\bsource=\"([^\"]+)\"")
                    .matcher(text());
            if (!declaration.find()) {
                throw new IllegalStateException("springProperty " + property + " not declared in " + resource);
            }
            return declaration.group(1);
        }

        private String text() {
            try (InputStream fragment = getClass().getClassLoader().getResourceAsStream(resource)) {
                if (fragment == null) {
                    throw new IllegalStateException("logback fragment missing from classpath: " + resource);
                }
                return new String(fragment.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException failure) {
                throw new IllegalStateException("cannot read logback fragment " + resource, failure);
            }
        }
    }
}
