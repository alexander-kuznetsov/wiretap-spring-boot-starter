package io.wiretap.integrationtests.outbound;

import io.wiretap.integrationtests.support.JsonLogCapture;
import io.wiretap.integrationtests.support.WiretapIntegrationTestBase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OutboundHttpClientsTest extends WiretapIntegrationTestBase {

    @Autowired
    TestRestTemplate restTemplate;

    @LocalServerPort
    int port;

    @ParameterizedTest
    @ValueSource(strings = {"rest-template", "rest-client", "web-client"})
    void outboundCallEmitsOutgoingHttpInfo(String clientPath, CapturedOutput output) {
        String marker = "outbound-" + clientPath;
        String relayUrl = "/api/outbound/" + clientPath + "?port=" + port + "&marker=" + marker;

        restTemplate.getForEntity(relayUrl, Object.class);

        Map<String, Object> log = JsonLogCapture.awaitMatching(output, e -> {
            if (!"OUTGOING".equals(JsonLogCapture.at(e, "http_info.direction"))) return false;
            String url = JsonLogCapture.at(e, "http_info.request_url");
            return url != null
                && url.contains("/api/echo")
                && url.contains("marker=" + marker);
        });

        assertThat((String) JsonLogCapture.at(log, "http_info.http_method")).isEqualTo("GET");
        assertThat((Integer) JsonLogCapture.at(log, "http_info.return_code")).isEqualTo(200);
        assertThat((String) JsonLogCapture.at(log, "http_info.request_url")).contains("/api/echo");
    }
}
