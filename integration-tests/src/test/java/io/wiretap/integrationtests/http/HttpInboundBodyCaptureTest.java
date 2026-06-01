package io.wiretap.integrationtests.http;

import io.wiretap.integrationtests.support.JsonLogCapture;
import io.wiretap.integrationtests.support.WiretapIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Inbound request bodies must be captured out of the box. Wiretap turns on the
 * logback-access {@code TeeFilter} by default via
 * {@code WiretapTeeFilterDefaultsEnvironmentPostProcessor}; this test sets no
 * {@code logback.access.tee-filter.enabled} anywhere (see
 * {@code application-test.yml}), yet the posted body still reaches the access log.
 */
class HttpInboundBodyCaptureTest extends WiretapIntegrationTestBase {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void inboundRequestBodyIsCapturedWithoutExplicitTeeFilter(CapturedOutput output) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(
                "{\"marker\":\"body-capture\",\"amount\":4242}", headers);

        restTemplate.postForEntity("/api/echo", request, Map.class);

        Map<String, Object> log = JsonLogCapture.awaitMatching(output, e -> {
            if (!"INCOMING".equals(JsonLogCapture.at(e, "http_info.direction"))) {
                return false;
            }
            Object body = JsonLogCapture.at(e, "http_info.request_body");
            return body instanceof String s && s.contains("body-capture");
        });

        String requestBody = JsonLogCapture.at(log, "http_info.request_body");
        assertThat(requestBody).contains("body-capture").contains("4242");

        Number requestBodyLength = JsonLogCapture.at(log, "http_info.request_body_length");
        assertThat(requestBodyLength).isNotNull();
        assertThat(requestBodyLength.intValue()).isPositive();
    }
}
