package io.wiretap.http.message.settings.body;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    private static HttpBodyMasker matchingMasker(String urlPattern) {
        return new HttpBodyMasker() {
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
    void structuralMasker_replacesNamedFields_onMatchingUrl() {
        DefaultBodyParser parser = new DefaultBodyParser(null, List.of(matchingMasker(".*/cardlimits.*")));

        JsonNode result = parser.parseResponseBody(CARD_LIMITS_JSON, CARD_LIMITS_URL,
                MediaType.APPLICATION_JSON, maskingOn);

        assertThat(result.get("remaining_auth").asText()).isEqualTo("***");
        assertThat(result.get("remaining_cash").asText()).isEqualTo("***");
        assertThat(result.get("currency").asText()).isEqualTo("USD");
    }

    @Test
    void structuralMasker_skipped_onNonMatchingUrl() {
        DefaultBodyParser parser = new DefaultBodyParser(null, List.of(matchingMasker(".*/cardlimits.*")));

        JsonNode result = parser.parseResponseBody(CARD_LIMITS_JSON, OTHER_URL,
                MediaType.APPLICATION_JSON, maskingOn);

        assertThat(result.get("remaining_auth").asText()).isEqualTo("1000");
    }

    @Test
    void firstMatchingMaskerWins_subsequentNotConsulted() {
        boolean[] secondCalled = {false};
        HttpBodyMasker first = matchingMasker(".*/cardlimits.*");
        HttpBodyMasker second = new HttpBodyMasker() {
            @Override public boolean appliesTo(String url) { secondCalled[0] = true; return true; }
            @Override public JsonNode mask(JsonNode body) { return body; }
        };
        DefaultBodyParser parser = new DefaultBodyParser(null, List.of(first, second));

        parser.parseResponseBody(CARD_LIMITS_JSON, CARD_LIMITS_URL,
                MediaType.APPLICATION_JSON, maskingOn);

        assertThat(secondCalled[0]).isFalse();
    }

    @Test
    void structuralMaskerAndHandler_composeOnSameBody() {
        HttpBodyMaskingHandler handler = value -> "currency".equals(value) ? value : value + "-h";
        DefaultBodyParser parser = new DefaultBodyParser(handler, List.of(matchingMasker(".*/cardlimits.*")));

        JsonNode result = parser.parseResponseBody(CARD_LIMITS_JSON, CARD_LIMITS_URL,
                MediaType.APPLICATION_JSON, maskingOn);

        // structural masker replaces remaining_*; recursive handler then suffixes every remaining text leaf
        assertThat(result.get("remaining_auth").asText()).isEqualTo("***-h");
        assertThat(result.get("currency").asText()).isEqualTo("USD-h");
    }

    @Test
    void maskingDisabled_neitherStructuralNorRecursiveRuns() {
        boolean[] structuralCalled = {false};
        HttpBodyMasker spy = new HttpBodyMasker() {
            @Override public boolean appliesTo(String url) { structuralCalled[0] = true; return true; }
            @Override public JsonNode mask(JsonNode body) { return body; }
        };
        HttpBodyMaskingHandler handler = value -> { throw new AssertionError("must not be called"); };
        DefaultBodyParser parser = new DefaultBodyParser(handler, List.of(spy));

        JsonNode result = parser.parseResponseBody(CARD_LIMITS_JSON, CARD_LIMITS_URL,
                MediaType.APPLICATION_JSON, maskingOff);

        assertThat(structuralCalled[0]).isFalse();
        assertThat(result.get("remaining_auth").asText()).isEqualTo("1000");
    }

    @Test
    void backwardCompatibility_singleArgConstructor() {
        HttpBodyMaskingHandler handler = value -> "MASKED";
        DefaultBodyParser parser = new DefaultBodyParser(handler);

        JsonNode result = parser.parseResponseBody(CARD_LIMITS_JSON, CARD_LIMITS_URL,
                MediaType.APPLICATION_JSON, maskingOn);

        assertThat(result.get("remaining_auth").asText()).isEqualTo("MASKED");
    }
}
