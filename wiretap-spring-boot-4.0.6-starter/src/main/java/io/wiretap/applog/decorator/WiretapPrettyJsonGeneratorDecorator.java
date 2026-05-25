package io.wiretap.applog.decorator;

import tools.jackson.core.JsonGenerator;
import net.logstash.logback.decorate.JsonGeneratorDecorator;

/**
 * Overlay stub for the SB 4.0.6 / Jackson 3 build.
 *
 * <p>The canonical (Jackson 2) version of this class installs a custom
 * {@code DefaultPrettyPrinter} via {@code JsonGenerator.setPrettyPrinter(...)}
 * to make scalar arrays render multi-line under {@code wiretap.pretty-print=true}.
 * Jackson 3 removed runtime pretty-printer mutation from {@code JsonGenerator} —
 * pretty-printing is now configured through {@code MapperBuilder.defaultPrettyPrinter(...)},
 * which {@code JsonGeneratorDecorator} cannot reach.
 *
 * <p>Logback XML still references this class so the configuration loader does
 * not fail; behaviour is a no-op. SB 4.0.6 pretty-print already requires a
 * different decorator path (see logstash-logback-encoder 9.0
 * {@code PrettyPrintingDecorator}) — wiring that up is out of scope here.
 * Setters are kept so XML configuration parses without errors.
 */
public class WiretapPrettyJsonGeneratorDecorator implements JsonGeneratorDecorator {

    @Override
    public JsonGenerator decorate(JsonGenerator generator) {
        return generator;
    }

    public void setRootSeparator(String rootSeparator) {
        // no-op: see class javadoc.
    }

    public void setSpacesInObjectEntries(boolean spacesInObjectEntries) {
        // no-op: see class javadoc.
    }
}
