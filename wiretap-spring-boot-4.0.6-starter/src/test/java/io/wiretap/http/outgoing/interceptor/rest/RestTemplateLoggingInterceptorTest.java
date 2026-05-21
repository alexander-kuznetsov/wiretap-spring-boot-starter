package io.wiretap.http.outgoing.interceptor.rest;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.RestTemplateLogMessageSettings;
import io.wiretap.http.message.settings.body.DefaultBodyParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class RestTemplateLoggingInterceptorTest {

    private static final ObjectMapper MAPPER = tools.jackson.databind.json.JsonMapper.builder().build();

    private WireMockServer wireMock;
    private ListAppender<ILoggingEvent> appender;
    private Logger interceptorLogger;

    @BeforeEach
    void start() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();

        interceptorLogger = (Logger) LoggerFactory.getLogger(RestLoggingInterceptor.class);
        appender = new ListAppender<>();
        appender.start();
        interceptorLogger.addAppender(appender);
        interceptorLogger.setLevel(Level.INFO);
    }

    @AfterEach
    void stop() {
        interceptorLogger.detachAppender(appender);
        wireMock.stop();
    }

    @Test
    void successfulRequest_capturesHttpInfoIntoMdc() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/users/42"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":42,\"name\":\"alice\"}")));

        RestTemplate restTemplate = templateWith(new HttpAccessFieldNames());

        String body = restTemplate.getForObject(wireMock.baseUrl() + "/users/42", String.class);

        assertThat(body).isEqualTo("{\"id\":42,\"name\":\"alice\"}");

        JsonNode loggedHttpInfo = capturedHttpInfoJson();
        assertThat(loggedHttpInfo.get("return_code").asInt()).isEqualTo(200);
        assertThat(loggedHttpInfo.get("http_method").asString()).isEqualTo("GET");
        assertThat(loggedHttpInfo.get("direction").asString()).isEqualTo("OUTGOING");
        assertThat(loggedHttpInfo.get("request_url").asString()).contains("/users/42");
        assertThat(loggedHttpInfo.get("response_body").asString()).contains("alice");
    }

    @Test
    void customFieldNames_renameKeysInMdcPayload() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/ping"))
                .willReturn(aResponse().withStatus(204)));

        HttpAccessFieldNames custom = new HttpAccessFieldNames();
        custom.setReturnCode("status");
        custom.setMethod("verb");
        custom.setUrl("path");

        RestTemplate restTemplate = templateWith(custom);

        restTemplate.getForObject(wireMock.baseUrl() + "/ping", String.class);

        JsonNode logged = capturedHttpInfoJson();
        assertThat(logged.has("return_code")).isFalse();
        assertThat(logged.has("status")).isTrue();
        assertThat(logged.get("status").asInt()).isEqualTo(204);
        assertThat(logged.get("verb").asString()).isEqualTo("GET");
        assertThat(logged.get("path").asString()).contains("/ping");
    }

    @Test
    void excludePattern_skipsLoggingEntirely() {
        wireMock.stubFor(get(urlPathEqualTo("/actuator/health"))
                .willReturn(aResponse().withStatus(200).withBody("{}")));

        RestTemplateLogMessageSettings settings = new RestTemplateLogMessageSettings();
        settings.setExcludeRequestPatterns(java.util.List.of(".*/actuator/.*"));
        RestTemplateLoggingInterceptor interceptor = new RestTemplateLoggingInterceptor(
                settings, new DefaultBodyParser(null), new HttpAccessFieldNames(), null, null);
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(interceptor);

        restTemplate.getForObject(wireMock.baseUrl() + "/actuator/health", String.class);

        assertThat(appender.list).isEmpty();
    }

    @Test
    void requestHeaderInWhitelist_isReflectedInLogPayload() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/whoami"))
                .withHeader("Content-Type", equalTo("application/json"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{}")));

        RestTemplate restTemplate = templateWith(new HttpAccessFieldNames());

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);
        restTemplate.exchange(wireMock.baseUrl() + "/whoami", org.springframework.http.HttpMethod.GET, entity, String.class);

        JsonNode logged = capturedHttpInfoJson();
        JsonNode reqHeaders = logged.get("request_headers");
        assertThat(reqHeaders).isNotNull();
        assertThat(reqHeaders.get("Content-Type").asString()).contains("application/json");
    }

    @Test
    void wildcardResponseHeaders_logEveryHeaderFromResponse() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/items/42"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Trace", "abc-123")
                        .withHeader("X-Tenant", "acme")
                        .withBody("{}")));

        RestTemplateLogMessageSettings settings = new RestTemplateLogMessageSettings();
        settings.setResponseHeaders(java.util.List.of("*"));
        RestTemplateLoggingInterceptor interceptor = new RestTemplateLoggingInterceptor(
                settings, new DefaultBodyParser(null), new HttpAccessFieldNames(), null, null);
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(interceptor);

        restTemplate.getForObject(wireMock.baseUrl() + "/items/42", String.class);

        JsonNode logged = capturedHttpInfoJson();
        JsonNode respHeaders = logged.get("response_headers");
        assertThat(respHeaders).isNotNull();
        assertThat(respHeaders.has("Content-Type")).isTrue();
        assertThat(respHeaders.has("X-Trace")).isTrue();
        assertThat(respHeaders.has("X-Tenant")).isTrue();
        assertThat(respHeaders.get("X-Trace").asString()).isEqualTo("abc-123");
    }

    private RestTemplate templateWith(HttpAccessFieldNames fieldNames) {
        RestTemplateLogMessageSettings settings = new RestTemplateLogMessageSettings();
        settings.getHttpBodySettings().setEnableBodyTruncating(false);
        RestTemplateLoggingInterceptor interceptor = new RestTemplateLoggingInterceptor(
                settings, new DefaultBodyParser(null), fieldNames, null, null);
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(interceptor);
        return restTemplate;
    }

    private JsonNode capturedHttpInfoJson() throws Exception {
        assertThat(appender.list)
                .as("interceptor should have emitted exactly one log event")
                .hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        String json = event.getMDCPropertyMap().get("HTTP-REQUEST-LOG");
        assertThat(json).as("MDC entry HTTP-REQUEST-LOG should be present").isNotNull();
        return MAPPER.readTree(json);
    }
}
