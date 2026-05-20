package io.wiretap.integrationtests.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

@RestController
@RequestMapping("/api/outbound")
public class OutboundController {

    private final RestTemplate restTemplate;
    private final RestClient restClient;
    private final WebClient webClient;

    public OutboundController(RestTemplate restTemplate, RestClient restClient, WebClient webClient) {
        this.restTemplate = restTemplate;
        this.restClient = restClient;
        this.webClient = webClient;
    }

    @GetMapping("/rest-template")
    public Object restTemplate(@RequestParam int port, @RequestParam String marker) {
        return restTemplate.getForObject(targetEcho(port, marker), Object.class);
    }

    @GetMapping("/rest-client")
    public Object restClient(@RequestParam int port, @RequestParam String marker) {
        return restClient.get().uri(targetEcho(port, marker)).retrieve().body(Object.class);
    }

    @GetMapping("/web-client")
    public Object webClient(@RequestParam int port, @RequestParam String marker) {
        return webClient.get().uri(targetEcho(port, marker)).retrieve().bodyToMono(Object.class).block();
    }

    private static String targetEcho(int port, String marker) {
        return "http://localhost:" + port + "/api/echo?marker=" + marker;
    }
}
