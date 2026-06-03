package io.wiretap.http.outgoing.interceptor.webclient;

import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.WebClientLogMessageSettings;
import io.wiretap.http.message.settings.body.DefaultBodyParser;
import io.wiretap.metrics.RecordingWiretapMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression test for the overhead metric on the WebClient failure path.
 * <p>
 * When the downstream exchange fails (timeout / reset), the filter must still
 * subtract the time it spent waiting on that downstream call from
 * {@code wiretap.http.overhead}. It used to pass {@code downstreamNanos = 0},
 * so a request that hung for seconds before failing looked like seconds of
 * wiretap overhead.
 */
class WebClientLoggingFilterErrorPathTest {

    @Test
    void downstreamError_recordsRealDownstreamTimeNotZero() {
        RecordingWiretapMetrics metrics = new RecordingWiretapMetrics();
        WebClientLoggingFilter filter = new WebClientLoggingFilter(
                new WebClientLogMessageSettings(), new DefaultBodyParser(null),
                new HttpAccessFieldNames(), null, null, metrics);

        ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("http://localhost/slow")).build();
        // The downstream call hangs ~100ms and then errors — mimics waiting on a
        // remote system that eventually times out or resets the connection.
        ExchangeFunction failingAfterDelay = req ->
                Mono.delay(Duration.ofMillis(100)).then(Mono.error(new RuntimeException("downstream timed out")));

        assertThatThrownBy(() -> filter.filter(request, failingAfterDelay).block())
                .isInstanceOf(RuntimeException.class);

        assertThat(metrics.httpRequestCount.get()).isEqualTo(1);
        assertThat(metrics.lastOutcome).isEqualTo("exception");
        assertThat(metrics.lastDownstreamNanos)
                .as("downstream wait must be subtracted from overhead, not reported as 0")
                .isGreaterThan(0L);
    }
}
