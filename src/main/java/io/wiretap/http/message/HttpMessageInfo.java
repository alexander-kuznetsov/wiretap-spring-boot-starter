package io.wiretap.http.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(value = JsonInclude.Include.NON_EMPTY)
public class HttpMessageInfo {

    @JsonProperty("return_code")
    private Integer returnCode;

    @JsonProperty("http_method")
    private String httpMethod;

    @JsonProperty("direction")
    private RequestDirection requestDirection;

    @JsonProperty("request_url")
    private String requestUrl;

    @JsonProperty("protocol")
    private String protocol;

    @JsonProperty("duration")
    private Long elapsedTime;

    @JsonProperty("source_port")
    private Integer sourcePort; // Local port used for the outbound connection (set only for outbound requests).

    @JsonProperty("request_headers")
    private Map<String, String> requestHeaders;

    @JsonProperty("response_headers")
    private Map<String, String> responseHeaders;

    @JsonProperty("request_params")
    private Map<String, List<String>> requestParams;

    @JsonProperty("request_body")
    private String requestBody;

    @JsonProperty("request_body_length")
    private long requestBodyLength;

    @JsonProperty("response_body")
    private String responseBody;

    @JsonProperty("response_body_length")
    private long responseBodyLength;

    @JsonProperty("xml_body_type")
    private String xmlBodyType;

    public enum RequestDirection {
        INCOMING,
        OUTGOING
    }
}
