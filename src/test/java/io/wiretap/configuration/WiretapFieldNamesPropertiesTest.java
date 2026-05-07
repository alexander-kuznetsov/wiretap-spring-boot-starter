package io.wiretap.configuration;

import io.wiretap.http.message.settings.HttpAccessFieldNames;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class WiretapFieldNamesPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void defaults_matchOriginalSchema() {
        runner.run(ctx -> {
            WiretapFieldNamesProperties props = ctx.getBean(WiretapFieldNamesProperties.class);

            assertThat(props.getTimestamp()).isEqualTo("@timestamp");
            assertThat(props.getEnv()).isEqualTo("env");
            assertThat(props.getSystem()).isEqualTo("system");
            assertThat(props.getInstance()).isEqualTo("inst");
            assertThat(props.getLbTraceId()).isEqualTo("lb_trace_id");
            assertThat(props.getTraceId()).isEqualTo("trace_id");
            assertThat(props.getSpanId()).isEqualTo("span_id");
            assertThat(props.getSessionKey()).isEqualTo("session_key");
            assertThat(props.getLevel()).isEqualTo("level");
            assertThat(props.getMessage()).isEqualTo("message");
            assertThat(props.getHttpInfo()).isEqualTo("http_info");

            HttpAccessFieldNames http = props.getHttp();
            assertThat(http.getReturnCode()).isEqualTo("return_code");
            assertThat(http.getMethod()).isEqualTo("http_method");
            assertThat(http.getDirection()).isEqualTo("direction");
            assertThat(http.getUrl()).isEqualTo("request_url");
            assertThat(http.getDuration()).isEqualTo("duration");
            assertThat(http.getRequestBody()).isEqualTo("request_body");
            assertThat(http.getResponseBody()).isEqualTo("response_body");
        });
    }

    @Test
    void topLevelOverrides_areBound() {
        runner
                .withPropertyValues(
                        "wiretap.fields.timestamp=ts",
                        "wiretap.fields.http-info=http"
                )
                .run(ctx -> {
                    WiretapFieldNamesProperties props = ctx.getBean(WiretapFieldNamesProperties.class);
                    assertThat(props.getTimestamp()).isEqualTo("ts");
                    assertThat(props.getHttpInfo()).isEqualTo("http");
                    // Untouched fields keep their default
                    assertThat(props.getMessage()).isEqualTo("message");
                });
    }

    @Test
    void nestedHttpOverrides_areBound() {
        runner
                .withPropertyValues(
                        "wiretap.fields.http.return-code=status",
                        "wiretap.fields.http.duration=elapsed_ms",
                        "wiretap.fields.http.url=path"
                )
                .run(ctx -> {
                    HttpAccessFieldNames http = ctx.getBean(WiretapFieldNamesProperties.class).getHttp();
                    assertThat(http.getReturnCode()).isEqualTo("status");
                    assertThat(http.getDuration()).isEqualTo("elapsed_ms");
                    assertThat(http.getUrl()).isEqualTo("path");
                    // Untouched nested fields keep their default
                    assertThat(http.getMethod()).isEqualTo("http_method");
                });
    }

    @EnableConfigurationProperties(WiretapFieldNamesProperties.class)
    static class TestConfig {
    }
}
