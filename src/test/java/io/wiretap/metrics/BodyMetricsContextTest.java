package io.wiretap.metrics;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

class BodyMetricsContextTest {

    @Test
    void classify_returnsOtherForNull() {
        assertThat(BodyMetricsContext.classify(null)).isEqualTo("other");
    }

    @Test
    void classify_recognisesApplicationJson() {
        assertThat(BodyMetricsContext.classify(MediaType.APPLICATION_JSON)).isEqualTo("json");
        assertThat(BodyMetricsContext.classify(MediaType.parseMediaType("application/json; charset=utf-8")))
                .isEqualTo("json");
    }

    @Test
    void classify_recognisesXmlVariants() {
        assertThat(BodyMetricsContext.classify(MediaType.APPLICATION_XML)).isEqualTo("xml");
        assertThat(BodyMetricsContext.classify(MediaType.TEXT_XML)).isEqualTo("xml");
        assertThat(BodyMetricsContext.classify(MediaType.parseMediaType("application/soap+xml")))
                .isEqualTo("xml");
    }

    @Test
    void classify_recognisesTextAndBinary() {
        assertThat(BodyMetricsContext.classify(MediaType.TEXT_PLAIN)).isEqualTo("text");
        assertThat(BodyMetricsContext.classify(MediaType.APPLICATION_OCTET_STREAM)).isEqualTo("binary");
    }

    @Test
    void classify_fallsThroughToOtherForUnknownTypes() {
        assertThat(BodyMetricsContext.classify(MediaType.IMAGE_PNG)).isEqualTo("other");
    }
}
