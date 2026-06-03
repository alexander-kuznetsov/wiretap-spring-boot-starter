package io.wiretap.http.outgoing.interceptor.feignclient;

import feign.Client;
import feign.Request;
import io.wiretap.http.message.settings.FeignClientMessageSettings;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.body.DefaultBodyParser;
import io.wiretap.metrics.RecordingWiretapMetrics;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression test for the overhead metric on the Feign failure path.
 * <p>
 * The wrapped client hangs ~80ms and then throws; that downstream wait must be
 * subtracted from {@code wiretap.http.overhead} instead of being reported as
 * {@code downstreamNanos = 0}.
 */
class FeignClientWrapperErrorPathTest {

    @Test
    void downstreamError_recordsRealDownstreamTimeNotZero() {
        RecordingWiretapMetrics metrics = new RecordingWiretapMetrics();

        Client failingAfterDelay = (request, options) -> {
            try {
                Thread.sleep(80);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new IOException("downstream blew up");
        };

        FeignClientWrapper wrapper = new FeignClientWrapper(
                failingAfterDelay, new DefaultBodyParser(null), new FeignClientMessageSettings(),
                new HttpAccessFieldNames(), null, null, metrics);

        Map<String, Collection<String>> headers = new HashMap<>();
        Request request = Request.create(
                Request.HttpMethod.GET, "http://localhost/slow", headers, Request.Body.empty(), null);

        assertThatThrownBy(() -> wrapper.execute(request, new Request.Options()))
                .isInstanceOf(IOException.class);

        assertThat(metrics.lastOutcome).isEqualTo("exception");
        assertThat(metrics.lastDownstreamNanos)
                .as("the downstream wait must be subtracted from overhead, not reported as 0")
                .isGreaterThan(0L);
    }
}
