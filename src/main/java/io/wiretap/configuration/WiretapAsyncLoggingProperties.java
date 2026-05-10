package io.wiretap.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Toggles wrapping the built-in {@code CONSOLE} and {@code FILE-ROLLING}
 * appenders in a Logback {@link ch.qos.logback.classic.AsyncAppender}.
 * <p>
 * Wiretap reads these values inside {@code logback.properties.xml} via
 * {@code <springProperty>} bindings — the Java code does not consume the bean
 * directly, but the bean must exist so Spring Boot exposes the values to the
 * Logback context and {@code spring-configuration-metadata.json} surfaces them
 * to the IDE.
 * <p>
 * Recommended for high-throughput WebClient workloads where synchronous
 * appender writes on the reactor event-loop thread become a bottleneck.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "wiretap.async-logging")
public class WiretapAsyncLoggingProperties {

    /** Wrap built-in appenders in {@code AsyncAppender}. Default: false. */
    private boolean enabled = false;

    /** Maximum number of events held in the async queue. Logback default is 256. */
    private int queueSize = 256;

    /**
     * If {@code true}, the appender drops events when the queue is full instead
     * of blocking the producer thread. Use this for hot paths where dropping a
     * log line is preferable to backpressuring the request thread.
     */
    private boolean neverBlock = false;

    /**
     * Threshold below which Logback drops TRACE/DEBUG/INFO events when the
     * queue starts to fill. Default {@code -1} keeps Logback's own default
     * ({@code queueSize / 5}). Set to {@code 0} to never drop events
     * regardless of fill level.
     */
    private int discardingThreshold = -1;
}
