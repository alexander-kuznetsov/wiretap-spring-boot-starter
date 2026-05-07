package io.wiretap.configuration;

import io.wiretap.http.incoming.provider.httpinfo.HttpInfoMessageProvider;
import io.wiretap.http.incoming.provider.message.MessageProvider;
import io.wiretap.http.incoming.provider.operationinfo.ExtraRequestInfoProvider;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.micrometer.tracing.Tracer;
import io.wiretap.http.outgoing.interceptor.feignclient.FeignClientWrapper;
import io.wiretap.http.outgoing.interceptor.rest.RestClientLoggingInterceptor;
import io.wiretap.http.outgoing.interceptor.rest.RestTemplateLoggingInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the library autoconfiguration and verifies all key beans are wired.
 * This catches regressions in component scan, conditional beans, and constructor signatures.
 */
class AutoConfigurationSmokeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, LoggerConfiguration.class))
            .withUserConfiguration(StubTracerConfig.class);

    @Test
    void contextLoadsWithDefaults() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(WiretapHeadersProperties.class);
            assertThat(ctx).hasSingleBean(WiretapFieldNamesProperties.class);
            assertThat(ctx).hasSingleBean(HttpAccessFieldNames.class);
            assertThat(ctx).hasSingleBean(HttpInfoMessageProvider.class);
            assertThat(ctx).hasSingleBean(MessageProvider.class);
            assertThat(ctx).hasSingleBean(ExtraRequestInfoProvider.class);
            assertThat(ctx).hasSingleBean(RestTemplateLoggingInterceptor.class);
            assertThat(ctx).hasSingleBean(RestClientLoggingInterceptor.class);
            assertThat(ctx).hasSingleBean(FeignClientWrapper.class);
        });
    }

    @Test
    void httpAccessFieldNamesBeanReflectsConfiguredOverrides() {
        runner
                .withPropertyValues(
                        "wiretap.fields.http.return-code=status",
                        "wiretap.fields.http.duration=elapsed_ms"
                )
                .run(ctx -> {
                    HttpAccessFieldNames names = ctx.getBean(HttpAccessFieldNames.class);
                    assertThat(names.getReturnCode()).isEqualTo("status");
                    assertThat(names.getDuration()).isEqualTo("elapsed_ms");
                });
    }

    @Test
    void feignInterceptorCanBeDisabled() {
        runner
                .withPropertyValues("wiretap.feign-interceptor.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(FeignClientWrapper.class));
    }

    /**
     * The library wires {@code AccessLogTraceIdForwarder} from a {@link Tracer} bean.
     * Tests don't need a real tracer, just any non-null instance.
     */
    @Configuration
    static class StubTracerConfig {
        @Bean
        Tracer tracer() {
            return io.micrometer.tracing.Tracer.NOOP;
        }
    }
}
