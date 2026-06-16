package io.wiretap.integrationtests.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/echo")
public class EchoController {

    @GetMapping
    public Map<String, Object> echoGet(@RequestParam Map<String, String> params) {
        Map<String, Object> response = new HashMap<>();
        response.put("method", "GET");
        response.put("params", params);
        return response;
    }

    @PostMapping
    public Map<String, Object> echoPost(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        response.put("method", "POST");
        response.put("body", body == null ? Map.of() : body);
        return response;
    }

    /**
     * Multipart upload endpoint. The part is read via {@code getParts()}, which
     * fails if the request stream was drained by the logback-access tee filter —
     * so this is the inbound multipart regression surface.
     */
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestPart("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        response.put("method", "UPLOAD");
        response.put("filename", file.getOriginalFilename());
        response.put("size", file.getSize());
        return response;
    }
}
