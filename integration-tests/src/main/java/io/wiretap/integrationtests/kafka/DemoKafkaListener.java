package io.wiretap.integrationtests.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DemoKafkaListener {

    @KafkaListener(topics = {"demo.events", "secrets.test", "secrets.events"}, groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(ConsumerRecord<String, String> record) {
        if ("fail-me".equals(record.key())) {
            throw new RuntimeException("demo failure triggered by key=" + record.key());
        }
    }
}
