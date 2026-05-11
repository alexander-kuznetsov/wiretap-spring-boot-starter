package io.wiretap.http.incoming.provider.message;

import ch.qos.logback.access.spi.IAccessEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import jakarta.annotation.PostConstruct;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import io.wiretap.configuration.WiretapAccessLogFieldsProperties;
import io.wiretap.http.message.settings.RestControllerLogMessageSettings;
import io.wiretap.http.message.HttpUrlMaskingHandler;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class MessageProvider extends AbstractFieldJsonProvider<IAccessEvent> {

    private static final String MESSAGE_PATTERN = "Captured incoming http request %s";

    private final boolean isUrlMaskingEnabled;
    @Nullable
    private final HttpUrlMaskingHandler urlMaskingHandler;

    public MessageProvider(
            RestControllerLogMessageSettings settings,
            WiretapAccessLogFieldsProperties fieldNames,
            @Nullable HttpUrlMaskingHandler urlMaskingHandler
    ) {
        super();
        setFieldName(fieldNames.getMessage());
        this.isUrlMaskingEnabled = settings.isEnableUrlMasking();
        this.urlMaskingHandler = urlMaskingHandler;
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
        return urlMaskingHandler != null ? urlMaskingHandler.maskUrl(url) : url;
    }
}
