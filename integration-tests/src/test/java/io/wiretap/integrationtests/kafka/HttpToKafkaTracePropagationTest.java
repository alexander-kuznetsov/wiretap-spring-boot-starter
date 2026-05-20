package io.wiretap.integrationtests.kafka;

import io.wiretap.integrationtests.support.JsonLogCapture;
import io.wiretap.integrationtests.support.WiretapIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpToKafkaTracePropagationTest extends WiretapIntegrationTestBase {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void traceIdPropagatesFromHttpToKafkaProducer(CapturedOutput output) {
        restTemplate.postForEntity("/api/kafka/trace-demo?topic=demo.events", null, Map.class);

        Map<String, Object> httpLog = JsonLogCapture.awaitMatching(output, e -> {
            if (!"INCOMING".equals(JsonLogCapture.at(e, "http_info.direction"))) return false;
            String url = JsonLogCapture.at(e, "http_info.request_url");
            return url != null && url.contains("/api/kafka/trace-demo");
        });

        Map<String, Object> kafkaLog = JsonLogCapture.awaitMatching(output, e -> {
            if (!"OUTGOING".equals(JsonLogCapture.at(e, "kafka_info.direction"))) return false;
            return "trace-marker".equals(JsonLogCapture.at(e, "kafka_info.key"));
        });

        String httpTraceId = (String) httpLog.get("trace_id");
        String kafkaTraceId = (String) kafkaLog.get("trace_id");

        assertThat(httpTraceId).as("HTTP log must carry a trace_id").isNotBlank();
        assertThat(kafkaTraceId).as("Kafka producer log must carry a trace_id").isNotBlank();
        assertThat(kafkaTraceId).as("trace_id must be identical across HTTP→Kafka").isEqualTo(httpTraceId);
    }
}
