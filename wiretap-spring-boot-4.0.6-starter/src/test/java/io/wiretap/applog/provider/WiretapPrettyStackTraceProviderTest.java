package io.wiretap.applog.provider;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class WiretapPrettyStackTraceProviderTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final Logger LOGGER = (Logger) LoggerFactory.getLogger(WiretapPrettyStackTraceProviderTest.class);
    private static final LoggerContext CONTEXT = (LoggerContext) LoggerFactory.getILoggerFactory();

    private WiretapPrettyStackTraceProvider provider;

    @BeforeEach
    void setUp() {
        provider = new WiretapPrettyStackTraceProvider();
        provider.setContext(CONTEXT);
        provider.start();
    }

    private LoggingEvent eventWith(Throwable throwable) {
        return new LoggingEvent(
                "logger.fqn",
                LOGGER,
                Level.ERROR,
                "boom",
                throwable,
                new Object[0]);
    }

    private JsonNode renderField(LoggingEvent event) throws Exception {
        StringWriter sw = new StringWriter();
        try (JsonGenerator gen = MAPPER.createGenerator(sw)) {
            gen.writeStartObject();
            provider.writeTo(gen, event);
            gen.writeEndObject();
        }
        return MAPPER.readTree(sw.toString());
    }

    @Test
    void writesNothing_whenEventHasNoThrowable() throws Exception {
        JsonNode root = renderField(eventWith(null));
        assertThat(root.has("stack_trace")).isFalse();
    }

    @Test
    void writesArrayOfStrings_whenThrowablePresent() throws Exception {
        JsonNode root = renderField(eventWith(new RuntimeException("boom")));

        JsonNode arr = root.get("stack_trace");
        assertThat(arr).isNotNull();
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.size()).isGreaterThanOrEqualTo(2);
        assertThat(arr.get(0).asString()).contains("RuntimeException").contains("boom");
        boolean hasAtLine = false;
        for (JsonNode line : arr.values()) {
            if (line.asString().trim().startsWith("at ")) { hasAtLine = true; break; }
        }
        assertThat(hasAtLine).as("expected at least one stack frame line").isTrue();
    }

    @Test
    void respectsMaxLength() throws Exception {
        WiretapPrettyStackTraceProvider tight = new WiretapPrettyStackTraceProvider();
        tight.setMaxLength(120);
        tight.setContext(CONTEXT);
        tight.start();
        provider = tight;

        JsonNode arr = renderField(eventWith(new RuntimeException("boom"))).get("stack_trace");
        assertThat(arr.isArray()).isTrue();
        int totalChars = 0;
        for (JsonNode line : arr.values()) totalChars += line.asString().length();
        assertThat(totalChars).isLessThan(300);
    }

    @Test
    void respectsMaxDepthPerThrowable() throws Exception {
        WiretapPrettyStackTraceProvider shallow = new WiretapPrettyStackTraceProvider();
        shallow.setMaxDepthPerThrowable(2);
        shallow.setContext(CONTEXT);
        shallow.start();
        provider = shallow;

        JsonNode arr = renderField(eventWith(new RuntimeException("boom"))).get("stack_trace");
        long atLines = 0;
        for (JsonNode line : arr.values()) {
            if (line.asString().trim().startsWith("at ")) atLines++;
        }
        assertThat(atLines).isLessThanOrEqualTo(2);
    }

    @Test
    void rootCauseAppearsFirst_byDefault() throws Exception {
        RuntimeException root = new RuntimeException("root cause");
        RuntimeException wrapper = new RuntimeException("wrapper", root);

        JsonNode arr = renderField(eventWith(wrapper)).get("stack_trace");
        assertThat(arr.get(0).asString()).contains("root cause");
    }

    @Test
    void fieldName_defaultsToStackTrace() {
        assertThat(provider.getFieldName()).isEqualTo("stack_trace");
    }

    @Test
    void fieldName_canBeOverridden() throws Exception {
        provider.setFieldName("exception_trace");

        JsonNode root = renderField(eventWith(new RuntimeException("boom")));
        assertThat(root.has("stack_trace")).isFalse();
        assertThat(root.get("exception_trace").isArray()).isTrue();
    }
}
