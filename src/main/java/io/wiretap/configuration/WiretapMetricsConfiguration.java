package io.wiretap.configuration;

import io.micrometer.core.instrument.MeterRegistry;
import io.wiretap.metrics.AsyncAppenderMetricsBinder;
import io.wiretap.metrics.NoOpWiretapMetrics;
import io.wiretap.metrics.WiretapMetrics;
import io.wiretap.metrics.WiretapMetricsImpl;
import io.wiretap.metrics.WiretapMetricsProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the active {@link WiretapMetrics} implementation when Micrometer is on
 * the classpath and the master switch {@code wiretap.metrics.enabled} is not
 * turned off (default: on).
 *
 * <p>The {@code MeterRegistry} is resolved lazily through an
 * {@link ObjectProvider}, so it does not have to exist when this configuration
 * is evaluated. That keeps the wiring immune to auto-configuration ordering:
 * this class is imported by {@code WiretapAutoConfiguration}, which runs at
 * {@code HIGHEST_PRECEDENCE} — long before Micrometer's registry
 * auto-configuration — so a plain {@code @ConditionalOnBean(MeterRegistry.class)}
 * would evaluate before the registry exists and silently fall back to no-op.
 *
 * <p>When no registry is available the binding itself falls back to
 * {@link NoOpWiretapMetrics}; the same no-op is installed by
 * {@code WiretapAutoConfiguration} when Micrometer is absent from the classpath
 * entirely, so interceptors never face a missing dependency.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "wiretap.metrics", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(WiretapMetricsProperties.class)
public class WiretapMetricsConfiguration {

    @Bean
    public WiretapMetrics wiretapMetrics(ObjectProvider<MeterRegistry> meterRegistry,
                                         WiretapMetricsProperties props) {
        MeterRegistry registry = meterRegistry.getIfAvailable();
        return registry != null ? new WiretapMetricsImpl(registry, props) : new NoOpWiretapMetrics();
    }

    /**
     * Binds {@code AsyncAppender} queue gauges to the {@link MeterRegistry}.
     * Created only when the user opts into wrapping the built-in appenders in
     * {@code AsyncAppender} via {@code wiretap.async-logging.enabled=true},
     * and keeps the {@code wiretap.metrics.async-appender.enabled=true} default.
     */
    @Bean
    @ConditionalOnExpression("${wiretap.async-logging.enabled:false} and ${wiretap.metrics.async-appender.enabled:true}")
    public AsyncAppenderMetricsBinder wiretapAsyncAppenderMetricsBinder(ObjectProvider<MeterRegistry> meterRegistry) {
        MeterRegistry registry = meterRegistry.getIfAvailable();
        // No registry on the classpath/context → nothing to bind the queue gauges to.
        return registry != null ? new AsyncAppenderMetricsBinder(registry) : null;
    }
}
