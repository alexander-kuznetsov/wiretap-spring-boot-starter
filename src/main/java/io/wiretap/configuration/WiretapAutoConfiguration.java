package io.wiretap.configuration;

import io.wiretap.http.message.settings.HttpAccessFieldNames;
import io.wiretap.http.message.settings.body.BodyParserConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

@Configuration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@EnableConfigurationProperties(WiretapAccessLogFieldsProperties.class)
@Import({
        BodyParserConfiguration.class,
        MessageMaskingConfiguration.class,
        IncomingHttpConfiguration.class,
        OutgoingRestTemplateConfiguration.class,
        OutgoingRestClientConfiguration.class,
        OutgoingFeignClientConfiguration.class,
        OutgoingWebServiceTemplateConfiguration.class,
        WiretapAccessLogConfiguration.class,
        WiretapAppLogConfiguration.class,
        WiretapTracingConfiguration.class
})
public class WiretapAutoConfiguration {

    @Bean
    public HttpAccessFieldNames httpAccessFieldNames(WiretapAccessLogFieldsProperties fieldNames) {
        return fieldNames.getHttp();
    }
}
