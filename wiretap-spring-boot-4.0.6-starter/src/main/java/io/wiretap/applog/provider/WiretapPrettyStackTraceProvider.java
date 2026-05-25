package io.wiretap.applog.provider;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.boolex.EventEvaluator;
import tools.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import net.logstash.logback.stacktrace.ShortenedThrowableConverter;

/**
 * Renders the throwable attached to a logging event as a JSON <strong>array of
 * strings</strong>, one element per line of the rendered stack trace. Intended
 * for use only when {@code wiretap.pretty-print=true} is active — together
 * with {@code PrettyPrintingJsonGeneratorDecorator} the {@code stack_trace}
 * field is then displayed multi-line in the terminal, which is impossible to
 * achieve with the default single-string representation (a JSON pretty-printer
 * never breaks inside string literals).
 *
 * <p>Lines are produced by delegating to logstash-logback-encoder's standard
 * {@link ShortenedThrowableConverter}, so depth / length limits, root-cause
 * ordering and class-name shortening behave exactly like the {@code
 * <stackTrace>} provider — only the field type changes from string to array.
 *
 * <p>Field name defaults to {@code stack_trace} (matches the standard provider)
 * and can be overridden via {@link #setFieldName(String)}.
 *
 * <p>In production builds (default {@code pretty-print=false}) this provider
 * is not wired in, so log shippers / log aggregators keep seeing the
 * traditional single-string {@code stack_trace} field.
 */
public class WiretapPrettyStackTraceProvider extends AbstractFieldJsonProvider<ILoggingEvent> {

    public static final String DEFAULT_FIELD_NAME = "stack_trace";

    private final ShortenedThrowableConverter converter;

    public WiretapPrettyStackTraceProvider() {
        this(new ShortenedThrowableConverter());
    }

    WiretapPrettyStackTraceProvider(ShortenedThrowableConverter converter) {
        this.converter = converter;
        setFieldName(DEFAULT_FIELD_NAME);
        this.converter.setRootCauseFirst(true);
    }

    @Override
    public void start() {
        converter.start();
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
        converter.stop();
    }

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) {
        if (event == null || event.getThrowableProxy() == null) {
            return;
        }
        String rendered = converter.convert(event);
        if (rendered == null || rendered.isEmpty()) {
            return;
        }

        generator.writeName(getFieldName());
        generator.writeStartArray();
        writeLines(generator, rendered);
        generator.writeEndArray();
    }

    private static void writeLines(JsonGenerator generator, String rendered) {
        int from = 0;
        int len = rendered.length();
        for (int i = 0; i < len; i++) {
            if (rendered.charAt(i) == '\n') {
                generator.writeString(stripCarriageReturn(rendered, from, i));
                from = i + 1;
            }
        }
        if (from < len) {
            generator.writeString(stripCarriageReturn(rendered, from, len));
        }
    }

    private static String stripCarriageReturn(String s, int from, int to) {
        if (to > from && s.charAt(to - 1) == '\r') {
            return s.substring(from, to - 1);
        }
        return s.substring(from, to);
    }

    // --- setters proxied to the underlying converter, so logback XML can
    //     configure this provider with the same knobs as the standard
    //     <stackTrace> + ShortenedThrowableConverter combination. ---

    public void setMaxDepthPerThrowable(int v) {
        converter.setMaxDepthPerThrowable(v);
    }

    public void setMaxLength(int v) {
        converter.setMaxLength(v);
    }

    public void setRootCauseFirst(boolean v) {
        converter.setRootCauseFirst(v);
    }

    public void setShortenedClassNameLength(int v) {
        converter.setShortenedClassNameLength(v);
    }

    public void addExclude(String pattern) {
        converter.addExclude(pattern);
    }

    public void addTruncateAfter(String pattern) {
        converter.addTruncateAfter(pattern);
    }

    public void setInlineHash(boolean v) {
        converter.setInlineHash(v);
    }

    public void setOmitCommonFrames(boolean v) {
        converter.setOmitCommonFrames(v);
    }

    public void addEvaluator(EventEvaluator<ILoggingEvent> evaluator) {
        converter.addEvaluator(evaluator);
    }
}
