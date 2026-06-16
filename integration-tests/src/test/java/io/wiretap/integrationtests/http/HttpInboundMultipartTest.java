package io.wiretap.integrationtests.http;

import io.wiretap.integrationtests.support.JsonLogCapture;
import io.wiretap.integrationtests.support.WiretapIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the logback-access {@code TeeFilter} draining multipart
 * request streams. Wiretap enables teeing by default; the stock tee filter buffers
 * the whole body for non-form-urlencoded requests, which drains the multipart
 * stream so {@code request.getParts()} sees nothing and {@code @RequestPart} comes
 * up empty. The content-type-aware tee filter must skip teeing for multipart so the
 * controller still receives the file.
 */
class HttpInboundMultipartTest extends WiretapIntegrationTestBase {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    @SuppressWarnings("unchecked")
    void multipartUploadReachesControllerAndIsStillLogged(CapturedOutput output) {
        byte[] content = "id,name\n1,alice\n2,bob\n".getBytes(StandardCharsets.UTF_8);
        ByteArrayResource filePart = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "data.csv";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/echo/upload", new HttpEntity<>(body, headers), Map.class);

        // The controller actually received the part — proves the stream was not
        // drained by teeing. Before the fix this returned 400 (missing part).
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().get("filename")).isEqualTo("data.csv");
        assertThat(((Number) response.getBody().get("size")).intValue()).isEqualTo(content.length);

        // The upload is still access-logged (skipping teeing must not drop the entry).
        Map<String, Object> log = JsonLogCapture.awaitMatching(output, e -> {
            if (!"INCOMING".equals(JsonLogCapture.at(e, "http_info.direction"))) {
                return false;
            }
            String url = JsonLogCapture.at(e, "http_info.request_url");
            return url != null && url.contains("/api/echo/upload");
        });
        assertThat((Integer) JsonLogCapture.at(log, "http_info.return_code")).isEqualTo(200);
    }
}
