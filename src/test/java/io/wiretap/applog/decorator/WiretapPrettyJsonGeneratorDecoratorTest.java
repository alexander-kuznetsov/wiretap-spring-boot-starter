package io.wiretap.applog.decorator;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class WiretapPrettyJsonGeneratorDecoratorTest {

    private static final JsonFactory FACTORY = new JsonFactory();

    private String render(WriteOp body) throws Exception {
        WiretapPrettyJsonGeneratorDecorator decorator = new WiretapPrettyJsonGeneratorDecorator();
        StringWriter sw = new StringWriter();
        try (JsonGenerator raw = FACTORY.createGenerator(sw)) {
            JsonGenerator gen = decorator.decorate(raw);
            body.run(gen);
        }
        return sw.toString();
    }

    @Test
    void scalarArray_renderedMultiLine() throws Exception {
        String out = render(gen -> {
            gen.writeStartObject();
            gen.writeFieldName("stack_trace");
            gen.writeStartArray();
            gen.writeString("first line");
            gen.writeString("second line");
            gen.writeString("third line");
            gen.writeEndArray();
            gen.writeEndObject();
        });

        // The standard PrettyPrintingJsonGeneratorDecorator would put all
        // three strings on one line with a FixedSpaceIndenter. We expect
        // each on its own line — at least three newlines inside the array.
        long newlines = out.chars().filter(c -> c == '\n').count();
        assertThat(newlines).as("expected multi-line scalar array, got:\n%s", out).isGreaterThanOrEqualTo(3);
        // The actual content survives.
        assertThat(out).contains("first line").contains("second line").contains("third line");
    }

    @Test
    void nestedObject_stillMultiLine() throws Exception {
        String out = render(gen -> {
            gen.writeStartObject();
            gen.writeFieldName("payload");
            gen.writeStartObject();
            gen.writeStringField("id", "42");
            gen.writeStringField("name", "alice");
            gen.writeEndObject();
            gen.writeEndObject();
        });

        assertThat(out).contains("\n");
        assertThat(out).contains("\"id\"");
        assertThat(out).contains("\"name\"");
    }

    @Test
    void rootSeparator_overridable() {
        WiretapPrettyJsonGeneratorDecorator d = new WiretapPrettyJsonGeneratorDecorator();
        // Setter does not throw; full XML semantics are covered by the
        // logstash decorator we mirror — here we just sanity-check the
        // surface API stays compatible with the logback XML config.
        d.setRootSeparator("[SPACE]");
        d.setSpacesInObjectEntries(false);
        d.setSpacesInObjectEntries(true);
    }

    @FunctionalInterface
    private interface WriteOp {
        void run(JsonGenerator gen) throws Exception;
    }
}
