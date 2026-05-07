package io.wiretap.http.message.settings;

import lombok.Data;

/**
 * JSON field names emitted inside the {@code http_info} object.
 * Defaults match the Wiretap schema; override any name via
 * {@code wiretap.fields.http.*} in {@code application.yml}.
 */
@Data
public class HttpAccessFieldNames {
    private String returnCode = "return_code";
    private String method = "http_method";
    private String direction = "direction";
    private String url = "request_url";
    private String protocol = "protocol";
    private String duration = "duration";
    private String sourcePort = "source_port";
    private String requestHeaders = "request_headers";
    private String responseHeaders = "response_headers";
    private String requestParams = "request_params";
    private String requestBody = "request_body";
    private String requestBodyLength = "request_body_length";
    private String responseBody = "response_body";
    private String responseBodyLength = "response_body_length";
    private String xmlBodyType = "xml_body_type";
}
