package io.wiretap.configuration;

import io.wiretap.http.incoming.provider.httpinfo.HttpInfoMessageProvider;
import io.wiretap.http.incoming.provider.message.MessageProvider;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import io.wiretap.http.outgoing.interceptor.feignclient.FeignClientWrapper;
import io.wiretap.http.outgoing.interceptor.rest.RestClientLoggingInterceptor;
import io.wiretap.http.outgoing.interceptor.rest.RestTemplateLoggingInterceptor;
import io.wiretap.http.outgoing.interceptor.webclient.WebClientLoggingFilter;
import io.wiretap.metrics.NoOpWiretapMetrics;
import io.wiretap.metrics.WiretapMetrics;
import io.wiretap.metrics.WiretapMetricsImpl;
import io.wiretap.metrics.WiretapMetricsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the library autoconfiguration and verifies all key beans are wired.
 * This catches regressions in the configuration tree, conditional beans, and constructor signatures.
 */
class AutoConfigurationSmokeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class, WiretapAutoConfiguration.class,
                    WebClientInterceptorConfiguration.class))
            .withUserConfiguration(StubTracerConfig.class);

    @Test
    void contextLoadsWithDefaults() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(WiretapHeadersProperties.class);
            assertThat(ctx).hasSingleBean(WiretapAccessLogFieldsProperties.class);
            assertThat(ctx).hasSingleBean(HttpAccessFieldNames.class);
            assertThat(ctx).hasSingleBean(HttpInfoMessageProvider.class);
            assertThat(ctx).hasSingleBean(MessageProvider.class);
assertThat(ctx).hasSingleBean(RestTemplateLoggingInterceptor.class);
            assertThat(ctx).hasSingleBean(RestClientLoggingInterceptor.class);
            assertThat(ctx).hasSingleBean(FeignClientWrapper.class);
            assertThat(ctx).hasSingleBean(WebClientLoggingFilter.class);
            assertThat(ctx).hasSingleBean(WiretapAsyncLoggingProperties.class);
        });
    }

    @Test
    void asyncLoggingPropertiesReflectsConfiguredOverrides() {
        runner
                .withPropertyValues(
                        "wiretap.async-logging.enabled=true",
                        "wiretap.async-logging.queue-size=1024",
                        "wiretap.async-logging.never-block=true"
                )
                .run(ctx -> {
                    WiretapAsyncLoggingProperties props = ctx.getBean(WiretapAsyncLoggingProperties.class);
                    assertThat(props.isEnabled()).isTrue();
                    assertThat(props.getQueueSize()).isEqualTo(1024);
                    assertThat(props.isNeverBlock()).isTrue();
                });
    }

    @Test
    void httpAccessFieldNamesBeanReflectsConfiguredOverrides() {
        runner
                .withPropertyValues(
                        "wiretap.access-log.fields.http.return-code=status",
                        "wiretap.access-log.fields.http.duration=elapsed_ms"
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
                .withPropertyValues("wiretap.feign-client-interceptor.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(FeignClientWrapper.class));
    }

    @Test
    void wiretapMetricsBean_isNoOpWhenNoMeterRegistry() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(WiretapMetrics.class);
            assertThat(ctx.getBean(WiretapMetrics.class)).isInstanceOf(NoOpWiretapMetrics.class);
        });
    }

    @Test
    void wiretapMetricsBean_isImplWhenMeterRegistryPresent() {
        runner
                .withUserConfiguration(StubMeterRegistryConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(WiretapMetrics.class);
                    assertThat(ctx.getBean(WiretapMetrics.class)).isInstanceOf(WiretapMetricsImpl.class);
                    assertThat(ctx).hasSingleBean(WiretapMetricsProperties.class);
                    assertThat(ctx.getBean(WiretapMetricsProperties.class).isEnabled()).isTrue();
                });
    }

    /**
     * Regression guard for the auto-configuration ordering bug: when the
     * {@code MeterRegistry} is contributed by an auto-configuration that runs
     * AFTER wiretap (which sits at {@code HIGHEST_PRECEDENCE}), the metrics
     * binding must still resolve to {@link WiretapMetricsImpl}. The earlier
     * {@code @ConditionalOnBean(MeterRegistry.class)} evaluated too early and
     * silently fell back to {@link NoOpWiretapMetrics}, so wiretap metrics never
     * appeared in a normal Micrometer/Actuator application. Unlike
     * {@link #wiretapMetricsBean_isImplWhenMeterRegistryPresent()}, the registry
     * here comes from an auto-configuration (not a user bean), which is what
     * reproduces the ordering.
     */
    @Test
    void wiretapMetricsBean_isImplWhenRegistryComesFromLaterAutoConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JacksonAutoConfiguration.class, WiretapAutoConfiguration.class,
                        WebClientInterceptorConfiguration.class,
                        LateMeterRegistryAutoConfiguration.class))
                .withUserConfiguration(StubTracerConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(WiretapMetrics.class);
                    assertThat(ctx.getBean(WiretapMetrics.class)).isInstanceOf(WiretapMetricsImpl.class);
                });
    }

    @Test
    void wiretapMetricsBean_isNoOpWhenDisabledViaProperty() {
        runner
                .withUserConfiguration(StubMeterRegistryConfig.class)
                .withPropertyValues("wiretap.metrics.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(WiretapMetrics.class);
                    assertThat(ctx.getBean(WiretapMetrics.class)).isInstanceOf(NoOpWiretapMetrics.class);
                });
    }

    @Test
    void wiretapMetricsProperties_pickUpDetailedTimingsAndHistogramsOverrides() {
        runner
                .withUserConfiguration(StubMeterRegistryConfig.class)
                .withPropertyValues(
                        "wiretap.metrics.detailed-timings=true",
                        "wiretap.metrics.histograms=true",
                        "wiretap.metrics.tags.topic=true",
                        "wiretap.metrics.tags.status=false"
                )
                .run(ctx -> {
                    WiretapMetricsProperties props = ctx.getBean(WiretapMetricsProperties.class);
                    assertThat(props.isDetailedTimings()).isTrue();
                    assertThat(props.isHistograms()).isTrue();
                    assertThat(props.getTags().isTopic()).isTrue();
                    assertThat(props.getTags().isStatus()).isFalse();
                });
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

    @Configuration
    static class StubMeterRegistryConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    /**
     * A {@code MeterRegistry} contributed by an auto-configuration ordered AFTER
     * wiretap's {@code HIGHEST_PRECEDENCE} configuration — mirroring how
     * Micrometer's real Prometheus/Simple registry appears in an application.
     */
    @AutoConfiguration
    @AutoConfigureAfter(WiretapAutoConfiguration.class)
    static class LateMeterRegistryAutoConfiguration {
        @Bean
        MeterRegistry lateMeterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
