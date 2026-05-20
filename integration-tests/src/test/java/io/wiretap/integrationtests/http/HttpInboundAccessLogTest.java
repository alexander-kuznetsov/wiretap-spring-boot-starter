package io.wiretap.integrationtests.http;

import io.wiretap.integrationtests.support.JsonLogCapture;
import io.wiretap.integrationtests.support.WiretapIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpInboundAccessLogTest extends WiretapIntegrationTestBase {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void getEchoEmitsHttpInfo(CapturedOutput output) {
        ResponseEntity<Map> response = restTemplate.getForEntity("/api/echo?marker=inbound-get", Map.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> log = JsonLogCapture.awaitMatching(output, e -> {
            if (!"INCOMING".equals(JsonLogCapture.at(e, "http_info.direction"))) return false;
            Map<String, Object> params = JsonLogCapture.at(e, "http_info.request_params");
            return params != null && params.containsKey("marker");
        });

        assertThat((String) JsonLogCapture.at(log, "http_info.http_method")).isEqualTo("GET");
        assertThat((Integer) JsonLogCapture.at(log, "http_info.return_code")).isEqualTo(200);
        assertThat((String) JsonLogCapture.at(log, "http_info.request_url")).contains("/api/echo");
    }

    @Test
    void postEchoEmitsHttpInfoWithBody(CapturedOutput output) {
        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/api/echo?marker=inbound-post",
            Map.of("hello", "world"),
            Map.class
        );
        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> log = JsonLogCapture.awaitMatching(output, e -> {
            if (!"INCOMING".equals(JsonLogCapture.at(e, "http_info.direction"))) return false;
            if (!"POST".equals(JsonLogCapture.at(e, "http_info.http_method"))) return false;
            Map<String, Object> params = JsonLogCapture.at(e, "http_info.request_params");
            return params != null && params.containsKey("marker");
        });

        assertThat((Integer) JsonLogCapture.at(log, "http_info.return_code")).isEqualTo(200);
        assertThat((String) JsonLogCapture.at(log, "http_info.request_url")).contains("/api/echo");
    }
}
