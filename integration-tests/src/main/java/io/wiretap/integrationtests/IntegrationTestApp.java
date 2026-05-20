package io.wiretap.integrationtests;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class IntegrationTestApp {

    public static void main(String[] args) {
        SpringApplication.run(IntegrationTestApp.class, args);
    }
}
