package io.wiretap.http.incoming.provider.trace;

import ch.qos.logback.access.spi.IAccessEvent;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionKeyProviderTest {

    private final SessionKeyProvider provider = new SessionKeyProvider();
    private StringWriter writer;
    private JsonGenerator generator;

    @BeforeEach
    void setUp() throws IOException {
        writer = new StringWriter();
        generator = new JsonFactory().createGenerator(writer);
        generator.writeStartObject();
    }

    @AfterEach
    void resetStaticState() {
        SessionKeyProvider.configureFieldName("session_key");
        SessionKeyProvider.setSessionKeyHeader("x-session-key");
    }

    @Test
    void writeTo_emitsSessionKeyFromRequestHeader() throws IOException {
        provider.writeTo(generator, eventWith(Map.of("x-session-key", "abc-123"), Map.of()));

        assertThat(close(generator, writer)).isEqualTo("{\"session_key\":\"abc-123\"}");
    }

    @Test
    void writeTo_fallsBackToResponseHeaderWhenRequestHeaderMissing() throws IOException {
        provider.writeTo(generator, eventWith(Map.of(), Map.of("x-session-key", "from-resp")));

        assertThat(close(generator, writer)).isEqualTo("{\"session_key\":\"from-resp\"}");
    }

    @Test
    void writeTo_omitsFieldWhenHeaderAbsentOnBothSides() throws IOException {
        provider.writeTo(generator, eventWith(Map.of(), Map.of()));

        assertThat(close(generator, writer)).isEqualTo("{}");
    }

    @Test
    void writeTo_honoursConfiguredFieldName() throws IOException {
        SessionKeyProvider.configureFieldName("user_session");

        provider.writeTo(generator, eventWith(Map.of("x-session-key", "v"), Map.of()));

        assertThat(close(generator, writer)).isEqualTo("{\"user_session\":\"v\"}");
    }

    @Test
    void writeTo_readsFromConfiguredHeader() throws IOException {
        SessionKeyProvider.setSessionKeyHeader("tcs-session-key");

        provider.writeTo(generator, eventWith(Map.of("tcs-session-key", "v"), Map.of()));

        assertThat(close(generator, writer)).isEqualTo("{\"session_key\":\"v\"}");
    }

    private static IAccessEvent eventWith(Map<String, String> reqHeaders, Map<String, String> respHeaders) {
        IAccessEvent event = mock(IAccessEvent.class);
        when(event.getRequestHeaderMap()).thenReturn(reqHeaders);
        when(event.getResponseHeaderMap()).thenReturn(respHeaders);
        return event;
    }

    private static String close(JsonGenerator gen, StringWriter w) throws IOException {
        gen.writeEndObject();
        gen.close();
        return w.toString();
    }
}
