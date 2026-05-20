package io.wiretap.integrationtests.http;

import io.wiretap.integrationtests.support.JsonLogCapture;
import io.wiretap.integrationtests.support.WiretapIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TenantAccessFieldProviderTest extends WiretapIntegrationTestBase {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void tenantHeaderProducesTenantField(CapturedOutput output) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Demo-Tenant", "acme");

        restTemplate.exchange(
            "/api/echo?marker=tenant-present",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            Map.class
        );

        Map<String, Object> log = JsonLogCapture.awaitMatching(output, markerMatcher("tenant-present"));

        assertThat((String) log.get("tenant")).isEqualTo("acme");
    }

    @Test
    void missingTenantHeaderOmitsField(CapturedOutput output) {
        restTemplate.getForEntity("/api/echo?marker=tenant-absent", Map.class);

        Map<String, Object> log = JsonLogCapture.awaitMatching(output, markerMatcher("tenant-absent"));

        assertThat(log).doesNotContainKey("tenant");
    }

    private static java.util.function.Predicate<Map<String, Object>> markerMatcher(String marker) {
        return e -> {
            if (!"INCOMING".equals(JsonLogCapture.at(e, "http_info.direction"))) return false;
            Map<String, Object> params = JsonLogCapture.at(e, "http_info.request_params");
            if (params == null) return false;
            Object raw = params.get("marker");
            return raw instanceof List<?> list && !list.isEmpty() && marker.equals(list.get(0));
        };
    }
}
