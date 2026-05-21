package io.wiretap.metrics;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AsyncAppenderBase;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Inspects the Logback {@link LoggerContext} after application startup and
 * registers Micrometer gauges for every {@link AsyncAppenderBase} found on the
 * ROOT logger. This makes queue backpressure observable —
 * {@code wiretap.async.appender.queue.size} rising toward
 * {@code wiretap.async.appender.queue.capacity} means the appender is about to
 * start dropping events.
 *
 * <p>Bean is created only when both {@code wiretap.async-logging.enabled=true}
 * (so the AsyncAppender exists) and
 * {@code wiretap.metrics.async-appender.enabled=true} (so we publish gauges).
 */
public final class AsyncAppenderMetricsBinder {

    private static final Logger log = LoggerFactory.getLogger(AsyncAppenderMetricsBinder.class);
    private static final String ROOT = "ROOT";

    private final MeterRegistry registry;
    private final AtomicBoolean bound = new AtomicBoolean(false);

    public AsyncAppenderMetricsBinder(MeterRegistry registry) {
        this.registry = registry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bind() {
        if (!bound.compareAndSet(false, true)) return;

        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof LoggerContext context)) {
            log.debug("Wiretap async-appender gauges skipped: SLF4J factory is {} (expected Logback LoggerContext)",
                    factory.getClass().getName());
            return;
        }

        Iterator<Appender<ch.qos.logback.classic.spi.ILoggingEvent>> appenders =
                context.getLogger(ROOT).iteratorForAppenders();
        while (appenders.hasNext()) {
            Appender<ch.qos.logback.classic.spi.ILoggingEvent> appender = appenders.next();
            if (appender instanceof AsyncAppenderBase<?> async) {
                bindAppender(async);
            }
        }
    }

    private <E> void bindAppender(AsyncAppenderBase<E> async) {
        String name = async.getName() == null ? async.getClass().getSimpleName() : async.getName();
        Gauge.builder("wiretap.async.appender.queue.size", async, AsyncAppenderBase::getNumberOfElementsInQueue)
                .description("Number of events currently buffered in the AsyncAppender queue.")
                .tag("appender", name)
                .register(registry);
        Gauge.builder("wiretap.async.appender.queue.capacity", async, a -> async.getQueueSize())
                .description("Configured maximum number of events the AsyncAppender queue can hold.")
                .tag("appender", name)
                .register(registry);
        Gauge.builder("wiretap.async.appender.queue.remaining", async, AsyncAppenderBase::getRemainingCapacity)
                .description("Remaining capacity in the AsyncAppender queue (capacity - size).")
                .tag("appender", name)
                .register(registry);
    }
}
