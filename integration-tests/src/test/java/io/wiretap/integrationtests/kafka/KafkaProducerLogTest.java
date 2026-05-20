package io.wiretap.integrationtests.kafka;

import io.wiretap.integrationtests.support.JsonLogCapture;
import io.wiretap.integrationtests.support.WiretapIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaProducerLogTest extends WiretapIntegrationTestBase {

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void producerSendEmitsOutgoingLog(CapturedOutput output) {
        kafkaTemplate.send("demo.events", "producer-success-key", "producer-success-value");

        Map<String, Object> log = JsonLogCapture.awaitMatching(output, e -> {
            if (!"OUTGOING".equals(JsonLogCapture.at(e, "kafka_info.direction"))) return false;
            if (!"demo.events".equals(JsonLogCapture.at(e, "kafka_info.topic"))) return false;
            return "producer-success-key".equals(JsonLogCapture.at(e, "kafka_info.key"));
        });

        assertThat((String) JsonLogCapture.at(log, "kafka_info.status")).isEqualTo("SUCCESS");
        assertThat((String) JsonLogCapture.at(log, "kafka_info.value")).isEqualTo("producer-success-value");
        assertThat((Integer) JsonLogCapture.at(log, "kafka_info.partition")).isNotNull();
    }
}
