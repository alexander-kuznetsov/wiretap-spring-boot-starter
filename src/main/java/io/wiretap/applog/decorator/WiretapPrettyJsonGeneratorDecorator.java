package io.wiretap.applog.decorator;

import ch.qos.logback.core.CoreConstants;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import net.logstash.logback.decorate.JsonGeneratorDecorator;

/**
 * Pretty-print {@link JsonGeneratorDecorator} used by wiretap under
 * {@code wiretap.pretty-print=true}.
 *
 * <p>Differs from {@code net.logstash.logback.decorate.PrettyPrintingJsonGeneratorDecorator}
 * in one place only — Jackson's {@link DefaultPrettyPrinter} is built with
 * {@link DefaultIndenter#SYSTEM_LINEFEED_INSTANCE} as the <strong>array
 * indenter</strong>. The default Jackson behaviour is to keep scalar arrays
 * on a single line ({@code FixedSpaceIndenter}); that ruins the new
 * {@code stack_trace} array of lines (and any other scalar array in the
 * payload) because each element would still be a string but the array
 * itself would render horizontally. Wiretap's pretty-print mode is
 * positioned as «for human eyes», so all containers — both objects and
 * arrays — are wrapped vertically.
 *
 * <p>The XML setters mirror the logstash decorator so logback configs can
 * tweak the same knobs (rootSeparator, spacesInObjectEntries).
 */
public class WiretapPrettyJsonGeneratorDecorator implements JsonGeneratorDecorator {

    private DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter()
            .withRootSeparator(CoreConstants.EMPTY_STRING)
            .withArrayIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);

    @Override
    public JsonGenerator decorate(JsonGenerator generator) {
        return generator.setPrettyPrinter(prettyPrinter);
    }

    /**
     * Same convention as logstash's decorator: {@code [SPACE]} in the value is
     * interpreted as a literal space (logback trims XML element text).
     */
    public void setRootSeparator(String rootSeparator) {
        prettyPrinter = prettyPrinter.withRootSeparator(
                rootSeparator == null ? null : rootSeparator.replace("[SPACE]", " "));
    }

    public void setSpacesInObjectEntries(boolean spacesInObjectEntries) {
        prettyPrinter = spacesInObjectEntries
                ? prettyPrinter.withSpacesInObjectEntries()
                : prettyPrinter.withoutSpacesInObjectEntries();
    }
}
