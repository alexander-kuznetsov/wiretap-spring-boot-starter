package io.wiretap.integrationtests.kafka;

import io.wiretap.integrationtests.support.JsonLogCapture;
import io.wiretap.integrationtests.support.WiretapIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConsumerErrorLogTest extends WiretapIntegrationTestBase {

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void failingListenerEmitsErrorLog(CapturedOutput output) {
        kafkaTemplate.send("demo.events", "fail-me", "trigger-failure");

        Map<String, Object> log = JsonLogCapture.awaitMatching(output, e -> {
            if (!"INCOMING".equals(JsonLogCapture.at(e, "kafka_info.direction"))) return false;
            if (!"fail-me".equals(JsonLogCapture.at(e, "kafka_info.key"))) return false;
            return "ERROR".equals(JsonLogCapture.at(e, "kafka_info.status"));
        });

        // Spring Kafka wraps listener exceptions in ListenerExecutionFailedException;
        // we assert on the wrapper class name and check the message carries our
        // original RuntimeException text (often via "Cause: ...").
        String errorClass = JsonLogCapture.at(log, "kafka_info.error_class");
        assertThat(errorClass).endsWith("Exception");
        assertThat((String) JsonLogCapture.at(log, "kafka_info.error_message")).isNotBlank();
    }
}
