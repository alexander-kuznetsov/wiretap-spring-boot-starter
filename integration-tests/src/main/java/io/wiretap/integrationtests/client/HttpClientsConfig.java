package io.wiretap.integrationtests.client;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class HttpClientsConfig {

    /**
     * Built via {@link RestTemplateBuilder} so that Spring Boot's autoconfigured
     * customizers (including wiretap's interceptor) are applied. A bare
     * {@code new RestTemplate()} skips the customizer chain and ends up
     * uninstrumented.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }
}
