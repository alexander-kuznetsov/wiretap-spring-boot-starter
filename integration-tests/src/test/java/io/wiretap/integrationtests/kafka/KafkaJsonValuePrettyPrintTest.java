package io.wiretap.integrationtests.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wiretap.integrationtests.support.JsonLogCapture;
import io.wiretap.integrationtests.support.WiretapIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that wiretap renders Kafka {@code key}/{@code value} payloads which
 * happen to be JSON objects/arrays as pretty-printed multi-line strings inside
 * {@code kafka_info}. The field stays a string (never a nested object) to
 * avoid log-aggregator index-type collisions, but the {@code \n} characters in
 * it make the payload render readably in viewers.
 */
class KafkaJsonValuePrettyPrintTest extends WiretapIntegrationTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void producerJsonValue_isPrettyPrintedAndRoundTrips(CapturedOutput output) throws Exception {
        String jsonPayload = "{\"orderId\":\"json-42\",\"items\":[{\"sku\":\"a\"},{\"sku\":\"b\"}]}";
        kafkaTemplate.send("demo.events", "json-pretty-key", jsonPayload);

        Map<String, Object> log = JsonLogCapture.awaitMatching(output, e -> {
            if (!"OUTGOING".equals(JsonLogCapture.at(e, "kafka_info.direction"))) return false;
            return "json-pretty-key".equals(JsonLogCapture.at(e, "kafka_info.key"));
        });

        String value = (String) JsonLogCapture.at(log, "kafka_info.value");
        assertThat(value).contains("\n");
        JsonNode tree = MAPPER.readTree(value);
        assertThat(tree.get("orderId").asText()).isEqualTo("json-42");
        assertThat(tree.get("items").isArray()).isTrue();
        assertThat(tree.get("items")).hasSize(2);
    }
}
