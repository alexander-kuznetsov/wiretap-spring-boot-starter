package io.wiretap.integrationtests.kafka;

import io.wiretap.integrationtests.support.JsonLogCapture;
import io.wiretap.integrationtests.support.WiretapIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConsumerLogTest extends WiretapIntegrationTestBase {

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void consumerEmitsIncomingLogWithDuration(CapturedOutput output) {
        kafkaTemplate.send("demo.events", "consumer-success-key", "consumer-success-value");

        Map<String, Object> log = JsonLogCapture.awaitMatching(output, e -> {
            if (!"INCOMING".equals(JsonLogCapture.at(e, "kafka_info.direction"))) return false;
            return "consumer-success-key".equals(JsonLogCapture.at(e, "kafka_info.key"));
        });

        assertThat((String) JsonLogCapture.at(log, "kafka_info.topic")).isEqualTo("demo.events");
        assertThat((String) JsonLogCapture.at(log, "kafka_info.status")).isEqualTo("SUCCESS");
        Number duration = JsonLogCapture.at(log, "kafka_info.duration");
        assertThat(duration).isNotNull();
        assertThat(duration.longValue()).isGreaterThanOrEqualTo(0L);
    }
}
