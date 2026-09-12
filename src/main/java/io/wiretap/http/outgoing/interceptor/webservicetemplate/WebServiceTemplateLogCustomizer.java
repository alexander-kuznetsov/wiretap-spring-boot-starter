package io.wiretap.http.outgoing.interceptor.webservicetemplate;

import org.springframework.boot.webservices.client.WebServiceTemplateCustomizer;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Attaches the wiretap SOAP interceptor to every {@code WebServiceTemplate} that
 * Spring's auto-configured {@code WebServiceTemplateBuilder} produces. The builder
 * runs customizers last, so interceptors the application configured stay ahead of
 * the logging one, and a template that already carries it is left as is so that a
 * chain wired by hand does not log every call twice.
 */
public final class WebServiceTemplateLogCustomizer implements WebServiceTemplateCustomizer {

    private final ClientInterceptor interceptor;

    public WebServiceTemplateLogCustomizer(ClientInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void customize(WebServiceTemplate template) {
        template.setInterceptors(
                Stream.concat(Arrays.stream(chain(template)), Stream.of(interceptor))
                        .distinct()
                        .toArray(ClientInterceptor[]::new));
    }

    private ClientInterceptor[] chain(WebServiceTemplate template) {
        return Objects.requireNonNullElse(template.getInterceptors(), new ClientInterceptor[0]);
    }
}
