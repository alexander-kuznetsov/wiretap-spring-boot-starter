package io.wiretap.http.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
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

    /**
     * Converts this object to a {@link LinkedHashMap} using the supplied field names,
     * replicating the {@code @JsonInclude(NON_EMPTY)} behaviour (null / empty values excluded).
     */
    public Map<String, Object> toMap(HttpAccessFieldNames f) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (returnCode != null) map.put(f.getReturnCode(), returnCode);
        if (httpMethod != null && !httpMethod.isEmpty()) map.put(f.getMethod(), httpMethod);
        if (requestDirection != null) map.put(f.getDirection(), requestDirection);
        if (requestUrl != null && !requestUrl.isEmpty()) map.put(f.getUrl(), requestUrl);
        if (protocol != null && !protocol.isEmpty()) map.put(f.getProtocol(), protocol);
        if (elapsedTime != null) map.put(f.getDuration(), elapsedTime);
        if (sourcePort != null) map.put(f.getSourcePort(), sourcePort);
        if (requestHeaders != null && !requestHeaders.isEmpty()) map.put(f.getRequestHeaders(), requestHeaders);
        if (responseHeaders != null && !responseHeaders.isEmpty()) map.put(f.getResponseHeaders(), responseHeaders);
        if (requestParams != null && !requestParams.isEmpty()) map.put(f.getRequestParams(), requestParams);
        if (requestBody != null && !requestBody.isEmpty()) map.put(f.getRequestBody(), requestBody);
        map.put(f.getRequestBodyLength(), requestBodyLength);
        if (responseBody != null && !responseBody.isEmpty()) map.put(f.getResponseBody(), responseBody);
        map.put(f.getResponseBodyLength(), responseBodyLength);
        if (xmlBodyType != null && !xmlBodyType.isEmpty()) map.put(f.getXmlBodyType(), xmlBodyType);
        return map;
    }
}
