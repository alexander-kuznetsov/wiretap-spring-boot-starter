[RU](README.ru.md) | EN

# Wiretap

> Structured JSON logging for Spring Boot applications, with HTTP request/response
> capture across servlet, RestTemplate, RestClient, FeignClient, WebClient, and WebServiceTemplate.

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![compatibility](https://github.com/alexander-kuznetsov/wiretap-spring-boot-starter/actions/workflows/compatibility.yml/badge.svg)](https://github.com/alexander-kuznetsov/wiretap-spring-boot-starter/actions/workflows/compatibility.yml)
[![release](https://github.com/alexander-kuznetsov/wiretap-spring-boot-starter/actions/workflows/release.yml/badge.svg)](https://github.com/alexander-kuznetsov/wiretap-spring-boot-starter/actions/workflows/release.yml)

**Status:** Pre-release — only `1.0.0-SNAPSHOT` is published right now (public Central Portal snapshots) and is being tested on live projects ahead of the stable `1.0.0` release. The public API (configuration properties, SPI interfaces, artifact coordinates) is essentially final and follows [semantic versioning](https://semver.org/spec/v2.0.0.html) from 1.0.0 on.

> **Try it live.** [`logger-demo`](https://github.com/alexander-kuznetsov/logger-demo) is a
> runnable Spring Boot sandbox that wires Wiretap into a real app — inbound/outbound HTTP,
> Kafka, masking, metrics, pretty-print — so you can see the JSON output before adding it to
> your own service.

## What you get

Add Wiretap to a Spring Boot application and every inbound and outbound HTTP call is
captured as a structured JSON log line, with consistent fields, automatic correlation
ID propagation, and built-in masking of sensitive data.

**Access log** (from `log.info(...)` or any SLF4J call):

```json
{
  "@timestamp": "2026-05-07T10:14:32.918+00:00",
  "env": "prod",
  "system": "checkout-api",
  "inst": "checkout-api-7d8c9f-xk2",
  "trace_id": "0123456789abcdef",
  "span_id": "fedcba9876543210",
  "level": "INFO",
  "thread_name": "http-nio-8080-exec-1",
  "logger": "com.example.OrderService",
  "message": "Order created"
}
```

**HTTP access log** (every inbound or outbound HTTP call):

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

> **Pre-release.** Only `1.0.0-SNAPSHOT` is published right now — to the public
> Central Portal snapshots repo — and it is being battle-tested on live projects.
> The stable `1.0.0` release will follow.

```gradle
repositories {
    mavenCentral()
    maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
}

dependencies {
    // Pick the coordinate matching your Spring Boot version
    // (see the "Versioning and compatibility" section below).
    implementation 'io.github.alexander-kuznetsov:wiretap-spring-boot-3.5.14-starter:1.0.0-SNAPSHOT'
}
```

Once `1.0.0` is released you can drop the snapshots repository and depend on
`:1.0.0` from `mavenCentral()` — that's the only change.

That's it — no other configuration is required. Wiretap auto-configures itself via Spring Boot's
auto-configuration mechanism. Logs are emitted to stdout in JSON format. Inbound HTTP
traffic is captured automatically; outbound capture happens for any `RestTemplate` / `RestClient` / `FeignClient` /
`WebClient` / `WebServiceTemplate` constructed via Spring's auto-configured builders.
`WebClient`-based clients such as `graphql.kickstart.spring.webclient.boot.GraphQLWebClient`
are covered automatically through the same `WebClient.Builder` customizer.

To also write logs to a rolling file:

```yaml
wiretap:
  file-logging:
    enabled: true
    path: /var/log/myapp     # default: /var/log/wiretap
```

### Custom logback config

Wiretap ships a default `logback-spring.xml` and `logback-access.xml`
inside the jar — that is what makes the JSON output appear out of the box.
If your application has its own `src/main/resources/logback-spring.xml`,
it overrides the bundled one (classpath order). To keep Wiretap's
encoders, `<include>` the fragments:

```xml
<!-- src/main/resources/logback-spring.xml -->
<configuration>
    <include resource="logback-console-appender.xml"/>
    <!-- your custom appenders here -->
</configuration>
```

The same applies to `logback-access.xml` and
`logback-access-console-appender.xml`.

## Application log fields

Standard fields emitted for every `log.info(...)` / `log.error(...)` call:

| Field | Default name | Source |
|---|---|---|
| `wiretap.app-log.fields.timestamp` | `@timestamp` | Event timestamp |
| `wiretap.app-log.fields.env` | `env` | `spring.profiles.active` |
| `wiretap.app-log.fields.system` | `system` | `spring.application.name` |
| `wiretap.app-log.fields.instance` | `inst` | `HOSTNAME` env var |
| `wiretap.app-log.fields.trace-id` | `trace_id` | MDC `traceId` |
| `wiretap.app-log.fields.span-id` | `span_id` | MDC `spanId` |
| `wiretap.app-log.fields.level` | `level` | Log level |
| `wiretap.app-log.fields.thread-name` | `thread_name` | Thread name |
| `wiretap.app-log.fields.logger-name` | `logger` | Logger name (no stack trace) |
| `wiretap.app-log.fields.message` | `message` | Formatted message (masked) |
| `wiretap.app-log.fields.http-info` | `http_info` | MDC `HTTP-REQUEST-LOG` as JSON |
| `wiretap.app-log.fields.extra` | `extra` | MDC `LOG_EXTRA` as JSON |
| `wiretap.app-log.fields.caller-class` | `caller_class` | Caller class (off by default) |
| `wiretap.app-log.fields.caller-method` | `caller_method` | Caller method (off by default) |
| `wiretap.app-log.fields.caller-line` | `caller_line` | Caller line (off by default) |
| `wiretap.app-log.fields.caller-file` | `caller_file` | Caller file (off by default) |

Caller-data fields require a stack-trace capture and are disabled by default.
Enable them when you need exact source location at the cost of extra CPU:

```yaml
wiretap:
  app-log:
    visibility-settings:
      CALLER_CLASS: true
      CALLER_METHOD: true
      CALLER_LINE: true
```

Rename any field the same way:

```yaml
wiretap:
  app-log:
    fields:
      logger-name: class   # revert to the old key name
      thread-name: thread
```

### Silencing a noisy logger

Wiretap rides on Spring Boot + Logback, so quiet (or mute) any logger with the
standard `logging.level.*` — no wiretap-specific config. For example, to silence
the chatty `brave.Tracer` span dumps:

```yaml
logging:
  level:
    brave.Tracer: OFF          # mute entirely; or WARN / ERROR to just raise the threshold
```

## Extra structured fields

The `extra` field in every app-log entry is populated from the MDC key `LOG_EXTRA`.
`ExtraAppLogContextKeeper` is a thread-bound utility that manages that key: it keeps
all extra fields in one JSON object so the root log structure stays clean.

```java
ExtraAppLogContextKeeper.putExtraField("order_id", "ord-42");
ExtraAppLogContextKeeper.putExtraField("step", "validation");
log.info("Payment step completed");
ExtraAppLogContextKeeper.clearExtraContext();
```

`extra` in the resulting log entry:

```json
{ "order_id": "ord-42", "step": "validation" }
```

| Method | Description |
|---|---|
| `putExtraField(key, value)` | Add or update a field in the current thread's extra context |
| `removeExtraField(key)` | Remove a single field; removes the MDC key when the last field is gone |
| `clearExtraContext()` | Remove all extra fields for the current thread |

MDC is not propagated automatically across threads. When you spawn async work
(`@Async`, `CompletableFuture`, parallel streams), copy and restore MDC manually
or use a `TaskDecorator`. Extra fields appear **only in app logs** — not in HTTP
access logs.

## Customising access-log field names

Default field names match the Wiretap schema. Override any name in `application.yml`:

```yaml
wiretap:
  access-log:
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
| `wiretap.access-log.fields.timestamp` | `@timestamp` |
| `wiretap.access-log.fields.env` | `env` |
| `wiretap.access-log.fields.system` | `system` |
| `wiretap.access-log.fields.instance` | `inst` |
| `wiretap.access-log.fields.lb-trace-id` | `lb_trace_id` |
| `wiretap.access-log.fields.trace-id` | `trace_id` |
| `wiretap.access-log.fields.span-id` | `span_id` |
| `wiretap.access-log.fields.level` | `level` |
| `wiretap.access-log.fields.message` | `message` |
| `wiretap.access-log.fields.http-info` | `http_info` |
| `wiretap.access-log.fields.http.return-code` | `return_code` |
| `wiretap.access-log.fields.http.method` | `http_method` |
| `wiretap.access-log.fields.http.direction` | `direction` |
| `wiretap.access-log.fields.http.url` | `request_url` |
| `wiretap.access-log.fields.http.protocol` | `protocol` |
| `wiretap.access-log.fields.http.duration` | `duration` |
| `wiretap.access-log.fields.http.source-port` | `source_port` |
| `wiretap.access-log.fields.http.request-headers` | `request_headers` |
| `wiretap.access-log.fields.http.response-headers` | `response_headers` |
| `wiretap.access-log.fields.http.request-params` | `request_params` |
| `wiretap.access-log.fields.http.request-body` | `request_body` |
| `wiretap.access-log.fields.http.request-body-length` | `request_body_length` |
| `wiretap.access-log.fields.http.response-body` | `response_body` |
| `wiretap.access-log.fields.http.response-body-length` | `response_body_length` |
| `wiretap.access-log.fields.http.xml-body-type` | `xml_body_type` |

## Adding custom fields (SPI)

Wiretap provides three SPI interfaces for extending what gets logged:

- **`WiretapAccessFieldProvider`** — adds fields to HTTP access logs (inbound and outbound HTTP calls).
- **`WiretapLogFieldProvider`** — adds fields to application logs (`log.info(...)`, `log.error(...)`, etc.).
- **`HttpAccessEventPostProcessHandler`** — reacts to the fully-encoded access-log JSON (e.g. publishes business metrics derived from response body) — see [Post-processing access-log events](#post-processing-access-log-events).

### HTTP access log fields

Implement `WiretapAccessFieldProvider` as a Spring bean — Wiretap picks it up automatically:

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

### Application log fields

Implement `WiretapLogFieldProvider` as a Spring bean to add fields to every `log.info(...)` line:

```java
@Component
public class TenantIdLogFieldProvider implements WiretapLogFieldProvider {
    @Override public String fieldName() { return "tenant_id"; }

    @Override public Object value(ILoggingEvent event) {
        return event.getMDCPropertyMap().get("tenant-id");
    }
}
```

Note that Logback-access and SLF4J MDC run in separate contexts. Fields that are
available via `IAccessEvent` (e.g. request headers) are not accessible inside
`WiretapLogFieldProvider` — read them from MDC keys set upstream via
`WiretapHeadersProperties` or a servlet filter.

### Post-processing access-log events

For workloads that need to react to the *fully-encoded* access-log JSON — for
example to publish a Micrometer counter tagged by a field extracted from the
response body — implement `HttpAccessEventPostProcessHandler`:

```java
public interface HttpAccessEventPostProcessHandler {
    void performPostProcess(byte[] encodedBytes);
}
```

Wiretap calls `performPostProcess(...)` from `LazyJsonAccessEncoder.encode()`
*after* the access-log line has been serialised to JSON and *before* it
reaches the appender. Register a Spring bean — Wiretap picks it up
automatically via `@ConditionalOnBean`:

```java
@Component
public class PaymentDecisionMetricsHandler implements HttpAccessEventPostProcessHandler {

    private final MeterRegistry registry;
    private final ObjectMapper mapper;

    public PaymentDecisionMetricsHandler(MeterRegistry registry, ObjectMapper mapper) {
        this.registry = registry;
        this.mapper = mapper;
    }

    @Override
    public void performPostProcess(byte[] encodedBytes) {
        try {
            JsonNode root = mapper.readTree(encodedBytes);
            String url = root.at("/http_info/request/url").asText();
            if (!url.contains("/payments/")) return;
            String decision = root.at("/http_info/response/body/decision").asText("unknown");
            registry.counter("payments.decision", "outcome", decision).increment();
        } catch (IOException ignored) {
            // never let the post-processor break the logging pipeline
        }
    }
}
```

Common use cases:

- Publish Micrometer counters or distributions tagged by fields extracted
  from the response body (payment amount, decision code, partner ID).
- Emit a parallel audit event to a separate sink (e.g. an audit topic)
  without re-parsing the same payload in business code.
- Sample-based forensic capture: record 1/1000 raw access lines to a
  cold-storage bucket for later replay.

**Constraints:**

- The handler runs synchronously on the logging thread (or, when
  `wiretap.async-logging.enabled=true`, on the `AsyncAppender` thread).
  Keep it lock-free and bounded — long-running work backs up the logging
  pipeline.
- Throw nothing. Wrap parsing in `try/catch` — exceptions inside the
  post-processor must not break the calling logger.
- Only one handler is invoked per access-log event. If you need to fan
  out to multiple subscribers, write a dispatching handler.

## Header forwarding

By default the following inbound request headers are copied into SLF4J MDC under
keys equal to the header name: `x-request-id`, `x-session-key`, `lb-trace-id`.
Inside the request thread they are reachable via `MDC.get("x-request-id")` and
from any Logback `PatternLayout` token (`%X{x-request-id}`).

They do **not** automatically appear as fields in wiretap's JSON application log.
The default encoder pipeline only emits a fixed schema plus the MDC keys it knows
about (`traceId`, `spanId`, `HTTP-REQUEST-LOG`, `KAFKA-MESSAGE-LOG`, `LOG_EXTRA`).
To surface a forwarded header as a top-level field in every `log.info(...)` line,
register a `WiretapLogFieldProvider` that reads it back from MDC — see the SPI
example above (`TenantIdLogFieldProvider`).

Override the forwarded list when your infrastructure uses different conventions:

```yaml
wiretap:
  headers:
    forward-to-mdc:
      - x-request-id
      - x-correlation-id
      - x-trace-id
```

To also emit a header value as a field in the access log (e.g. `session_key`), use
`WiretapAccessFieldProvider`. Logback-access runs in a separate context from SLF4J
MDC, so the header must be read directly from the event — the provider below also
checks response headers as a fallback, which is useful when a session key is
established in the response (e.g. SOAP):

```java
@Component
public class SessionKeyFieldProvider implements WiretapAccessFieldProvider {
    @Override public String fieldName() { return "session_key"; }

    @Override public Object value(IAccessEvent event) {
        String v = event.getRequestHeaderMap().get("x-session-key");
        return v != null ? v : event.getResponseHeaderMap().get("x-session-key");
    }
}
```

## What gets logged

Wiretap captures HTTP traffic from six sources, each configurable independently.
Each source has its own property prefix:

| Traffic | Prefix | Toggle |
|---|---|---|
| Inbound (servlet) | `wiretap.rest-controllers.*` | Always on |
| Outbound `RestTemplate` | `wiretap.rest-template-interceptor.*` | `.enabled=false` to disable |
| Outbound `RestClient` | `wiretap.rest-client-interceptor.*` | `.enabled=false` to disable |
| Outbound `FeignClient` | `wiretap.feign-client-interceptor.*` | `.enabled=false` to disable |
| Outbound `WebClient` / `GraphQLWebClient` | `wiretap.web-client-interceptor.*` | `.enabled=false` to disable |
| Outbound `WebServiceTemplate` (SOAP) | `wiretap.web-service-template-interceptor.*` | `.enabled=false` to disable |
| Outbound Kafka producer | `wiretap.kafka-producer-interceptor.*` | `.enabled=false` to disable |
| Inbound Kafka consumer | `wiretap.kafka-consumer-interceptor.*` | `.enabled=false` to disable |

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

### Header capture and the `*` wildcard

Each HTTP source has a pair of explicit allow-lists — `request-headers` and
`response-headers` — and Kafka has a single `headers` list. Only headers
named in these lists make it into the log payload. Defaults are intentionally
small (`Content-Type` and `X-Forwarded-For` for HTTP, `x-trace-id` and
`x-request-id` for Kafka), because most production traffic carries
sensitive material such as `Authorization` or `Cookie` that should not
end up in logs by accident.

If you don't know the full set of headers in advance — common during early
development, debugging or when an upstream proxy injects unknown
correlation fields — use the wildcard:

```yaml
wiretap:
  rest-controllers:
    request-headers: ['*']        # log every inbound request header
  web-client-interceptor:
    response-headers: ['*']       # log every header WebClient receives back
  kafka-producer-interceptor:
    headers: ['*']                # log every record header sent
```

The wildcard works in `request-headers`, `response-headers` (for all HTTP
sources, inbound and outbound, including SOAP `MimeHeaders` plus the
underlying transport headers) and the Kafka `headers` list, including
per-URL `specific-http-info-settings` and per-topic
`specific-topic-settings` overrides. When `*` is present in the list, the
rest of the elements are ignored (the match is case-insensitive on the
literal asterisk). It does **not** apply to
`wiretap.headers.forward-to-mdc` — that list stays explicit by design,
because pushing arbitrary headers into MDC would inflate every log line
and risk leaking values into downstream systems.

Wildcard captures headers verbatim — wiretap does **not** strip
`Authorization`, `Cookie`, or any other sensitive header for you. For
Kafka, register a `KafkaHeaderMaskingHandler` to scrub header values.
For HTTP, configure `wiretap.message-masking` or scrub at the appender
level. Don't turn `*` on for inbound traffic without a plan for those
two headers.

### Body limits and masking

```yaml
wiretap:
  rest-controllers:
    http-body-settings:
      max-body-length: 10000        # truncate bodies longer than this
      max-field-length: 1000        # truncate string fields inside JSON bodies
      enable-body-truncating: true
      enable-body-masking: true     # call HttpBodyFieldMaskingHandler for each body field value
    enable-url-masking: true        # call HttpUrlMaskingHandler for the request URL
    enable-request-params-masking: true   # call HttpRequestParamsMaskingHandler per query param
```

Wiretap provides four independent masking SPI interfaces. Register only the beans
you need — each context is opt-in:

| Interface | Applied to | Activation |
|---|---|---|
| `io.wiretap.applog.message.handler.MessageMaskingHandler` | `message` field in app logs | bean present + `wiretap.message-masking=true` (default) |
| `io.wiretap.http.message.settings.body.HttpBodyFieldMaskingHandler` | each field value in HTTP request/response bodies (recursive, no URL context) | bean present + `enable-body-masking=true` |
| `io.wiretap.http.message.settings.body.HttpBodyMaskingHandler` | parsed JSON body as a whole, per-URL (structural — mask specific fields on specific endpoints) | bean present + `enable-body-masking=true`; first handler whose `appliesTo(url)` is `true` wins |
| `io.wiretap.http.message.HttpUrlMaskingHandler` | full request URL (path + query string) | bean present + `enable-url-masking=true` |
| `io.wiretap.http.message.HttpRequestParamsMaskingHandler` | each query parameter value in `request_params` | bean present + `enable-request-params-masking=true` (default) |

`HttpBodyMaskingHandler` (structural per-URL) and
`HttpBodyFieldMaskingHandler` (recursive per-field) compose: the
structural handler (if it matches) runs first on the JSON tree, then
the recursive field-handler (if registered) runs over the result. Use
the structural one for endpoint-specific rules, the recursive one for
blanket string patterns:

```java
@Component
public class CardLimitsMasker implements HttpBodyMaskingHandler {
    private static final List<String> FIELDS = List.of("remaining_auth", "remaining_cash");

    @Override public boolean appliesTo(String url) {
        return url.contains("/api/cardlimits");
    }

    @Override public JsonNode mask(JsonNode body) {
        if (body.isObject()) {
            FIELDS.forEach(name -> Optional.ofNullable(body.findValue(name))
                    .ifPresent(v -> ((ObjectNode) body).put(name, "***")));
        }
        return body;
    }
}
```

When no bean is registered for a context, data passes through unchanged regardless of
the flag value. Per-URL control via `specific-http-info-settings[].enable-body-masking`
(and `enable-url-masking`) follows the same rule — the handler is only called when both
the flag and the bean are present.

Disable message masking globally even when a bean is registered:

```yaml
wiretap:
  message-masking: false
```

### Skipping URLs entirely

```yaml
wiretap:
  rest-controllers:
    exclude-request-patterns:
      - "/actuator/.*"
      - "/health"
```

### Capturing inbound bodies

Inbound `request_body` / `response_body` capture rides on logback-access's
`TeeFilter`, which the logback-access starter ships **disabled**. Wiretap turns
it on by default — it contributes `logback.access.tee-filter.enabled=true` as an
overridable default — so inbound bodies are logged out of the box.

The `TeeFilter` buffers each request/response body in memory before the access
encoder runs. To opt out (e.g. high-throughput services with large payloads),
set `logback.access.tee-filter.enabled=false`. Outbound bodies are captured by
the client interceptors and do not depend on this filter.

### Buffering body across filter boundaries

Logback-access's standard `TeeFilter` captures bodies only when the
servlet filter chain runs in full and writes through the original
streams. When a custom filter, exception resolver, or framework
(e.g. Spring Security) replaces the response stream or interrupts the
chain, the body is gone by the time the access encoder runs.

For those edge cases, stash the body via `BufferedHttpBodyHolder` —
Wiretap reads it back when it builds the `http_info` payload:

```java
@Component
public class CapturedBodyExceptionResolver implements HandlerExceptionResolver {
    @Override
    public ModelAndView resolveException(HttpServletRequest req, HttpServletResponse res,
                                         Object handler, Exception ex) {
        String reqBody  = readMyCachedRequestBody(req);
        String respBody = renderErrorResponse(ex);
        BufferedHttpBodyHolder.put(req, new BufferedHttpMessageInfo(
                reqBody,  reqBody.length(),
                respBody, respBody.length()));
        // ...write the response as usual
        return new ModelAndView();
    }
}
```

The buffer is stored as an `HttpServletRequest` attribute, so it is
tied to the request lifecycle (no `ThreadLocal`, safe under virtual
threads, no explicit cleanup needed).

## WebClient: non-blocking semantics and known limitations

`WebClient` is a non-blocking HTTP client whose main wins are (1) many concurrent
connections per event-loop thread and (2) backpressure for streaming bodies.
Logging an HTTP exchange necessarily touches both — Wiretap aims to keep the
filter pragmatic on the hot path while documenting the trade-offs honestly.

**What the filter does that is fully non-blocking**

- The reactive pipeline itself — capture, log, replay — stays inside
  Reactor operators, no blocking I/O calls.
- Connection-per-thread stays N:1; the event-loop thread is not pinned per
  request.

**What still runs synchronously on the event-loop thread (by default)**

- Jackson serialization of the captured HTTP info (microseconds for small
  payloads, can climb to milliseconds for larger ones).
- The `log.info(...)` call goes through Logback's appender chain; the built-in
  `ConsoleAppender` and `RollingFileAppender` write synchronously. Under load
  (high QPS, large bodies) this becomes the dominant cost.

**Streaming-aware capture (automatic)**

Responses with the following content types are logged with metadata only —
the body Flux is not joined or mutated, so SSE / NDJSON / large downloads pass
through untouched:

```
text/event-stream         application/grpc
application/x-ndjson      application/grpc+proto
application/octet-stream  application/grpc+json
multipart/x-mixed-replace
```

The `response_body` field will contain the marker
`[streaming response — body not captured]` instead of the actual body.
No configuration is required.

**Visibility-aware capture**

When `REQUEST_BODY` or `RESPONSE_BODY` visibility is `false` (globally or
per-URL), the corresponding body is not captured at all — no decorator is
attached to the request, no buffer drain happens on the response. This saves
both memory and CPU compared to capturing first and discarding later.

**Bounded capture**

The captured string for the log line is hard-capped at
`http-body-settings.max-body-length` (default 2KB). Bodies larger than this
get the marker `...[truncated]` appended in the log; the full body is still
delivered to the application unchanged.

**Async logging (recommended for high-throughput WebClient workloads)**

Wrap Wiretap's built-in appenders in a Logback `AsyncAppender`:

```yaml
wiretap:
  async-logging:
    enabled: true
    queue-size: 1024
    never-block: true              # drop events on overflow instead of blocking
    discarding-threshold: 0        # 0 = never discard (default keeps Logback's queueSize/5)
```

With this enabled, `log.info(...)` returns to the event-loop thread as soon as
the event is queued; the actual write happens on a dedicated worker thread.
Confirm by checking the thread name on a logged WebClient line — it should be
`AsyncAppender-Worker-...` rather than `reactor-http-nio-N`.

**WebClient + Wiretap vs RestTemplate + Wiretap**

| | RestTemplate + Wiretap | WebClient + Wiretap |
|---|---|---|
| Threading model | One thread per request | N requests per event-loop thread |
| Body capture | Always full (in-memory) | Capped at `max-body-length` for the log line |
| Streaming bodies | n/a (RestTemplate doesn't stream) | Auto-skipped |
| Sync `log.info()` cost | Cheap (dedicated thread) | Pins the event-loop until appender returns — use `wiretap.async-logging.enabled` |

If your WebClient calls are mostly small REST/GraphQL request/response and you
serve modest QPS, the defaults are fine. For high-QPS, large-body, or
streaming workloads, enable `async-logging` and double-check that
`max-body-length` is tight enough to keep per-request memory predictable.

## Kafka logging

When `spring-kafka` is on the classpath, Wiretap emits exactly one
JSON log line per Kafka message on each side of the pipe — producer
and consumer — both carrying a uniform `kafka_info` block with full
record snapshot, `status` (`SUCCESS` / `ERROR`), and the outcome of
the operation.

### Producer

Producer-side logs come from a Spring Kafka
`org.springframework.kafka.support.ProducerListener`. The hook fires
**after** the broker has acknowledged (or rejected) the send, so the
single log line carries both the typed pre-serialization snapshot
(`key`, `value`, headers as the application produced them) and the
broker-side coordinates (`partition`, `offset`):

```json
{
  "@timestamp": "...",
  "level": "INFO",
  "logger": "io.wiretap.kafka.KafkaLogSink",
  "message": "Sent outgoing kafka message orders.events",
  "kafka_info": {
    "direction": "OUTGOING",
    "topic": "orders.events",
    "partition": 3,
    "offset": 18472,
    "key": "ord-42",
    "key_length": 6,
    "value": "{\"orderId\":\"ord-42\",\"amount\":100}",
    "value_length": 33,
    "headers": { "x-trace-id": "0123456789abcdef", "b3": "..." },
    "timestamp": "2026-05-07T10:14:32.918Z",
    "status": "SUCCESS"
  }
}
```

On a failed send the same line comes through at `WARN` with
`status=ERROR` plus `error_class` and `error_message`:

```json
{
  "level": "WARN",
  "message": "Failed to send outgoing kafka message orders.events",
  "kafka_info": {
    "direction": "OUTGOING",
    "topic": "orders.events",
    "key": "ord-42",
    "value": "{...}",
    "status": "ERROR",
    "error_class": "org.apache.kafka.common.errors.TimeoutException",
    "error_message": "Expiring 1 record(s) for orders.events-3: 30000 ms has passed since batch creation"
  }
}
```

Spring Boot's auto-configured `KafkaTemplate` picks up the
`ProducerListener` bean automatically through `ObjectProvider`.
Manually constructed templates (multi-cluster setups) attach it
explicitly — see «Custom KafkaTemplate» below.

```yaml
wiretap:
  kafka-producer-interceptor:
    enabled: true                            # opt-out toggle
    headers:                                 # which headers end up in the log
      - x-trace-id
      - x-request-id
    visibility-settings:
      VALUE: true                            # also: TOPIC PARTITION CLIENT_ID KEY HEADERS TIMESTAMP STATUS ...
    enable-value-masking: true               # call KafkaValueMaskingHandler for key/value
    enable-headers-masking: true             # call KafkaHeaderMaskingHandler for header values
    enable-topic-masking: true               # call KafkaTopicMaskingHandler for topic name
    message-body-settings:
      enable-value-truncating: true
      max-value-length: 2000
      enable-value-masking: true             # combines with the top-level enable-value-masking
    exclude-topic-patterns:
      - ".*\\.internal\\..*"
    specific-topic-settings:
      - match-topic-pattern: "orders\\..*"
        visibility-settings:
          VALUE: false                       # don't log payloads on the orders.* family
```

Three opt-in masking SPIs are exposed; register a single Spring bean each:

| Interface | Applied to |
|---|---|
| `io.wiretap.kafka.message.KafkaValueMaskingHandler` | `key` and `value` |
| `io.wiretap.kafka.message.KafkaHeaderMaskingHandler` | each header value |
| `io.wiretap.kafka.message.KafkaTopicMaskingHandler` | topic name |

The same SPIs apply to consumer-side logs as well — registering a
single bean covers both directions.

### Consumer

Consumer-side logs come from a Spring Kafka
`org.springframework.kafka.listener.RecordInterceptor` attached to
listener containers via Spring Boot's `ContainerCustomizer` SPI. The
hook fires on `success(record, consumer)` / `failure(record, exception,
consumer)` — **after** the `@KafkaListener` method has finished — so
the single log line includes:

- the typed pre-deserialization snapshot Kafka delivered,
- `duration` of the listener invocation in milliseconds,
- `status` (`SUCCESS` / `ERROR`), and on failure `error_class` /
  `error_message`.

Because the callback runs inside the Spring Kafka listener
observation span, MDC already carries `traceId` / `spanId` by the time
wiretap logs — `kafka_info` correlates with the listener's own log
lines without any header parsing, even when the upstream producer did
not propagate a trace (observation opens a fresh root span in that
case and wiretap inherits it).

If listener observation is disabled
(`spring.kafka.listener.observation-enabled=false`), wiretap still
tries to extract a trace from `b3` / `traceparent` record headers as a
fallback, so a producer that propagates does not silently lose its
trace either way.

```json
{
  "level": "INFO",
  "message": "Processed incoming kafka message orders.events in 47ms",
  "kafka_info": {
    "direction": "INCOMING",
    "topic": "orders.events",
    "partition": 3,
    "offset": 18472,
    "client_id": "checkout-api-consumer-1",
    "group_id": "checkout-group",
    "key": "ord-42",
    "value": "{\"orderId\":\"ord-42\"}",
    "headers": { "b3": "...", "x-trace-id": "abc" },
    "timestamp": "2026-05-07T10:14:32.918Z",
    "timestamp_type": "CREATE_TIME",
    "duration": 47,
    "status": "SUCCESS"
  }
}
```

On a listener exception the level becomes `WARN`:

```json
{
  "level": "WARN",
  "message": "Failed to process incoming kafka message orders.events after 350ms",
  "kafka_info": {
    "direction": "INCOMING",
    "topic": "orders.events",
    "partition": 3,
    "offset": 18472,
    "key": "ord-42",
    "value": "{...}",
    "duration": 350,
    "status": "ERROR",
    "error_class": "com.example.OrderValidationException",
    "error_message": "amount must be positive"
  }
}
```

A listener that hangs past `max.poll.interval.ms` still produces a
log line: Spring Kafka raises an exception that reaches `failure(...)`.
The only sliver that can fall through is a JVM crash mid-processing,
where no fallback would help.

```yaml
wiretap:
  kafka-consumer-interceptor:
    enabled: true
    visibility-settings:
      VALUE: true
    exclude-topic-patterns:
      - "__consumer_offsets"
```

### kafka_info fields reference

The same `kafka_info` schema covers both directions; some fields are
filled only on one side or only in failure cases. All fields can be
hidden via `visibility-settings` (enum keys correspond to the column
names below).

| Field | Type | Producer (OUTGOING) | Consumer (INCOMING) | Notes |
|---|---|---|---|---|
| `direction` | string | `OUTGOING` | `INCOMING` | Always present. |
| `topic` | string | always | always | Source topic of the record. |
| `partition` | int | broker-assigned on ack | always | Producer side: post-ack value from `RecordMetadata`. |
| `offset` | long | broker-assigned on ack | always | Producer side: post-ack value. |
| `client_id` | string | (omitted) | always | Producer side: not available from `ProducerListener` — Kafka exposes producer client-id only through JMX/Micrometer metrics. |
| `group_id` | string | — | always | Consumer's `group.id`. |
| `key` | string | always | always | `String.valueOf(key)` — typed value before serialization. |
| `key_length` | long | always | always | Byte length of `key` (UTF-8 for strings, native for byte arrays). |
| `value` | string | always | always | Same shape as `key` — typed pre-serialization value. |
| `value_length` | long | always | always | Byte length of `value`. |
| `headers` | object | configurable | configurable | Names in `wiretap.kafka-*-interceptor.headers` (use `['*']` to log them all). Values joined by `;` if multi-valued. |
| `timestamp` | ISO-8601 string | conditional | always | See **«About `timestamp`»** below. |
| `timestamp_type` | string | (typically omitted) | always | `CREATE_TIME` or `LOG_APPEND_TIME`. See below. |
| `duration` | long (ms) | (omitted) | always | Consumer-side listener invocation time. Producer-side latency is covered by Kafka native metrics. |
| `status` | enum | `SUCCESS` / `ERROR` | `SUCCESS` / `ERROR` | Outcome of the send / listener invocation. |
| `error_class` | string | on failure | on failure | FQN of the exception. |
| `error_message` | string | on failure | on failure | `exception.getMessage()`. |

### JSON payload formatting

When `key` or `value` happens to parse as a JSON object or array, wiretap
runs it through `OBJECT_MAPPER.writerWithDefaultPrettyPrinter()` before
emitting, so the string ends up multi-line (real `\n` characters inside).
Log aggregators (Kibana / Splunk / Grafana Loki) render this as a
formatted payload block instead of a collapsed one-liner. Scalars,
plain strings and malformed JSON are emitted verbatim with no
transformation.

The `value` field stays a **string** in the final JSON — wiretap
deliberately does not embed payloads as nested objects under
`kafka_info.value`. Across topics, payload shapes vary; turning them
into nested objects would force the log aggregator to index every
shape, producing `mapping conflict` errors in Elasticsearch /
OpenSearch as soon as two records use the same top-level field name
with different types.

Pretty-printing runs **after** any `KafkaValueMaskingHandler` (the
handler still sees the original single-line payload, so existing
regex-based masks are not broken) and **before** `enable-value-truncating`
(the length limit applies to the final pretty-printed text).

### About `timestamp` and `timestamp_type`

This field is the most frequently misread, so it gets its own block.

#### Where the value comes from

**Producer (`OUTGOING`)**:
- If the application explicitly built the record with a timestamp
  (`new ProducerRecord<>(topic, partition, timestamp, key, value)`),
  that value is logged.
- Otherwise, if `RecordMetadata.hasTimestamp()` is true after the
  broker ack, the broker-side timestamp is logged as a fallback.
- Otherwise the field is omitted.
- `timestamp_type` is **typically omitted** on producer-side; the
  Kafka client does not expose it for outbound records.

**Consumer (`INCOMING`)**:
- Always `ConsumerRecord.timestamp()` — the value broker stored and
  every consumer sees. Which clock that is — producer's send time
  (`CREATE_TIME`) or broker's append time (`LOG_APPEND_TIME`) —
  depends on the topic-level `message.timestamp.type` config. Default
  in Kafka 3.x is `CreateTime`.
- `timestamp_type` carries the discriminator (`CREATE_TIME` /
  `LOG_APPEND_TIME`). `NO_TIMESTAMP_TYPE` is treated as «not set» and
  the field is omitted.

#### What it does **not** mean

- The `OUTGOING` `timestamp` is **not** «when the broker wrote the
  record». It is either an application-supplied timestamp or
  best-effort broker-ack timestamp, and on a topic with
  `message.timestamp.type=LogAppendTime` these can differ from the
  broker's actual log-append moment.
- The `INCOMING` `timestamp` is **not** «when the consumer received
  the record». For end-to-end latency, compare it with `@timestamp`
  of the `INCOMING` log line and treat the difference as «time spent
  in the topic between broker write and consumer dispatch».

#### Custom KafkaTemplate

A manually constructed `KafkaTemplate` (multi-cluster setups,
per-tenant templates) does not go through Spring Boot's
KafkaAutoConfiguration. Inject the wiretap `ProducerListener` and
attach it explicitly:

```java
@Configuration
public class KafkaConfig {

    private final List<ProducerListener<Object, Object>> producerListeners;

    public KafkaConfig(List<ProducerListener<Object, Object>> producerListeners) {
        this.producerListeners = producerListeners;
    }

    @Bean("pciDssKafkaTemplate")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public KafkaTemplate<String, String> pciDssKafkaTemplate(ProducerFactory<String, String> factory) {
        KafkaTemplate<String, String> template = new KafkaTemplate<>(factory);
        producerListeners.forEach(l -> template.setProducerListener((ProducerListener) l));
        template.setObservationEnabled(true);   // for trace propagation through headers
        return template;
    }
}
```

`setProducerListener` is a single-slot setter — if your application
already has its own listener, wrap both with
`org.springframework.kafka.support.CompositeProducerListener` before
setting.

#### Custom listener container factories

The auto-configured `ConcurrentKafkaListenerContainerFactory` from
Spring Boot picks up wiretap's `ContainerCustomizer` automatically.
If you build a factory by hand (multi-cluster setups, per-tenant
listeners), inject the wiretap customizer and apply it yourself:

```java
@Configuration
public class KafkaConfig {

    private final List<ContainerCustomizer<Object, Object,
            ConcurrentMessageListenerContainer<Object, Object>>> customizers;

    public KafkaConfig(List<ContainerCustomizer<Object, Object,
            ConcurrentMessageListenerContainer<Object, Object>>> customizers) {
        this.customizers = customizers;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> listenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        // ... your consumer factory, error handler, etc.
        customizers.forEach(c -> factory.setContainerCustomizer(
                (ContainerCustomizer) c));
        return factory;
    }
}
```

`setContainerCustomizer` is a single-slot setter on the factory, so if
your application already has its own `RecordInterceptor` (e.g. for
retry-state cleanup), wrap both with
`org.springframework.kafka.listener.CompositeRecordInterceptor` before
setting.

### Distributed tracing across Kafka

Two actors are involved:

1. **Producer-side propagation** — `KafkaTemplate.send` must inject the
   propagation header (`b3` or W3C `traceparent`, depending on
   `management.tracing.propagation.type`). This is what
   `spring.kafka.template.observation-enabled=true` turns on. Without
   it nothing crosses the wire, and the trace ends at the producer
   service.

2. **Consumer-side MDC** — Spring Kafka's listener observation opens a
   span around each record, picking up the parent from the propagation
   header (or starting a fresh root span if the producer didn't
   propagate). That span's `traceId` / `spanId` end up in MDC, which
   wiretap inherits naturally because its `RecordInterceptor` runs
   inside that span. The relevant property is
   `spring.kafka.listener.observation-enabled=true`.

So the practical recipe — both flags on:

```yaml
spring:
  kafka:
    template:
      observation-enabled: true
    listener:
      observation-enabled: true
```

`kafka_info` carries `trace_id` even when only the listener flag is
on (the trace will just be unrelated to the producer's). If you turn
listener observation off altogether, wiretap falls back to extracting
`b3` / `traceparent` from record headers directly, so a producer that
does propagate still produces a correlated consumer log.

### Fire-and-forget and edge cases

`KafkaTemplate.send(...)` is always asynchronous — it returns
`CompletableFuture<SendResult<K, V>>`. «Fire-and-forget» on the caller
side (dropping the future) does **not** affect wiretap logging:
`ProducerListener` is attached inside the template, and the
`Callback` Spring Kafka registers with `KafkaProducer.send(...)` fires
regardless of what the application does with the returned future.

Scenarios:

| Situation | wiretap behaviour |
|---|---|
| Broker ack OK | `INFO Sent outgoing kafka message {topic}` + `status=SUCCESS` |
| Timeout / retry exhausted / broker rejected | `WARN Failed to send outgoing kafka message {topic}` + `status=ERROR` + `error_class` / `error_message` |
| `KafkaProducer.close()` with pending records | Each pending record gets a `ProducerFencedException` (or similar) → `WARN` line per record |
| `max.block.ms` elapses inside `send()` (producer buffer full) | `send` throws **synchronously** before the record reaches the producer's queue. No callback fires, so no `kafka_info` line. The exception propagates to the caller — wrap fire-and-forget sends in `try / catch` if you want to log these yourself, or attach a `.whenComplete(...)` to the future. |
| JVM crash mid-send | No log line — no mechanism can recover this. |
| Direct `KafkaProducer.send(...)` bypassing `KafkaTemplate` | wiretap doesn't see it — auto-registration is on the template, not on the raw producer. |

### A note about the producer-side thread

`onSuccess` / `onError` callbacks run on the producer-IO thread
(`kafka-producer-network-thread | …`), not on the caller of `send()`.
MDC on that thread is empty by default — `trace_id` ends up in
`kafka_info OUTGOING` because Spring Kafka observation propagates the
context through Micrometer Context Propagation, which Spring Boot
configures automatically. If you disable that propagation explicitly
or run a Spring Boot version without it, producer-side `kafka_info`
may end up without `trace_id`. Consumer-side is unaffected — the
listener observation span is opened locally there.

## Tracing

Wiretap reads `trace_id` and `span_id` from the active Micrometer Tracing context
(any backend — Brave, OpenTelemetry, …). When Brave is on the classpath the
library configures a 64-bit single-header B3 propagator by default; disable that
behaviour with `wiretap.tracing.propagation.type.b3.enabled=false`.

The `lb_trace_id` field is sourced from the inbound `lb-trace-id` request header,
intended for load-balancer-emitted IDs.

## Metrics

Wiretap publishes Micrometer metrics about its **own** processing overhead so
you can quantify what logging costs in production. Metrics activate
automatically whenever a `MeterRegistry` bean exists in the context
(typically through `spring-boot-starter-actuator`); without one the library
installs a no-op facade and adds zero overhead.

```yaml
wiretap:
  metrics:
    enabled: true             # master switch (default true)
    detailed-timings: false   # per-phase (parse/mask/truncate/serialize) timers
    histograms: false         # add Prometheus heatmap buckets + p50/p95/p99
    tags:
      topic: false            # include Kafka topic as a tag (cardinality risk)
      status: true            # include grouped HTTP status (2xx/3xx/4xx/5xx)
    async-appender:
      enabled: true           # queue gauges when wiretap.async-logging.enabled
```

### Metric catalogue

Always published (when enabled and a `MeterRegistry` is present):

| Metric                              | Type                 | Tags                                                   | Notes |
|-------------------------------------|----------------------|--------------------------------------------------------|-------|
| `wiretap.http.overhead`             | Timer (seconds)      | `direction`, `client`, `outcome`, `status`             | Wiretap-attributable overhead per HTTP request — the downstream call time is excluded (see note below) |
| `wiretap.http.requests`             | Counter              | `direction`, `client`, `outcome`, `status`             | Co-emitted with the timer |
| `wiretap.http.skipped`              | Counter              | `direction`, `client`, `reason`                        | Requests that bypassed logging |
| `wiretap.http.body.size`            | DistributionSummary (bytes) | `direction`, `client`, `content_type_class`, `kind` (`request`/`response`) | Captured body size |
| `wiretap.http.body.capture.failures`| Counter              | `direction`, `client`, `phase`                         | Body-pipeline exceptions, emitted consistently across every client (incoming servlet + all outgoing): `phase=capture` (reading/parsing the body) or `phase=serialize` (rendering the MDC JSON) |
| `wiretap.kafka.overhead`            | Timer                | `direction` (`producer`/`consumer`), `outcome`         | Full pipeline overhead per Kafka message |
| `wiretap.kafka.messages`            | Counter              | `direction`, `outcome`                                 | Co-emitted with the timer |
| `wiretap.kafka.skipped`             | Counter              | `direction`, `reason`                                  | Skip causes (excluded topic / null record) |
| `wiretap.kafka.message.size`        | DistributionSummary (bytes) | `direction`                                     | Message value size |
| `wiretap.kafka.body.capture.failures`| Counter             | `direction`, `phase`                                   | Kafka body-pipeline exceptions — Kafka counterpart of `wiretap.http.body.capture.failures` (`phase=capture` masking/parsing, `phase=serialize` JSON rendering) |

> **`wiretap.http.overhead` measures the logging cost, not request latency.** For
> outgoing clients the downstream call time is measured in **nanoseconds** and
> subtracted, so the timer reflects only wiretap's own work (capture, parse, mask,
> serialise) with no millisecond rounding. The subtracted downstream is the network
> call for RestTemplate/RestClient/Feign, the time until the response is received
> (headers, plus the body for buffered responses) for WebClient, and the SOAP
> round-trip for WebServiceTemplate. For incoming (servlet) there is no downstream
> to subtract, so the timer covers the full JSON rendering of the access log in
> `LazyJsonAccessEncoder.encode()` (every provider, including `http_info`). The
> log's `duration`/`elapsedTime` field stays in milliseconds (the user-facing
> latency) and is independent of this nanosecond-precise overhead timer. Enable the
> `wiretap.metrics.detailed-timings` phase timers below for per-phase attribution.

Opt-in under `wiretap.metrics.detailed-timings=true`:

| Metric                          | Tags                                                  | Notes |
|---------------------------------|-------------------------------------------------------|-------|
| `wiretap.body.phase`            | `phase`, `direction`, `client`, `content_type_class`  | Per-phase body-processing timer. HTTP body emits `parse` / `mask` / `truncate` from `DefaultBodyParser`, tagged with the real `direction` (`incoming`/`outgoing`) and `client` (`servlet`/`webclient`/…) of the call. Kafka body emits the same three phases from `KafkaLogSink.renderValue` with `client=kafka` and `direction=producer`/`consumer`. |
| `wiretap.json.serialization`    | `sink`, `direction`, `client`                         | `ObjectMapper.writeValueAsString` time |
| `wiretap.body.masker.invocation`| `masker_class`, `direction`                           | Per `HttpBodyMaskingHandler` invocation on the HTTP side; per `KafkaValueMaskingHandler` invocation on the Kafka side (`masker_class` = handler FQN). |

Opt-in under `wiretap.async-logging.enabled=true`
(plus `wiretap.metrics.async-appender.enabled=true`, on by default):

| Metric                                  | Type  | Tags         | Notes |
|-----------------------------------------|-------|--------------|-------|
| `wiretap.async.appender.queue.size`     | Gauge | `appender`   | Current buffered events |
| `wiretap.async.appender.queue.capacity` | Gauge | `appender`   | Configured `queueSize` |
| `wiretap.async.appender.queue.remaining`| Gauge | `appender`   | Capacity − size |

### Tag value reference

- `direction`: `incoming` / `outgoing` (HTTP), `producer` / `consumer` (Kafka).
- `client`: `servlet` (incoming) / `webclient` / `restclient` / `resttemplate` / `feign` / `webservicetemplate` (HTTP); `kafka` (Kafka body pipeline — only on `wiretap.body.phase` and `wiretap.body.masker.invocation`).
- `outcome`: `success` / `client_error` (4xx) / `server_error` (5xx) / `exception` (HTTP); `success` / `error` (Kafka).
- `status`: `2xx` / `3xx` / `4xx` / `5xx` / `other` / `exception` — never the raw status code.
- `content_type_class`: `json` / `xml` / `text` / `binary` / `other`.
- `phase`: on `wiretap.body.phase` — `parse` / `mask` / `truncate`; on `wiretap.{http,kafka}.body.capture.failures` — `capture` (reading/parsing the body) / `serialize` (rendering the MDC JSON).
- `reason`: `exclude_pattern` / `exclude_topic` / `streaming` / `unsupported_content_type` / `visibility_disabled` / `null_topic` / `null_record`.

### Scraping

Combine with `spring-boot-starter-actuator` and pick your exporter
(`micrometer-registry-prometheus`, Datadog, etc.). All wiretap metrics use the
`wiretap.` prefix, so a single Prometheus rule matches the lot:

```bash
curl localhost:8080/actuator/prometheus | grep '^wiretap_'
```

## Pretty printing

For local development, set `wiretap.pretty-print=true` to emit multi-line JSON.
Leave it `false` (default) in production — log shippers parse single-line JSON faster.

When pretty-print is on, the `stack_trace` field is rendered as a JSON
**array of strings** (one element per line of the rendered trace) instead
of a single embedded string. That is because a JSON pretty-printer cannot
break lines inside a string literal — without this trick, a long stack
trace becomes a horizontal scroll bar in the terminal. The same lengths
(`wiretap.stacktrace.max-depth`, `wiretap.stacktrace.max-length`) and the
same `ShortenedThrowableConverter` are used in both modes, so the only
difference is the JSON shape:

```text
# pretty-print=false (production, default) — single string
"stack_trace": "java.lang.RuntimeException: boom\n\tat com.example..."

# pretty-print=true (local development) — array of strings
"stack_trace": [
  "java.lang.RuntimeException: boom",
  "\tat com.example.Foo.bar(Foo.java:42)",
  "\tat ..."
]
```

Don't enable pretty-print in environments that ship logs to Elasticsearch /
OpenSearch — switching the field type between string and array on the
same index will cause a mapping conflict.

## Compatibility

Wiretap is published as **one artifact per tested Spring Boot patch
version**. The coordinate name encodes the exact target, so the choice
is unambiguous:

| Artifact                              | Spring Boot       | Java        | Status |
|---------------------------------------|-------------------|-------------|--------|
| `wiretap-spring-boot-3.2.7-starter`   | 3.2.7 (baseline)  | 17, 21      | extended support — kept working for legacy consumers |
| `wiretap-spring-boot-3.4.5-starter`   | 3.4.5             | 17, 21, 25  | actively tested |
| `wiretap-spring-boot-3.5.14-starter`  | 3.5.14            | 17, 21, 25  | actively tested, last 3.x minor |
| `wiretap-spring-boot-4.0.6-starter`   | 4.0.6             | 17, 21, 25  | actively tested, first SB 4 / Jackson 3 line |

The split exists because Logback 1.5 moved `IAccessEvent` from
`ch.qos.logback.access.spi` to `ch.qos.logback.access.common.spi` and
Spring Boot 4 bumped to Jackson 3 with relocated client modules — a
single jar cannot satisfy all the active branches. Pick the coordinate
that matches your Spring Boot version. See
[`COMPATIBILITY.md`](./COMPATIBILITY.md) for the deprecation policy and
verification scripts.

> **Deprecated:** `io.github.alexander-kuznetsov:wiretap` (without the
> `-spring-boot-X.Y.Z-starter` suffix) is frozen at `0.1.6`. Migrate to
> `wiretap-spring-boot-3.2.7-starter` for the equivalent contents and
> future patches.

Override the matrix per build:

```bash
./gradlew test -PspringBootVersion=3.5.14 -PjavaToolchain=21
```

## Building from source

```bash
./gradlew build
```

Tests use JUnit 5 + WireMock + AssertJ. Mockito requires
`-Dnet.bytebuddy.experimental=true` on Java 25, which is configured in
`build.gradle`.

## License

Apache License 2.0 — see [LICENSE](./LICENSE).
