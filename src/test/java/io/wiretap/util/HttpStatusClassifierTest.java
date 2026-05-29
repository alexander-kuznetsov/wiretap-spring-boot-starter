package io.wiretap.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpStatusClassifierTest {

    @Test
    void outcome_mapsRangesAndExceptionSentinel() {
        assertThat(HttpStatusClassifier.outcome(-1)).isEqualTo("exception");
        assertThat(HttpStatusClassifier.outcome(200)).isEqualTo("success");
        assertThat(HttpStatusClassifier.outcome(302)).isEqualTo("success");
        assertThat(HttpStatusClassifier.outcome(404)).isEqualTo("client_error");
        assertThat(HttpStatusClassifier.outcome(503)).isEqualTo("server_error");
        assertThat(HttpStatusClassifier.outcome(100)).isEqualTo("other");
        assertThat(HttpStatusClassifier.outcome(0)).isEqualTo("other");
    }

    @Test
    void statusGroup_mapsBucketsAndExceptionSentinel() {
        assertThat(HttpStatusClassifier.statusGroup(-1)).isEqualTo("exception");
        assertThat(HttpStatusClassifier.statusGroup(204)).isEqualTo("2xx");
        assertThat(HttpStatusClassifier.statusGroup(301)).isEqualTo("3xx");
        assertThat(HttpStatusClassifier.statusGroup(400)).isEqualTo("4xx");
        assertThat(HttpStatusClassifier.statusGroup(500)).isEqualTo("5xx");
        assertThat(HttpStatusClassifier.statusGroup(100)).isEqualTo("other");
    }
}
