package io.wiretap.integrationtests.kafka;

import io.wiretap.integrationtests.support.JsonLogCapture;
import io.wiretap.integrationtests.support.WiretapIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaValueMaskingTest extends WiretapIntegrationTestBase {

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void secretsTopicValueIsMasked(CapturedOutput output) {
        kafkaTemplate.send("secrets.test", "masked-key", "super-secret-payload");

        Map<String, Object> log = JsonLogCapture.awaitMatching(output, e -> {
            if (!"OUTGOING".equals(JsonLogCapture.at(e, "kafka_info.direction"))) return false;
            return "secrets.test".equals(JsonLogCapture.at(e, "kafka_info.topic"));
        });

        assertThat((String) JsonLogCapture.at(log, "kafka_info.value")).isEqualTo("***");
        assertThat((String) JsonLogCapture.at(log, "kafka_info.value")).doesNotContain("super-secret-payload");
    }
}
