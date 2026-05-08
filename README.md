[RU](README.ru.md) | EN

# Wiretap

> Structured JSON logging for Spring Boot applications, with HTTP request/response
> capture across servlet, RestTemplate, RestClient, FeignClient, and WebServiceTemplate.

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**Status:** `0.1.0-SNAPSHOT` — work in progress, public API not yet stable. Do not use in production.

## What you get

Add Wiretap to a Spring Boot application and every inbound and outbound HTTP call is
captured as a structured JSON log line, with consistent fields, automatic correlation
ID propagation, and built-in masking of sensitive data.

```json
{
  "@timestamp": "2026-05-07T10:14:32.918+00:00",
  "env": "prod",
  "system": "checkout-api",
  "inst": "checkout-api-7d8c9f-xk2",
  "trace_id": "0123456789abcdef",
  "span_id": "fedcba9876543210",
  "level": "INFO",
  "message": "Captured incoming http request /api/v1/orders",
  "http_info": {
    "direction": "INCOMING",
    "http_method": "POST",
    "request_url": "/api/v1/orders",
    "return_code": 201,
    "duration": 47,
    "request_body": "{\"items\":[...]}",
    "request_body_length": 320,
    "response_body": "{\"id\":\"order_42\"}",
    "response_body_length": 19
  }
}
```

## Quick start

```gradle
dependencies {
    implementation 'io.wiretap:wiretap:0.1.0-SNAPSHOT'
}
```

That's it — no configuration is required. Wiretap auto-configures itself via Spring Boot's
auto-configuration mechanism. Logs are emitted to stdout in JSON format. Inbound HTTP
traffic is captured automatically; outbound capture happens for any `RestTemplate` /
`RestClient` / `FeignClient` / `WebServiceTemplate` constructed via Spring's
auto-configured builders.

To also write logs to a rolling file:

```yaml
wiretap:
  file-logging:
    enabled: true
    path: /var/log/myapp     # default: /var/log/wiretap
```

## Customising field names

Default field names match the Wiretap schema. Override any name in `application.yml`:

```yaml
wiretap:
  fields:
    timestamp: "@timestamp"
    trace-id: trace_id
    http-info: http
    http:
      return-code: status
      duration: elapsed_ms
      request-url: path
      request-body: req
      response-body: resp
```

The change applies to both incoming access logs and outgoing HTTP logs, so all
log records share the same shape.

| Property | Default |
|---|---|
| `wiretap.fields.timestamp` | `@timestamp` |
| `wiretap.fields.env` | `env` |
| `wiretap.fields.system` | `system` |
| `wiretap.fields.instance` | `inst` |
| `wiretap.fields.lb-trace-id` | `lb_trace_id` |
| `wiretap.fields.trace-id` | `trace_id` |
| `wiretap.fields.span-id` | `span_id` |
| `wiretap.fields.session-key` | `session_key` |
| `wiretap.fields.level` | `level` |
| `wiretap.fields.message` | `message` |
| `wiretap.fields.http-info` | `http_info` |
| `wiretap.fields.http.return-code` | `return_code` |
| `wiretap.fields.http.method` | `http_method` |
| `wiretap.fields.http.direction` | `direction` |
| `wiretap.fields.http.url` | `request_url` |
| `wiretap.fields.http.protocol` | `protocol` |
| `wiretap.fields.http.duration` | `duration` |
| `wiretap.fields.http.source-port` | `source_port` |
| `wiretap.fields.http.request-headers` | `request_headers` |
| `wiretap.fields.http.response-headers` | `response_headers` |
| `wiretap.fields.http.request-params` | `request_params` |
| `wiretap.fields.http.request-body` | `request_body` |
| `wiretap.fields.http.request-body-length` | `request_body_length` |
| `wiretap.fields.http.response-body` | `response_body` |
| `wiretap.fields.http.response-body-length` | `response_body_length` |
| `wiretap.fields.http.xml-body-type` | `xml_body_type` |

## Adding custom fields (SPI)

Out of the box Wiretap emits a fixed set of fields. To enrich every access log
entry with values from your domain (per-tenant ID, kiosk ID, business operation
type, …), implement `WiretapAccessFieldProvider` as a Spring bean — Wiretap picks
it up automatically:

```java
@Component
public class TenantIdFieldProvider implements WiretapAccessFieldProvider {
    @Override public String fieldName() { return "tenant_id"; }

    @Override public Object value(IAccessEvent event) {
        String raw = event.getRequestHeaderMap().get("X-Tenant-ID");
        return raw == null ? null : raw.toLowerCase();
    }
}
```

Returning `null` from `value(...)` skips the field for that event.
For multi-field providers or raw JSON output, override `writeTo(...)` directly.

## Header forwarding

Wiretap handles session/correlation headers in two independent places that cannot
share state:

- **`forward-to-mdc`** — copies header values into SLF4J MDC at the start of every
  request, so any `log.info(...)` call in that thread is automatically tagged with them.
- **`session-key-header`** — tells the Logback-access layer which header to read when
  writing the `session_key` field in the structured HTTP access-log entry. Logback-access
  runs in a separate context (`IAccessEvent`) with no access to the SLF4J MDC, so the
  header must be re-read directly from the request. As a fallback, the response headers
  are also checked — useful for protocols (e.g. SOAP) where a session key is established
  in the response.

Both properties default to `x-session-key`, but they are configured independently
because they feed different logging subsystems.

Override either set when your infrastructure uses different conventions:

```yaml
wiretap:
  headers:
    forward-to-mdc:
      - x-request-id
      - x-correlation-id
      - x-trace-id
    session-key-header: x-session-key
```

## What gets logged

Wiretap captures HTTP traffic from five sources, each configurable independently.
Each source has its own property prefix:

| Traffic | Prefix | Toggle |
|---|---|---|
| Inbound (servlet) | `wiretap.rest-controllers.*` | Always on |
| Outbound `RestTemplate` | `wiretap.rest-template-interceptor.*` | `.enabled=false` to disable |
| Outbound `RestClient` | `wiretap.rest-client-interceptor.*` | `.enabled=false` to disable |
| Outbound `FeignClient` | `wiretap.feign-client-interceptor.*` | `.enabled=false` to disable |
| Outbound `WebServiceTemplate` (SOAP) | `wiretap.web-service-template-interceptor.*` | `.enabled=false` to disable |

### Field visibility

Each source supports per-field visibility flags. A field can be turned off
globally, then re-enabled for specific URL patterns (or vice-versa):

```yaml
wiretap:
  rest-controllers:
    visibility-settings:
      REQUEST_BODY: false        # don't log inbound request bodies by default
    specific-http-info-settings:
      - match-url-pattern: ".*/orders/.*"
        visibility-settings:
          REQUEST_BODY: true     # but do log them on the orders endpoint
```

Available toggles: `REQUEST_URL`, `REQUEST_HEADERS`, `REQUEST_PARAMS`,
`REQUEST_BODY`, `RESPONSE_HEADERS`, `RESPONSE_BODY`.

### Body limits and masking

```yaml
wiretap:
  rest-controllers:
    http-body-settings:
      max-body-length: 10000        # truncate bodies longer than this
      max-field-length: 1000        # truncate string fields inside JSON bodies
      enable-body-truncating: true
      enable-body-masking: false    # apply MaskUtil to bodies (PAN, phone, exp date, PIN)
    enable-url-masking: true        # mask PAN/phone numbers in URLs
```

Built-in `MaskUtil` recognises:
- Card PAN (Luhn-checked or simple length-based)
- Russian-format phone numbers (`+7...`)
- Card expiry dates (in `"expiry": "2510"` style JSON values)
- PIN blocks (16-char hex)

To plug in your own masking logic, expose a bean of type
`io.wiretap.applog.message.handler.MessageMaskingHandler`. It is applied to
unstructured `log.info(...)` messages.

### Skipping URLs entirely

```yaml
wiretap:
  rest-controllers:
    exclude-request-patterns:
      - "/actuator/.*"
      - "/health"
```

## Tracing

Wiretap reads `trace_id` and `span_id` from the active Micrometer Tracing context
(any backend — Brave, OpenTelemetry, …). When Brave is on the classpath the
library configures a 64-bit single-header B3 propagator by default; disable that
behaviour with `wiretap.tracing.propagation.type.b3.enabled=false`.

The `lb_trace_id` field is sourced from the inbound `lb-trace-id` request header,
intended for load-balancer-emitted IDs.

## Pretty printing

For local development, set `wiretap.pretty-print=true` to emit multi-line JSON.
Leave it `false` (default) in production — log shippers parse single-line JSON faster.

## Compatibility

| | Version |
|---|---|
| Java | 17+ (tested on 17, 21, 25) |
| Spring Boot | 3.2.x and later |
| logback-access-spring-boot-starter | 4.1.x |

## Building from source

```bash
./gradlew build
```

Tests use JUnit 5 + WireMock + AssertJ. Mockito requires
`-Dnet.bytebuddy.experimental=true` on Java 25, which is configured in
`build.gradle`.

## License

Apache License 2.0 — see [LICENSE](./LICENSE).
