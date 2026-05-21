package io.wiretap.configuration;

import io.micrometer.core.instrument.MeterRegistry;
import io.wiretap.metrics.AsyncAppenderMetricsBinder;
import io.wiretap.metrics.WiretapMetrics;
import io.wiretap.metrics.WiretapMetricsImpl;
import io.wiretap.metrics.WiretapMetricsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the active {@link WiretapMetrics} implementation when Micrometer is on
 * the classpath, the application provides a {@code MeterRegistry} bean, and
 * the master switch {@code wiretap.metrics.enabled} is not turned off
 * (default: on).
 *
 * <p>When any of these conditions are not met, {@code WiretapAutoConfiguration}
 * installs a no-op {@code WiretapMetrics} bean instead, so interceptors never
 * have to deal with a missing dependency.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(MeterRegistry.class)
@ConditionalOnProperty(prefix = "wiretap.metrics", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(WiretapMetricsProperties.class)
public class WiretapMetricsConfiguration {

    @Bean
    public WiretapMetrics wiretapMetrics(MeterRegistry registry, WiretapMetricsProperties props) {
        return new WiretapMetricsImpl(registry, props);
    }

    /**
     * Binds {@code AsyncAppender} queue gauges to the {@link MeterRegistry}.
     * Created only when the user opts into wrapping the built-in appenders in
     * {@code AsyncAppender} via {@code wiretap.async-logging.enabled=true},
     * and keeps the {@code wiretap.metrics.async-appender.enabled=true} default.
     */
    @Bean
    @ConditionalOnExpression("${wiretap.async-logging.enabled:false} and ${wiretap.metrics.async-appender.enabled:true}")
    public AsyncAppenderMetricsBinder wiretapAsyncAppenderMetricsBinder(MeterRegistry registry) {
        return new AsyncAppenderMetricsBinder(registry);
    }
}
