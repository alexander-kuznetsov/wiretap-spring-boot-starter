package io.wiretap.http.message;

public record BufferedHttpMessageInfo(
        String requestBody,
        long requestBodyLength,
        String responseBody,
        long responseBodyLength
) {
}
