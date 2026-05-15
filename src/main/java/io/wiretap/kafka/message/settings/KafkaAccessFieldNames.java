package io.wiretap.kafka.message.settings;

import lombok.Data;

/**
 * JSON field names emitted inside the {@code kafka_info} object.
 * Defaults match the Wiretap schema; override any name via
 * {@code wiretap.access-log.fields.kafka.*} in {@code application.yml}.
 */
@Data
public class KafkaAccessFieldNames {
    private String direction = "direction";
    private String topic = "topic";
    private String partition = "partition";
    private String offset = "offset";
    private String clientId = "client_id";
    private String groupId = "group_id";
    private String key = "key";
    private String keyLength = "key_length";
    private String value = "value";
    private String valueLength = "value_length";
    private String headers = "headers";
    private String timestamp = "timestamp";
    private String timestampType = "timestamp_type";
    private String duration = "duration";
    private String status = "status";
    private String errorClass = "error_class";
    private String errorMessage = "error_message";
}
