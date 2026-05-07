package io.wiretap.http.message;

import io.wiretap.http.message.HttpMessageInfo.RequestDirection;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpMessageInfoTest {

    private final HttpAccessFieldNames defaults = new HttpAccessFieldNames();

    @Test
    void toMap_withFullPayload_includesAllFields() {
        HttpMessageInfo info = HttpMessageInfo.builder()
                .returnCode(200)
                .httpMethod("POST")
                .requestDirection(RequestDirection.INCOMING)
                .requestUrl("/api/v1/users")
                .protocol("HTTP/1.1")
                .elapsedTime(42L)
                .sourcePort(54321)
                .requestHeaders(Map.of("X-Trace", "abc"))
                .responseHeaders(Map.of("Content-Type", "application/json"))
                .requestParams(Map.of("page", List.of("1")))
                .requestBody("{\"name\":\"Alice\"}")
                .requestBodyLength(16)
                .responseBody("{\"id\":1}")
                .responseBodyLength(8)
                .xmlBodyType("Envelope")
                .build();

        Map<String, Object> map = info.toMap(defaults);

        assertThat(map)
                .containsEntry("return_code", 200)
                .containsEntry("http_method", "POST")
                .containsEntry("direction", RequestDirection.INCOMING)
                .containsEntry("request_url", "/api/v1/users")
                .containsEntry("protocol", "HTTP/1.1")
                .containsEntry("duration", 42L)
                .containsEntry("source_port", 54321)
                .containsEntry("request_body", "{\"name\":\"Alice\"}")
                .containsEntry("request_body_length", 16L)
                .containsEntry("response_body", "{\"id\":1}")
                .containsEntry("response_body_length", 8L)
                .containsEntry("xml_body_type", "Envelope");
    }

    @Test
    void toMap_omitsNullAndEmptyFieldsButKeepsPrimitiveLengths() {
        HttpMessageInfo info = HttpMessageInfo.builder()
                .returnCode(204)
                .httpMethod("GET")
                .requestDirection(RequestDirection.OUTGOING)
                .requestUrl("/health")
                .build();

        Map<String, Object> map = info.toMap(defaults);

        assertThat(map).containsKeys("return_code", "http_method", "direction", "request_url",
                "request_body_length", "response_body_length");
        assertThat(map).doesNotContainKeys("protocol", "duration", "source_port",
                "request_headers", "response_headers", "request_params",
                "request_body", "response_body", "xml_body_type");
        assertThat(map.get("request_body_length")).isEqualTo(0L);
        assertThat(map.get("response_body_length")).isEqualTo(0L);
    }

    @Test
    void toMap_honoursCustomFieldNames() {
        HttpAccessFieldNames custom = new HttpAccessFieldNames();
        custom.setReturnCode("status");
        custom.setDuration("elapsed_ms");
        custom.setUrl("path");

        HttpMessageInfo info = HttpMessageInfo.builder()
                .returnCode(200)
                .elapsedTime(15L)
                .requestUrl("/foo")
                .build();

        Map<String, Object> map = info.toMap(custom);

        assertThat(map)
                .containsEntry("status", 200)
                .containsEntry("elapsed_ms", 15L)
                .containsEntry("path", "/foo")
                .doesNotContainKeys("return_code", "duration", "request_url");
    }

    @Test
    void toMap_emptyMessageInfo_onlyEmitsPrimitiveLengths() {
        HttpMessageInfo info = HttpMessageInfo.builder().build();

        Map<String, Object> map = info.toMap(defaults);

        assertThat(map).containsOnlyKeys("request_body_length", "response_body_length");
    }
}
