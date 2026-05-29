package io.wiretap.http.message.settings.body;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.wiretap.metrics.BodyMetricsContext;
import io.wiretap.metrics.WiretapMetricsImpl;
import io.wiretap.metrics.WiretapMetricsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultBodyParserTest {

    private static final String CARD_LIMITS_URL = "/api/cardlimits/123";
    private static final String OTHER_URL = "/api/orders/123";
    private static final String CARD_LIMITS_JSON =
            "{\"remaining_auth\":\"1000\",\"remaining_cash\":\"500\",\"currency\":\"USD\"}";

    private final HttpBodySettings maskingOn = body(true);
    private final HttpBodySettings maskingOff = body(false);

    private HttpBodySettings body(boolean enableMasking) {
        HttpBodySettings settings = new HttpBodySettings();
        settings.setEnableBodyMasking(enableMasking);
        settings.setMaxBodyLength(10_000);
        return settings;
    }

    private static HttpBodyMaskingHandler matchingMaskingHandler(String urlPattern) {
        return new HttpBodyMaskingHandler() {
            @Override public boolean appliesTo(String url) { return url.matches(urlPattern); }
            @Override public JsonNode mask(JsonNode body) {
                if (body.isObject()) {
                    ObjectNode obj = (ObjectNode) body;
                    if (obj.has("remaining_auth")) obj.put("remaining_auth", "***");
                    if (obj.has("remaining_cash")) obj.put("remaining_cash", "***");
                }
                return body;
            }
        };
    }

    @Test
    void structuralHandler_replacesNamedFields_onMatchingUrl() {
        DefaultBodyParser parser = new DefaultBodyParser(null, List.of(matchingMaskingHandler(".*/cardlimits.*")));

        JsonNode result = parser.parseResponseBody(CARD_LIMITS_JSON, CARD_LIMITS_URL,
                MediaType.APPLICATION_JSON, maskingOn);

        assertThat(result.get("remaining_auth").asText()).isEqualTo("***");
        assertThat(result.get("remaining_cash").asText()).isEqualTo("***");
        assertThat(result.get("currency").asText()).isEqualTo("USD");
    }

    @Test
    void structuralHandler_skipped_onNonMatchingUrl() {
        DefaultBodyParser parser = new DefaultBodyParser(null, List.of(matchingMaskingHandler(".*/cardlimits.*")));

        JsonNode result = parser.parseResponseBody(CARD_LIMITS_JSON, OTHER_URL,
                MediaType.APPLICATION_JSON, maskingOn);

        assertThat(result.get("remaining_auth").asText()).isEqualTo("1000");
    }

    @Test
    void firstMatchingHandlerWins_subsequentNotConsulted() {
        boolean[] secondCalled = {false};
        HttpBodyMaskingHandler first = matchingMaskingHandler(".*/cardlimits.*");
        HttpBodyMaskingHandler second = new HttpBodyMaskingHandler() {
            @Override public boolean appliesTo(String url) { secondCalled[0] = true; return true; }
            @Override public JsonNode mask(JsonNode body) { return body; }
        };
        DefaultBodyParser parser = new DefaultBodyParser(null, List.of(first, second));

        parser.parseResponseBody(CARD_LIMITS_JSON, CARD_LIMITS_URL,
                MediaType.APPLICATION_JSON, maskingOn);

        assertThat(secondCalled[0]).isFalse();
    }

    @Test
    void structuralHandlerAndFieldHandler_composeOnSameBody() {
        HttpBodyFieldMaskingHandler fieldHandler = value -> "currency".equals(value) ? value : value + "-h";
        DefaultBodyParser parser = new DefaultBodyParser(fieldHandler, List.of(matchingMaskingHandler(".*/cardlimits.*")));

        JsonNode result = parser.parseResponseBody(CARD_LIMITS_JSON, CARD_LIMITS_URL,
                MediaType.APPLICATION_JSON, maskingOn);

        // structural handler replaces remaining_*; recursive field-handler then suffixes every remaining text leaf
        assertThat(result.get("remaining_auth").asText()).isEqualTo("***-h");
        assertThat(result.get("currency").asText()).isEqualTo("USD-h");
    }

    @Test
    void maskingDisabled_neitherStructuralNorRecursiveRuns() {
        boolean[] structuralCalled = {false};
        HttpBodyMaskingHandler spy = new HttpBodyMaskingHandler() {
            @Override public boolean appliesTo(String url) { structuralCalled[0] = true; return true; }
            @Override public JsonNode mask(JsonNode body) { return body; }
        };
        HttpBodyFieldMaskingHandler fieldHandler = value -> { throw new AssertionError("must not be called"); };
        DefaultBodyParser parser = new DefaultBodyParser(fieldHandler, List.of(spy));

        JsonNode result = parser.parseResponseBody(CARD_LIMITS_JSON, CARD_LIMITS_URL,
                MediaType.APPLICATION_JSON, maskingOff);

        assertThat(structuralCalled[0]).isFalse();
        assertThat(result.get("remaining_auth").asText()).isEqualTo("1000");
    }

    @Test
    void backwardCompatibility_singleArgConstructor() {
        HttpBodyFieldMaskingHandler fieldHandler = value -> "MASKED";
        DefaultBodyParser parser = new DefaultBodyParser(fieldHandler);

        JsonNode result = parser.parseResponseBody(CARD_LIMITS_JSON, CARD_LIMITS_URL,
                MediaType.APPLICATION_JSON, maskingOn);

        assertThat(result.get("remaining_auth").asText()).isEqualTo("MASKED");
    }

    @Test
    void phaseTimers_carryProvidedMetricsContext_whenDetailedTimingsOn() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WiretapMetricsProperties props = new WiretapMetricsProperties();
        props.setDetailedTimings(true);
        DefaultBodyParser parser = new DefaultBodyParser(null, List.of(), new WiretapMetricsImpl(registry, props));

        // 5-arg overload threads the caller's context into wiretap.body.phase tags
        parser.parseResponseBody(CARD_LIMITS_JSON, OTHER_URL, MediaType.APPLICATION_JSON, maskingOn,
                new BodyMetricsContext("outgoing", "feign", "json"));

        Timer parse = registry.find("wiretap.body.phase")
                .tags("phase", "parse", "direction", "outgoing", "client", "feign", "content_type_class", "json")
                .timer();
        assertThat(parse).as("parse phase tagged with the supplied context").isNotNull();
        assertThat(parse.count()).isEqualTo(1);
        assertThat(registry.find("wiretap.body.phase").tags("client", "unknown").timer())
                .as("HTTP phase timers must no longer fall back to the NONE/unknown context")
                .isNull();
    }
}
