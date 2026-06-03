package io.wiretap.http.outgoing.interceptor.rest;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.RestTemplateLogMessageSettings;
import io.wiretap.http.message.settings.body.DefaultBodyParser;
import io.wiretap.metrics.RecordingWiretapMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression test for the overhead metric on the RestTemplate / RestClient
 * failure path (shared {@link RestLoggingInterceptor} base).
 * <p>
 * A read timeout means we waited on the downstream for the whole timeout; that
 * time must be subtracted from {@code wiretap.http.overhead}, not reported as
 * {@code downstreamNanos = 0}.
 */
class RestLoggingInterceptorErrorPathTest {

    private WireMockServer wireMock;

    @BeforeEach
    void start() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void stop() {
        wireMock.stop();
    }

    @Test
    void readTimeout_recordsRealDownstreamTimeNotZero() {
        wireMock.stubFor(get(urlPathEqualTo("/slow"))
                .willReturn(aResponse()
                        .withFixedDelay(2000)
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        RecordingWiretapMetrics metrics = new RecordingWiretapMetrics();
        RestTemplateLoggingInterceptor interceptor = new RestTemplateLoggingInterceptor(
                new RestTemplateLogMessageSettings(), new DefaultBodyParser(null),
                new HttpAccessFieldNames(), null, null, metrics);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofMillis(150));
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getInterceptors().add(interceptor);

        assertThatThrownBy(() -> restTemplate.getForObject(wireMock.baseUrl() + "/slow", String.class))
                .isInstanceOf(ResourceAccessException.class);

        assertThat(metrics.lastOutcome).isEqualTo("exception");
        assertThat(metrics.lastDownstreamNanos)
                .as("the time spent waiting on the slow downstream must be subtracted, not reported as 0")
                .isGreaterThan(0L);
    }
}
