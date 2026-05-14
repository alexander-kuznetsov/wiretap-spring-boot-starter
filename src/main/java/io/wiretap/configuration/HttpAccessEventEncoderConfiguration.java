package io.wiretap.configuration;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import io.wiretap.http.incoming.encoder.LazyJsonAccessEncoder;
import io.wiretap.http.incoming.encoder.postprocessor.HttpAccessEventPostProcessHandler;

@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(HttpAccessEventPostProcessHandler.class)
public class HttpAccessEventEncoderConfiguration {

    private final HttpAccessEventPostProcessHandler httpAccessEventPostProcessHandler;

    @PostConstruct
    public void initHttpAccessEventPostProcessor() {
        LazyJsonAccessEncoder.httpAccessEventPostProcessHandler = httpAccessEventPostProcessHandler;
    }
}
