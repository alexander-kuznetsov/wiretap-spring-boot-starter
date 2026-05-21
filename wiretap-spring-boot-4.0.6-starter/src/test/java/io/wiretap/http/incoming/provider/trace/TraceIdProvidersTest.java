package io.wiretap.http.incoming.provider.trace;

import ch.qos.logback.access.common.spi.IAccessEvent;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;
import io.wiretap.http.incoming.SleuthCorrelationId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TraceIdProvidersTest {

    @AfterEach
    void resetStatics() {
        TraceIdProvider.configureFieldName("trace_id");
        SpanIdProvider.configureFieldName("span_id");
    }

    @Test
    void traceIdProvider_emitsValueFromAccessEventAttribute() throws IOException {
        IAccessEvent event = mock(IAccessEvent.class);
        when(event.getAttribute(SleuthCorrelationId.SLEUTH_TRACE_ID.getAttributeName())).thenReturn("0123456789abcdef");

        assertThat(serialize(new TraceIdProvider(), event)).isEqualTo("{\"trace_id\":\"0123456789abcdef\"}");
    }

    @Test
    void traceIdProvider_honoursConfiguredFieldName() throws IOException {
        TraceIdProvider.configureFieldName("trace");
        IAccessEvent event = mock(IAccessEvent.class);
        when(event.getAttribute(SleuthCorrelationId.SLEUTH_TRACE_ID.getAttributeName())).thenReturn("abc");

        assertThat(serialize(new TraceIdProvider(), event)).isEqualTo("{\"trace\":\"abc\"}");
    }

    @Test
    void spanIdProvider_emitsValueFromAccessEventAttribute() throws IOException {
        IAccessEvent event = mock(IAccessEvent.class);
        when(event.getAttribute(SleuthCorrelationId.SLEUTH_SPAN_ID.getAttributeName())).thenReturn("ffeeddccbbaa9988");

        assertThat(serialize(new SpanIdProvider(), event)).isEqualTo("{\"span_id\":\"ffeeddccbbaa9988\"}");
    }

    @Test
    void spanIdProvider_honoursConfiguredFieldName() throws IOException {
        SpanIdProvider.configureFieldName("span");
        IAccessEvent event = mock(IAccessEvent.class);
        when(event.getAttribute(SleuthCorrelationId.SLEUTH_SPAN_ID.getAttributeName())).thenReturn("xyz");

        assertThat(serialize(new SpanIdProvider(), event)).isEqualTo("{\"span\":\"xyz\"}");
    }

    private static <P extends net.logstash.logback.composite.AbstractFieldJsonProvider<IAccessEvent>>
    String serialize(P provider, IAccessEvent event) throws IOException {
        StringWriter w = new StringWriter();
        JsonGenerator gen = new JsonFactory().createGenerator(ObjectWriteContext.empty(), w);
        gen.writeStartObject();
        provider.writeTo(gen, event);
        gen.writeEndObject();
        gen.close();
        return w.toString();
    }
}
