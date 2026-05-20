package io.wiretap.integrationtests.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
