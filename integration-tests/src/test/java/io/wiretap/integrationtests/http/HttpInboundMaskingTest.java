package io.wiretap.integrationtests.http;

import io.wiretap.integrationtests.support.JsonLogCapture;
import io.wiretap.integrationtests.support.WiretapIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpInboundMaskingTest extends WiretapIntegrationTestBase {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void sensitiveQueryParamsAreMasked(CapturedOutput output) {
        restTemplate.getForEntity(
            "/api/echo?marker=masking&phone=79991234567&token=abc",
            Map.class
        );

        Map<String, Object> log = JsonLogCapture.awaitMatching(output, e -> {
            if (!"INCOMING".equals(JsonLogCapture.at(e, "http_info.direction"))) return false;
            Map<String, Object> params = JsonLogCapture.at(e, "http_info.request_params");
            return params != null && "masking".equals(asFirst(params.get("marker")));
        });

        Map<String, Object> params = JsonLogCapture.at(log, "http_info.request_params");
        assertThat(asFirst(params.get("phone"))).isEqualTo("***");
        assertThat(asFirst(params.get("token"))).isEqualTo("***");
        assertThat(asFirst(params.get("marker"))).isEqualTo("masking");
    }

    @SuppressWarnings("unchecked")
    private static String asFirst(Object listOrString) {
        if (listOrString instanceof List<?> list && !list.isEmpty()) {
            return String.valueOf(list.get(0));
        }
        return listOrString == null ? null : listOrString.toString();
    }
}
