package io.wiretap.integrationtests.http;

import io.wiretap.integrationtests.support.WiretapIntegrationTestBase;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof on a real servlet container that {@code x-request-id} is
 * forwarded into MDC <em>before</em> the servlet filter chain runs, and that MDC
 * is cleared between requests so values do not leak across pooled threads.
 *
 * <p>The MDC is observed through a probe filter (order {@code 0}) that runs inside
 * wiretap's {@code HIGHEST_PRECEDENCE} filter but still in the filter phase (before
 * the {@code DispatcherServlet}); it copies {@code MDC.get("x-request-id")} into the
 * {@code X-Mdc-Seen} response header. Under the previous {@code HandlerInterceptor}
 * approach MDC would not yet be populated at this point, so the header would be absent.
 *
 * <p>Tomcat is pinned to a single worker thread so the second request deterministically
 * reuses the thread that served the first — exposing any leftover MDC.
 */
@TestPropertySource(properties = {
        "server.tomcat.threads.max=1",
        "server.tomcat.threads.min-spare=1"
})
class CorrelationHeadersMdcForwardingTest extends WiretapIntegrationTestBase {

    private static final String MDC_PROBE_HEADER = "X-Mdc-Seen";

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void forwardsCorrelationHeaderToServletFilterPhase() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-request-id", "req-42");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/echo?marker=mdc", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst(MDC_PROBE_HEADER))
                .as("x-request-id must be in MDC while the servlet filter chain runs")
                .isEqualTo("req-42");
    }

    @Test
    void clearsMdcBetweenRequestsOnReusedThread() {
        HttpHeaders withId = new HttpHeaders();
        withId.set("x-request-id", "leak-1");

        ResponseEntity<String> first = restTemplate.exchange(
                "/api/echo", HttpMethod.GET, new HttpEntity<>(withId), String.class);
        assertThat(first.getHeaders().getFirst(MDC_PROBE_HEADER)).isEqualTo("leak-1");

        // Same single Tomcat worker, but this request carries no x-request-id.
        ResponseEntity<String> second = restTemplate.exchange(
                "/api/echo", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);

        assertThat(second.getHeaders().getFirst(MDC_PROBE_HEADER))
                .as("MDC from the previous request must not leak onto the reused worker thread")
                .isNull();
    }

    @TestConfiguration
    static class MdcProbeConfig {

        @Bean
        FilterRegistrationBean<Filter> mdcProbeFilter() {
            Filter probe = (request, response, chain) -> {
                String seen = MDC.get("x-request-id");
                if (seen != null) {
                    ((HttpServletResponse) response).setHeader(MDC_PROBE_HEADER, seen);
                }
                chain.doFilter(request, response);
            };
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(probe);
            // Runs after wiretap's HIGHEST_PRECEDENCE filter, still before the DispatcherServlet.
            registration.setOrder(0);
            return registration;
        }
    }
}
