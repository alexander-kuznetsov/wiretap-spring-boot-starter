package io.wiretap.applog.provider;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractFieldJsonProvider;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Logback-instantiated wrapper that delegates to {@link WiretapStandardLogFieldsProvider}
 * once Spring has registered one. The environment post-processor does so as soon as the
 * environment is prepared, so by the time Logback parses {@code logback-spring.xml} a
 * provider is normally already in place. Until then, writes a minimal fallback
 * (timestamp, level, logger, thread, message) using default field names so that
 * pre-Spring log events still produce a usable JSON record instead of an empty {@code {}}.
 */
public class LazyStandardLogFieldsProvider extends AbstractFieldJsonProvider<ILoggingEvent> {

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private static volatile WiretapStandardLogFieldsProvider provider;

    /**
     * Called by {@code WiretapAppLogEnvironmentPostProcessor} once the environment is
     * prepared and again by {@link WiretapStandardLogFieldsProvider} once the bean exists.
     */
    public static void setProvider(WiretapStandardLogFieldsProvider p) {
        provider = p;
    }

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        WiretapStandardLogFieldsProvider p = provider;
        if (p != null) {
            p.writeTo(generator, event);
            return;
        }
        writeFallback(generator, event);
    }

    private static void writeFallback(JsonGenerator gen, ILoggingEvent event) throws IOException {
        gen.writeStringField("@timestamp",
                Instant.ofEpochMilli(event.getTimeStamp()).atOffset(ZoneOffset.UTC).format(TS_FORMAT));
        gen.writeStringField("level", event.getLevel().toString());
        gen.writeStringField("thread_name", event.getThreadName());
        gen.writeStringField("logger", event.getLoggerName());
        String msg = event.getFormattedMessage();
        if (msg != null) {
            gen.writeStringField("message", msg);
        }
    }
}
