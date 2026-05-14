package io.wiretap.configuration;

import feign.Client;
import io.wiretap.http.message.HttpRequestParamsMaskingHandler;
import io.wiretap.http.message.HttpUrlMaskingHandler;
import io.wiretap.http.message.settings.FeignClientMessageSettings;
import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.body.BodyParser;
import io.wiretap.http.outgoing.interceptor.feignclient.FeignClientWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FeignClientMessageSettings.class)
public class OutgoingFeignClientConfiguration {

    @ConditionalOnProperty(name = "wiretap.feign-client-interceptor.enabled", havingValue = "true", matchIfMissing = true)
    @Bean
    public Client loggingFeignClient(
            BodyParser bodyParser, FeignClientMessageSettings settings, HttpAccessFieldNames httpFieldNames,
            @Autowired(required = false) HttpUrlMaskingHandler urlMaskingHandler,
            @Autowired(required = false) HttpRequestParamsMaskingHandler paramsMaskingHandler) {
        return new FeignClientWrapper(new Client.Default(null, null), bodyParser, settings, httpFieldNames, urlMaskingHandler, paramsMaskingHandler);
    }
}
