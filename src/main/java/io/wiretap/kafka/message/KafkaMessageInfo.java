package io.wiretap.kafka.message;

import io.wiretap.kafka.message.settings.KafkaAccessFieldNames;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class KafkaMessageInfo {

    public enum Direction {INCOMING, OUTGOING}

    public enum Status {SUCCESS, ERROR}

    private Direction direction;
    private String topic;
    private Integer partition;
    private Long offset;
    private String clientId;
    private String groupId;
    private String key;
    private Long keyLength;
    private String value;
    private Long valueLength;
    private Map<String, String> headers;
    private String timestamp;
    private String timestampType;
    private Long duration;
    private Status status;
    private String errorClass;
    private String errorMessage;

    public Map<String, Object> toMap(KafkaAccessFieldNames f) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (direction != null) map.put(f.getDirection(), direction);
        if (topic != null && !topic.isEmpty()) map.put(f.getTopic(), topic);
        if (partition != null) map.put(f.getPartition(), partition);
        if (offset != null) map.put(f.getOffset(), offset);
        if (clientId != null && !clientId.isEmpty()) map.put(f.getClientId(), clientId);
        if (groupId != null && !groupId.isEmpty()) map.put(f.getGroupId(), groupId);
        if (key != null) map.put(f.getKey(), key);
        if (keyLength != null) map.put(f.getKeyLength(), keyLength);
        if (value != null) map.put(f.getValue(), value);
        if (valueLength != null) map.put(f.getValueLength(), valueLength);
        if (headers != null && !headers.isEmpty()) map.put(f.getHeaders(), headers);
        if (timestamp != null && !timestamp.isEmpty()) map.put(f.getTimestamp(), timestamp);
        if (timestampType != null && !timestampType.isEmpty()) map.put(f.getTimestampType(), timestampType);
        if (duration != null) map.put(f.getDuration(), duration);
        if (status != null) map.put(f.getStatus(), status);
        if (errorClass != null && !errorClass.isEmpty()) map.put(f.getErrorClass(), errorClass);
        if (errorMessage != null && !errorMessage.isEmpty()) map.put(f.getErrorMessage(), errorMessage);
        return map;
    }
}
