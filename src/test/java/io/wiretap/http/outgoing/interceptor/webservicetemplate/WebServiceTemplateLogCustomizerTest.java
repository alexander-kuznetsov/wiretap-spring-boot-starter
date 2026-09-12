package io.wiretap.http.outgoing.interceptor.webservicetemplate;

import org.junit.jupiter.api.Test;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.client.support.interceptor.ClientInterceptorAdapter;

import static org.assertj.core.api.Assertions.assertThat;

final class WebServiceTemplateLogCustomizerTest {

    @Test
    void attachesTheInterceptorToATemplateWithoutAny() {
        WebServiceTemplate template = new WebServiceTemplate();
        ClientInterceptor interceptor = new ClientInterceptorAdapter() {
        };
        new WebServiceTemplateLogCustomizer(interceptor).customize(template);
        assertThat(template.getInterceptors())
                .as("a template with no interceptors must end up with the logging one")
                .containsExactly(interceptor);
    }

    @Test
    void keepsInterceptorsConfiguredAheadOfItInPlace() {
        WebServiceTemplate template = new WebServiceTemplate();
        ClientInterceptor security = new ClientInterceptorAdapter() {
        };
        ClientInterceptor interceptor = new ClientInterceptorAdapter() {
        };
        template.setInterceptors(new ClientInterceptor[]{security});
        new WebServiceTemplateLogCustomizer(interceptor).customize(template);
        assertThat(template.getInterceptors())
                .as("interceptors the application configured must stay ahead of the logging one")
                .containsExactly(security, interceptor);
    }

    @Test
    void doesNotAttachTheSameInterceptorTwice() {
        WebServiceTemplate template = new WebServiceTemplate();
        ClientInterceptor interceptor = new ClientInterceptorAdapter() {
        };
        template.setInterceptors(new ClientInterceptor[]{interceptor});
        new WebServiceTemplateLogCustomizer(interceptor).customize(template);
        assertThat(template.getInterceptors())
                .as("a template that already carries the logging interceptor must not log twice")
                .containsExactly(interceptor);
    }
}
