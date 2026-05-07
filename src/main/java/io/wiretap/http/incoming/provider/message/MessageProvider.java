package io.wiretap.http.incoming.provider.message;

import ch.qos.logback.access.spi.IAccessEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import jakarta.annotation.PostConstruct;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import org.springframework.stereotype.Component;
import io.wiretap.configuration.WiretapFieldNamesProperties;
import io.wiretap.http.message.settings.RestControllerLogMessageSettings;
import io.wiretap.util.MaskUtil;

import java.io.IOException;

@Component
public class MessageProvider extends AbstractFieldJsonProvider<IAccessEvent> {

    private static final String MESSAGE_PATTERN = "Captured incoming http request %s";

    private final boolean isUrlMaskingEnabled;

    public MessageProvider(RestControllerLogMessageSettings settings, WiretapFieldNamesProperties fieldNames) {
        super();
        setFieldName(fieldNames.getMessage());
        this.isUrlMaskingEnabled = settings.isEnableUrlMasking();
    }

    @PostConstruct
    public synchronized void init() {
        LazyMessageProvider.setProvider(this);
    }

    @Override
    public void writeTo(JsonGenerator generator, IAccessEvent iAccessEvent) throws IOException {
        final String message = String.format(
                MESSAGE_PATTERN,
                isUrlMaskingEnabled ? getMasked(iAccessEvent.getRequestURI()) : iAccessEvent.getRequestURI()
        );
        generator.writeFieldName(getFieldName());
        generator.writeString(message);
    }

    private String getMasked(String url) {
        return MaskUtil.maskPhoneNumber(MaskUtil.maskAllPans(url, true));
    }
}
