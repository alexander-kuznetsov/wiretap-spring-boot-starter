package io.wiretap.integrationtests.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/kafka")
public class KafkaProducerController {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaProducerController(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping("/send")
    public Map<String, Object> send(@RequestParam String topic,
                                    @RequestParam(required = false) String key,
                                    @RequestParam String value) {
        kafkaTemplate.send(topic, key, value);
        return Map.of("topic", topic, "key", key == null ? "" : key, "value", value);
    }

    @PostMapping("/trace-demo")
    public Map<String, Object> traceDemo(@RequestParam(defaultValue = "demo.events") String topic) {
        kafkaTemplate.send(topic, "trace-marker", "trace-demo-payload");
        return Map.of("topic", topic);
    }
}
