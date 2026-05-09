package io.wiretap.configuration;

import io.wiretap.http.message.settings.HttpAccessFieldNames;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class WiretapAccessLogFieldsPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void defaults_matchOriginalSchema() {
        runner.run(ctx -> {
            WiretapAccessLogFieldsProperties props = ctx.getBean(WiretapAccessLogFieldsProperties.class);

            assertThat(props.getTimestamp()).isEqualTo("@timestamp");
            assertThat(props.getEnv()).isEqualTo("env");
            assertThat(props.getSystem()).isEqualTo("system");
            assertThat(props.getInstance()).isEqualTo("inst");
            assertThat(props.getLbTraceId()).isEqualTo("lb_trace_id");
            assertThat(props.getTraceId()).isEqualTo("trace_id");
            assertThat(props.getSpanId()).isEqualTo("span_id");
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
                        "wiretap.access-log.fields.timestamp=ts",
                        "wiretap.access-log.fields.http-info=http"
                )
                .run(ctx -> {
                    WiretapAccessLogFieldsProperties props = ctx.getBean(WiretapAccessLogFieldsProperties.class);
                    assertThat(props.getTimestamp()).isEqualTo("ts");
                    assertThat(props.getHttpInfo()).isEqualTo("http");
                    assertThat(props.getMessage()).isEqualTo("message");
                });
    }

    @Test
    void nestedHttpOverrides_areBound() {
        runner
                .withPropertyValues(
                        "wiretap.access-log.fields.http.return-code=status",
                        "wiretap.access-log.fields.http.duration=elapsed_ms",
                        "wiretap.access-log.fields.http.url=path"
                )
                .run(ctx -> {
                    HttpAccessFieldNames http = ctx.getBean(WiretapAccessLogFieldsProperties.class).getHttp();
                    assertThat(http.getReturnCode()).isEqualTo("status");
                    assertThat(http.getDuration()).isEqualTo("elapsed_ms");
                    assertThat(http.getUrl()).isEqualTo("path");
                    assertThat(http.getMethod()).isEqualTo("http_method");
                });
    }

    @EnableConfigurationProperties(WiretapAccessLogFieldsProperties.class)
    static class TestConfig {
    }
}
