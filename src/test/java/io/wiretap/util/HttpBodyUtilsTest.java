package io.wiretap.util;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

class HttpBodyUtilsTest {

    @Test
    void bypassesTeeingForMultipartImagesAndBinaryStreamingTypes() {
        assertThat(HttpBodyUtils.shouldBypassTeeBuffering(MediaType.MULTIPART_FORM_DATA)).isTrue();
        assertThat(HttpBodyUtils.shouldBypassTeeBuffering(MediaType.parseMediaType("multipart/form-data;boundary=xyz"))).isTrue();
        assertThat(HttpBodyUtils.shouldBypassTeeBuffering(MediaType.parseMediaType("multipart/mixed"))).isTrue();
        assertThat(HttpBodyUtils.shouldBypassTeeBuffering(MediaType.parseMediaType("multipart/related"))).isTrue();
        assertThat(HttpBodyUtils.shouldBypassTeeBuffering(MediaType.APPLICATION_OCTET_STREAM)).isTrue();
        assertThat(HttpBodyUtils.shouldBypassTeeBuffering(MediaType.parseMediaType("binary/octet-stream"))).isTrue();
        assertThat(HttpBodyUtils.shouldBypassTeeBuffering(MediaType.APPLICATION_PDF)).isTrue();
        assertThat(HttpBodyUtils.shouldBypassTeeBuffering(MediaType.IMAGE_PNG)).isTrue();
        assertThat(HttpBodyUtils.shouldBypassTeeBuffering(MediaType.parseMediaType("image/svg+xml"))).isTrue();
        assertThat(HttpBodyUtils.shouldBypassTeeBuffering(MediaType.TEXT_EVENT_STREAM)).isTrue();
    }

    @Test
    void doesNotBypassParseableTypesNorFormUrlencoded() {
        assertThat(HttpBodyUtils.shouldBypassTeeBuffering(MediaType.APPLICATION_JSON)).isFalse();
        assertThat(HttpBodyUtils.shouldBypassTeeBuffering(MediaType.parseMediaType("application/json;charset=UTF-8"))).isFalse();
        assertThat(HttpBodyUtils.shouldBypassTeeBuffering(MediaType.TEXT_PLAIN)).isFalse();
        assertThat(HttpBodyUtils.shouldBypassTeeBuffering(MediaType.APPLICATION_XML)).isFalse();
        // Intentionally still teed: logback-access handles it without draining params.
        assertThat(HttpBodyUtils.shouldBypassTeeBuffering(MediaType.APPLICATION_FORM_URLENCODED)).isFalse();
        assertThat(HttpBodyUtils.shouldBypassTeeBuffering(null)).isFalse();
    }
}
