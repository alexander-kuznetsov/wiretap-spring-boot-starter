package io.wiretap.applog.decorator;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for the overlay stub. The Jackson 3 build does not support
 * runtime pretty-printer mutation via {@code JsonGenerator}, so the overlay
 * version of the decorator is a no-op (see its javadoc). This test only
 * verifies that the class loads, {@code decorate(...)} does not throw and
 * the XML setters parse without errors.
 */
class WiretapPrettyJsonGeneratorDecoratorTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Test
    void decorate_returnsGenerator_andDoesNotThrow() throws Exception {
        WiretapPrettyJsonGeneratorDecorator d = new WiretapPrettyJsonGeneratorDecorator();
        StringWriter sw = new StringWriter();
        try (JsonGenerator raw = MAPPER.createGenerator(sw)) {
            JsonGenerator decorated = d.decorate(raw);
            assertThat(decorated).isNotNull();
            decorated.writeStartObject();
            decorated.writeName("x");
            decorated.writeString("1");
            decorated.writeEndObject();
        }
        assertThat(sw.toString()).contains("\"x\"");
    }

    @Test
    void xmlSetters_doNotThrow() {
        WiretapPrettyJsonGeneratorDecorator d = new WiretapPrettyJsonGeneratorDecorator();
        d.setRootSeparator("[SPACE]");
        d.setSpacesInObjectEntries(false);
        d.setSpacesInObjectEntries(true);
    }
}
