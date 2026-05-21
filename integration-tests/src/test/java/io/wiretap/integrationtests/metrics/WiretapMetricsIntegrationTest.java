package io.wiretap.integrationtests.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.wiretap.integrationtests.support.WiretapIntegrationTestBase;
import io.wiretap.metrics.WiretapMetrics;
import io.wiretap.metrics.WiretapMetricsImpl;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the {@code WiretapMetrics} contract holds end-to-end:
 * an HTTP round-trip lifts {@code wiretap.http.overhead} counts in both the
 * {@code servlet} (incoming) and {@code resttemplate} (outgoing) dimensions.
 */
class WiretapMetricsIntegrationTest extends WiretapIntegrationTestBase {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    MeterRegistry meterRegistry;

    @Autowired
    WiretapMetrics wiretapMetrics;

    @LocalServerPort
    int port;

    @Test
    void wiretapMetricsBean_isBackedByMicrometerWhenMeterRegistryAvailable() {
        assertThat(wiretapMetrics).isInstanceOf(WiretapMetricsImpl.class);
    }

    @Test
    void outboundCall_recordsBothIncomingAndOutgoingHttpOverheadTimers() {
        String marker = "metrics-rest-template";
        String relayUrl = "/api/outbound/rest-template?port=" + port + "&marker=" + marker;

        restTemplate.getForEntity(relayUrl, Object.class);

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Timer outgoing = meterRegistry.find("wiretap.http.overhead")
                    .tag("direction", "outgoing")
                    .tag("client", "resttemplate")
                    .timer();
            assertThat(outgoing).isNotNull();
            assertThat(outgoing.count()).isGreaterThanOrEqualTo(1);

            Timer incoming = meterRegistry.find("wiretap.http.overhead")
                    .tag("direction", "incoming")
                    .tag("client", "servlet")
                    .timer();
            assertThat(incoming).isNotNull();
            assertThat(incoming.count()).isGreaterThanOrEqualTo(2); // /api/outbound + /api/echo
        });

        assertThat(meterRegistry.find("wiretap.http.requests")
                .tag("direction", "outgoing")
                .tag("client", "resttemplate")
                .counter())
                .as("counter co-emitted with the timer")
                .isNotNull();

        assertThat(meterRegistry.find("wiretap.http.body.size")
                .tag("direction", "outgoing")
                .tag("client", "resttemplate")
                .summary())
                .as("response body size DistributionSummary")
                .isNotNull();
    }

    @TestConfiguration
    static class TestMeterRegistryConfig {
        @Bean
        @Primary
        MeterRegistry simpleMeterRegistry() {
            return new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        }
    }
}
