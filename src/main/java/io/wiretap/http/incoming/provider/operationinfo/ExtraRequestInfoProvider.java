package io.wiretap.http.incoming.provider.operationinfo;

import ch.qos.logback.access.spi.IAccessEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * Logback-access provider that injects user-supplied extra information
 * (business context, operation metadata, etc.) into HTTP access logs.
 */
@Slf4j
@Component
public class ExtraRequestInfoProvider extends AbstractFieldJsonProvider<IAccessEvent> {
    private final ObjectMapper mapper;
    private final boolean isPrettyLog;
    public ExtraRequestInfoProvider(
            ObjectMapper mapper,
            @Value("${wiretap.pretty-print:false}") boolean isPrettyLog,
            @Value("${wiretap.rest-controllers.extra-info-field-name:operation_info}") String operationInfoFieldName
    ) {
        super();
        this.mapper = mapper;
        this.isPrettyLog = isPrettyLog;
        setFieldName(operationInfoFieldName);
    }

    @PostConstruct
    public synchronized void init() {
        LazyExtraRequestInfoProvider.setProvider(this);
    }
    @Override
    public void writeTo(JsonGenerator generator, IAccessEvent iAccessEvent) {
        try {
            final String additionalRequestInfo = ExtraRequestInfoContextKeeper.getAndRemoveAdditionalInfo();
            if (additionalRequestInfo != null && !additionalRequestInfo.isBlank()) {
                generator.writeFieldName(this.getFieldName());
                Optional<JsonNode> operationContextJsonNodeOptional = getExtraParamsJsonNode(additionalRequestInfo);

                if (operationContextJsonNodeOptional.isEmpty()) { // plain string was passed instead of structured JSON
                    generator.writeRawValue(mapper.writer().writeValueAsString(additionalRequestInfo));
                    return;
                }
                generator.writeRawValue(getExtraInfoLogValueString(isPrettyLog, operationContextJsonNodeOptional.get()));
            }
        } catch (IOException e) {
            log.error("Error while providing to log additional info", e);
        }
    }

    private String getExtraInfoLogValueString(boolean isPrettyLog, JsonNode operationContextJsonNode) throws JsonProcessingException {
        try {
            return isPrettyLog ?
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(operationContextJsonNode) :
                    mapper.writer().writeValueAsString(operationContextJsonNode);
        } catch (JsonProcessingException e) {
            return "Error during parsing extra request info";
        }
    }

    private Optional<JsonNode> getExtraParamsJsonNode(String additionalRequestInfo) throws JsonProcessingException {
        try {
            return Optional.of(mapper.readTree(additionalRequestInfo));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }
}