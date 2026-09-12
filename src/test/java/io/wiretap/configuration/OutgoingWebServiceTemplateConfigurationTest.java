package io.wiretap.configuration;

import io.micrometer.tracing.Tracer;
import io.wiretap.http.outgoing.interceptor.webservicetemplate.WebServiceTemplateLoggingInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webservices.client.WebServiceTemplateBuilder;
import org.springframework.boot.webservices.client.WebServiceTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ws.client.core.WebServiceTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the SOAP wiring to what the README promises: a {@code WebServiceTemplate}
 * built through Spring's auto-configured {@code WebServiceTemplateBuilder} logs
 * without the application touching its interceptor chain, and the
 * {@code wiretap.web-service-template-interceptor.enabled} toggle switches that off.
 * The builder only consults {@code WebServiceTemplateCustomizer} beans, so a bare
 * {@code ClientInterceptor} bean alone was never picked up.
 */
final class OutgoingWebServiceTemplateConfigurationTest {

    @Test
    void builderAttachesTheLoggingInterceptorOnItsOwn() {
        runner().run(ctx -> assertThat(built(ctx).getInterceptors())
                .as("a template from the auto-configured builder must carry the logging interceptor")
                .contains(ctx.getBean(WebServiceTemplateLoggingInterceptor.class)));
    }

    @Test
    void builderDoesNotDuplicateAnInterceptorWiredByHand() {
        runner().run(ctx -> assertThat(builder(ctx)
                .interceptors(ctx.getBean(WebServiceTemplateLoggingInterceptor.class))
                .build()
                .getInterceptors())
                .as("an application that attached the interceptor itself must not log every call twice")
                .containsOnlyOnce(ctx.getBean(WebServiceTemplateLoggingInterceptor.class)));
    }

    @Test
    void toggleRemovesTheCustomizer() {
        runner()
                .withPropertyValues("wiretap.web-service-template-interceptor.enabled=false")
                .run(ctx -> assertThat(ctx)
                        .as("disabling the SOAP interceptor must leave the builder without a wiretap customizer")
                        .doesNotHaveBean(WebServiceTemplateCustomizer.class));
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, WiretapAutoConfiguration.class))
                .withUserConfiguration(StubTracerConfig.class);
    }

    private WebServiceTemplate built(AssertableApplicationContext ctx) {
        return builder(ctx).build();
    }

    private WebServiceTemplateBuilder builder(AssertableApplicationContext ctx) {
        return new WebServiceTemplateBuilder(ctx.getBeanProvider(WebServiceTemplateCustomizer.class)
                .orderedStream()
                .toArray(WebServiceTemplateCustomizer[]::new));
    }

    @Configuration
    static class StubTracerConfig {
        @Bean
        Tracer tracer() {
            return Tracer.NOOP;
        }
    }
}
