package io.wiretap.http.outgoing.interceptor.webclient;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.HttpInfoLogMessageSettings.HttpConfigurableField;
import io.wiretap.http.message.settings.WebClientLogMessageSettings;
import io.wiretap.http.message.settings.body.DefaultBodyParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class WebClientLoggingFilterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WireMockServer wireMock;
    private ListAppender<ILoggingEvent> appender;
    private Logger filterLogger;

    @BeforeEach
    void start() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();

        filterLogger = (Logger) LoggerFactory.getLogger(WebClientLoggingFilter.class);
        appender = new ListAppender<>();
        appender.start();
        filterLogger.addAppender(appender);
        filterLogger.setLevel(Level.INFO);
    }

    @AfterEach
    void stop() {
        filterLogger.detachAppender(appender);
        wireMock.stop();
    }

    @Test
    void successfulRequest_capturesHttpInfoIntoMdc() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/items/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"name\":\"widget\"}")));

        WebClient client = clientWith(new HttpAccessFieldNames());

        String body = client.get()
                .uri(wireMock.baseUrl() + "/items/1")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        assertThat(body).isEqualTo("{\"id\":1,\"name\":\"widget\"}");

        JsonNode logged = capturedHttpInfoJson();
        assertThat(logged.get("return_code").asInt()).isEqualTo(200);
        assertThat(logged.get("http_method").asText()).isEqualTo("GET");
        assertThat(logged.get("direction").asText()).isEqualTo("OUTGOING");
        assertThat(logged.get("request_url").asText()).contains("/items/1");
        assertThat(logged.get("response_body").asText()).contains("widget");
    }

    @Test
    void postRequest_capturesRequestBody() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/orders"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":99}")));

        WebClient client = clientWith(new HttpAccessFieldNames());

        client.post()
                .uri(wireMock.baseUrl() + "/orders")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"sku\":\"ABC\"}")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode logged = capturedHttpInfoJson();
        assertThat(logged.get("return_code").asInt()).isEqualTo(201);
        assertThat(logged.get("request_url").asText()).contains("/orders");
    }

    @Test
    void customFieldNames_renameKeysInMdcPayload() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/ping"))
                .willReturn(aResponse().withStatus(204)));

        HttpAccessFieldNames custom = new HttpAccessFieldNames();
        custom.setReturnCode("status");
        custom.setMethod("verb");
        custom.setUrl("path");

        WebClient client = clientWith(custom);

        client.get().uri(wireMock.baseUrl() + "/ping").retrieve().toBodilessEntity().block();

        JsonNode logged = capturedHttpInfoJson();
        assertThat(logged.has("return_code")).isFalse();
        assertThat(logged.get("status").asInt()).isEqualTo(204);
        assertThat(logged.get("verb").asText()).isEqualTo("GET");
        assertThat(logged.get("path").asText()).contains("/ping");
    }

    @Test
    void excludedUrl_skipsLogging() {
        wireMock.stubFor(get(urlPathEqualTo("/actuator/health"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        WebClientLogMessageSettings settings = new WebClientLogMessageSettings();
        settings.setExcludeRequestPatterns(java.util.List.of(".*/actuator/.*"));
        WebClientLoggingFilter filter = new WebClientLoggingFilter(settings, new DefaultBodyParser(null), new HttpAccessFieldNames(), null, null);
        WebClient client = WebClient.builder().filter(filter).build();

        client.get().uri(wireMock.baseUrl() + "/actuator/health").retrieve().toBodilessEntity().block();

        assertThat(appender.list).isEmpty();
    }

    @Test
    void streamingResponse_skipsBodyBufferingAndLogsMarker() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/events"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream;charset=UTF-8")
                        .withBody("data: one\n\ndata: two\n\n")));

        WebClient client = clientWith(new HttpAccessFieldNames());

        client.get().uri(wireMock.baseUrl() + "/events").retrieve().toBodilessEntity().block();

        JsonNode logged = capturedHttpInfoJson();
        assertThat(logged.get("return_code").asInt()).isEqualTo(200);
        assertThat(logged.get("response_body").asText())
                .contains("streaming response");
        assertThat(logged.get("response_body").asText())
                .doesNotContain("data: one");
    }

    @Test
    void requestBodyVisibilityFalse_skipsRequestBodyCapture() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/orders"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("{}")));

        WebClientLogMessageSettings settings = new WebClientLogMessageSettings();
        settings.getHttpBodySettings().setEnableBodyTruncating(false);
        settings.getVisibilitySettings().put(HttpConfigurableField.REQUEST_BODY, Boolean.FALSE);
        WebClientLoggingFilter filter = new WebClientLoggingFilter(settings, new DefaultBodyParser(null), new HttpAccessFieldNames(), null, null);
        WebClient client = WebClient.builder().filter(filter).build();

        client.post().uri(wireMock.baseUrl() + "/orders")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"sku\":\"ABC\"}")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode logged = capturedHttpInfoJson();
        assertThat(logged.has("request_body"))
                .as("request_body should be omitted when REQUEST_BODY visibility is false")
                .isFalse();
        assertThat(logged.get("return_code").asInt()).isEqualTo(201);
    }

    @Test
    void responseBodyVisibilityFalse_skipsResponseBodyDrain() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/items/2"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":2}")));

        WebClientLogMessageSettings settings = new WebClientLogMessageSettings();
        settings.getHttpBodySettings().setEnableBodyTruncating(false);
        settings.getVisibilitySettings().put(HttpConfigurableField.RESPONSE_BODY, Boolean.FALSE);
        WebClientLoggingFilter filter = new WebClientLoggingFilter(settings, new DefaultBodyParser(null), new HttpAccessFieldNames(), null, null);
        WebClient client = WebClient.builder().filter(filter).build();

        String body = client.get().uri(wireMock.baseUrl() + "/items/2")
                .retrieve().bodyToMono(String.class).block();

        assertThat(body).isEqualTo("{\"id\":2}");
        JsonNode logged = capturedHttpInfoJson();
        assertThat(logged.has("response_body"))
                .as("response_body should be omitted when RESPONSE_BODY visibility is false")
                .isFalse();
    }

    @Test
    void largeResponseBody_truncatesAtMaxBodyLength() throws Exception {
        String big = "x".repeat(2000);
        wireMock.stubFor(get(urlPathEqualTo("/big"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(big)));

        WebClientLogMessageSettings settings = new WebClientLogMessageSettings();
        settings.getHttpBodySettings().setEnableBodyTruncating(false);
        settings.getHttpBodySettings().setMaxBodyLength(100);
        WebClientLoggingFilter filter = new WebClientLoggingFilter(settings, new DefaultBodyParser(null), new HttpAccessFieldNames(), null, null);
        WebClient client = WebClient.builder().filter(filter).build();

        String received = client.get().uri(wireMock.baseUrl() + "/big")
                .retrieve().bodyToMono(String.class).block();

        assertThat(received).hasSize(2000);

        JsonNode logged = capturedHttpInfoJson();
        String responseBody = logged.get("response_body").asText();
        assertThat(responseBody).contains("[truncated]");
        assertThat(responseBody.length()).isLessThan(big.length());
    }

    @Test
    void largeRequestBody_truncatesAtMaxBodyLength() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/upload"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{}")));

        String big = "y".repeat(2000);
        WebClientLogMessageSettings settings = new WebClientLogMessageSettings();
        settings.getHttpBodySettings().setEnableBodyTruncating(false);
        settings.getHttpBodySettings().setMaxBodyLength(100);
        WebClientLoggingFilter filter = new WebClientLoggingFilter(settings, new DefaultBodyParser(null), new HttpAccessFieldNames(), null, null);
        WebClient client = WebClient.builder().filter(filter).build();

        client.post().uri(wireMock.baseUrl() + "/upload")
                .contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                .bodyValue(big)
                .retrieve().bodyToMono(String.class).block();

        JsonNode logged = capturedHttpInfoJson();
        String requestBody = logged.get("request_body").asText();
        assertThat(requestBody).contains("[truncated]");
        assertThat(requestBody.length()).isLessThan(big.length());
    }

    private WebClient clientWith(HttpAccessFieldNames fieldNames) {
        WebClientLogMessageSettings settings = new WebClientLogMessageSettings();
        settings.getHttpBodySettings().setEnableBodyTruncating(false);
        WebClientLoggingFilter filter = new WebClientLoggingFilter(settings, new DefaultBodyParser(null), fieldNames, null, null);
        return WebClient.builder().filter(filter).build();
    }

    private JsonNode capturedHttpInfoJson() throws Exception {
        assertThat(appender.list)
                .as("filter should have emitted exactly one log event")
                .hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        String json = event.getMDCPropertyMap().get("HTTP-REQUEST-LOG");
        assertThat(json).as("MDC entry HTTP-REQUEST-LOG should be present").isNotNull();
        return MAPPER.readTree(json);
    }
}
