package io.wiretap.integrationtests.applog;

import io.wiretap.integrationtests.support.JsonLogCapture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces a pod that dies while its beans are being created: the
 * {@code Application run failed} record is the one line that explains the
 * crash, and it must carry the same {@code env} as every other record so that
 * log storage can still attribute it to the right environment.
 */
@ExtendWith(OutputCaptureExtension.class)
final class StartupFailureAppLogFieldsTest {

    @Test
    void failedStartupIsReportedWithTheEnvironment(CapturedOutput out) {
        assertThat(JsonLogCapture.<String>at(failure(out), "env"))
                .as("a context that never created the wiretap bean must still log its own failure with env")
                .isEqualTo("qa-17");
    }

    private static Map<String, Object> failure(CapturedOutput out) {
        try {
            new SpringApplicationBuilder(BrokenConfig.class)
                    .web(WebApplicationType.NONE)
                    .properties("spring.profiles.active=qa-17")
                    .run();
        } catch (BeanCreationException expected) {
        }
        return JsonLogCapture.all(out).stream()
                .filter(log -> "Application run failed".equals(log.get("message")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the startup failure must be logged as a JSON record"));
    }

    @Configuration
    static class BrokenConfig {
        @Bean
        Object brokenBean() {
            throw new IllegalStateException("simulated startup failure");
        }
    }
}
