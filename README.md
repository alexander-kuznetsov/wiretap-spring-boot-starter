[RU](README.ru.md) | EN

# Wiretap

> Structured JSON logging for Spring Boot applications, with HTTP request/response
> capture across servlet, RestTemplate, RestClient, FeignClient, WebClient, and WebServiceTemplate.

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**Status:** `0.1.0-SNAPSHOT` — work in progress, public API not yet stable. Do not use in production.

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

```gradle
dependencies {
    implementation 'io.wiretap:wiretap:0.1.0-SNAPSHOT'
}
```

That's it — no configuration is required. Wiretap auto-configures itself via Spring Boot's
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

Wiretap provides two SPI interfaces for adding custom fields:

- **`WiretapAccessFieldProvider`** — adds fields to HTTP access logs (inbound and outbound HTTP calls).
- **`WiretapLogFieldProvider`** — adds fields to application logs (`log.info(...)`, `log.error(...)`, etc.).

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

## Header forwarding

By default the following inbound request headers are mirrored into MDC so any
`log.info(...)` call inside the request thread is automatically tagged with them:
`x-request-id`, `x-session-key`, `lb-trace-id`.

Override the list when your infrastructure uses different conventions:

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

### Body limits and masking

```yaml
wiretap:
  rest-controllers:
    http-body-settings:
      max-body-length: 10000        # truncate bodies longer than this
      max-field-length: 1000        # truncate string fields inside JSON bodies
      enable-body-truncating: true
      enable-body-masking: true     # call HttpBodyMaskingHandler for each body field value
    enable-url-masking: true        # call HttpUrlMaskingHandler for the request URL
    enable-request-params-masking: true   # call HttpRequestParamsMaskingHandler per query param
```

Wiretap provides four independent masking SPI interfaces. Register only the beans
you need — each context is opt-in:

| Interface | Applied to | Activation |
|---|---|---|
| `io.wiretap.applog.message.handler.MessageMaskingHandler` | `message` field in app logs | bean present + `wiretap.message-masking=true` (default) |
| `io.wiretap.http.message.settings.body.HttpBodyMaskingHandler` | each field value in HTTP request/response bodies | bean present + `enable-body-masking=true` |
| `io.wiretap.http.message.HttpUrlMaskingHandler` | full request URL (path + query string) | bean present + `enable-url-masking=true` |
| `io.wiretap.http.message.HttpRequestParamsMaskingHandler` | each query parameter value in `request_params` | bean present + `enable-request-params-masking=true` (default) |

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

When `spring-kafka` is on the classpath, Wiretap auto-registers a Kafka
`ProducerInterceptor` that captures every produced record. The hook is
`org.apache.kafka.clients.producer.ProducerInterceptor.onSend(...)` — it runs
**before** the configured `Serializer` touches the payload, so the log line
holds the typed `key` / `value` exactly as the application produced them, not
the on-the-wire bytes:

```json
{
  "@timestamp": "...",
  "level": "INFO",
  "logger": "io.wiretap.kafka.KafkaLogSink",
  "message": "Captured outgoing kafka message orders.events",
  "kafka_info": {
    "direction": "OUTGOING",
    "topic": "orders.events",
    "partition": null,
    "client_id": "checkout-api-producer-1",
    "key": "ord-42",
    "key_length": 6,
    "value": "{\"orderId\":\"ord-42\",\"amount\":100}",
    "value_length": 33,
    "headers": { "x-trace-id": "0123456789abcdef" }
  }
}
```

Broker-side fields (`partition`, `offset`, `duration`) are not known at the
pre-serialization point. A second log line is emitted from
`onAcknowledgement` **only on delivery failure** with `status=ERROR`,
`error_class`, `error_message` and whatever metadata the broker returned.
Successful acknowledgements are already covered by the `onSend` line.

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

The registration mechanism is the Spring Boot
`DefaultKafkaProducerFactoryCustomizer` — Wiretap adds
`interceptor.classes=io.wiretap.kafka.producer.WiretapProducerInterceptor`
to the producer factory; you keep using `KafkaTemplate` / `@KafkaListener`
without further wiring.

### Consumer

The consumer side is symmetric. Wiretap registers a Kafka
`ConsumerInterceptor` via `DefaultKafkaConsumerFactoryCustomizer`. The
hook is `org.apache.kafka.clients.consumer.ConsumerInterceptor.onConsume(...)`
— it runs **after** the configured `Deserializer` has produced typed
`key` / `value`, but **before** the records are returned to the
application listener. That gives the same object representation the
listener will see, while letting the log line precede any business
processing (and any deserialization failures happen earlier and surface
as broker-level errors, not as missing log entries).

```json
{
  "kafka_info": {
    "direction": "INCOMING",
    "topic": "orders.events",
    "partition": 3,
    "offset": 18472,
    "client_id": "checkout-api-consumer-1",
    "group_id": "checkout-group",
    "key": "ord-42",
    "value": "{\"orderId\":\"ord-42\"}",
    "timestamp": "2026-05-07T10:14:32.918Z",
    "timestamp_type": "CREATE_TIME"
  }
}
```

```yaml
wiretap:
  kafka-consumer-interceptor:
    enabled: true
    visibility-settings:
      VALUE: true
    exclude-topic-patterns:
      - "__consumer_offsets"
```

`onCommit` is not logged — commit-time activity is not interesting for
an access log. The three masking SPIs (`KafkaValueMaskingHandler`,
`KafkaHeaderMaskingHandler`, `KafkaTopicMaskingHandler`) apply to the
consumer side as well; registering a single bean covers both directions.

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
