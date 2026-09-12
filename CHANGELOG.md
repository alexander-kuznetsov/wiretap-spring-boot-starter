# Changelog

All notable changes are recorded here.
This project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html);
versions before `1.0.0` are pre-release and the public API may change between minors.

## [Unreleased]

### Fixed
- A `WebServiceTemplate` built through Spring's auto-configured
  `WebServiceTemplateBuilder` now logs SOAP traffic out of the box, as the README
  has always promised. The starter registered the logging interceptor as a bare
  `ClientInterceptor` bean, which the builder never consults — it only applies
  `WebServiceTemplateCustomizer` beans — so SOAP logging worked only for templates
  whose interceptor chain the application assembled by hand. A customizer now
  appends the interceptor after the ones the application configured and skips a
  chain that already carries it, so a template wired by hand keeps logging once.
  `wiretap.web-service-template-interceptor.enabled=false` switches the customizer
  off, matching the toggle every other outbound client already had.
- `wiretap.file-logging.enabled` and `wiretap.file-logging.path` now reach the
  access log as well. The access fragment bound them under dash-separated names —
  `wiretap.file-logging-enabled` and `wiretap.file-logging-path` — which no
  documentation ever mentioned and relaxed binding does not reconcile with a dotted
  path, so the `HTTP_FILE-ROLLING` appender stayed inactive whatever the
  documented keys were set to and inbound traffic reached the file only through
  stdout redirection. Both fragments now read the documented keys, so one toggle
  switches application and access file appenders together. Anyone who found the
  undocumented spelling and set `wiretap.file-logging-enabled` loses their file
  appender and should move to `wiretap.file-logging.enabled`.

## [2.1.0] - 2026-09-03

A Kafka record can now be kept out of the log by a bean, not only by a topic
pattern — useful when one topic carries traffic from several producers and only
part of it is worth logging. Nothing changes without that bean.

### Added
- `KafkaRecordLogFilter` — an opt-in SPI deciding per record whether a Kafka log
  line is written at all. One topic often carries traffic from several producers
  while only part of it is worth logging; a single Spring bean now expresses that
  rule in code (several key fields, nesting, per-topic policies, values pulled from
  a feature flag) instead of a configuration DSL. The filter runs after
  `exclude-topic-patterns` and before masking, truncation and serialisation, so it
  reads the raw record and a rejected one pays for nothing else. It receives the
  whole `KafkaMessageInfo` snapshot — key, value, headers, direction, status,
  duration — and one bean covers both producer and consumer. Headers are the one
  field that is not raw: they are already narrowed to the names listed under
  `headers`, so a predicate cannot branch on one wiretap does not collect. Without a bean every
  record is logged, so 2.0.x behaviour is unchanged. Rejected records are counted
  as `wiretap.kafka.skipped{reason="filter_bean"}`; a filter that throws leaves the
  record in the log and is counted as `reason="filter_error"`.
- `enable-record-filtering` — toggles that filter from configuration, per topic like
  the masking switches. The predicate stays in code, but the set of topics it applies
  to no longer does: `false` globally with `true` on a `specific-topic-settings` block
  points one bean at exactly the topics that want it, without a rebuild. Defaults to
  `true`, so a registered bean keeps applying everywhere unless told otherwise, and
  with no bean present the toggle is a no-op. The per-topic settings lookup now runs
  just before the filter rather than just after, so a dropped record additionally pays
  for one topic-pattern match; masking and serialisation still sit behind the filter.

### Changed
- `KafkaLogSink` gained a `KafkaRecordLogFilter` constructor parameter, sixth of seven,
  ahead of `WiretapMetrics`. Code constructing the sink directly needs the extra
  argument; pass `null` for "no filter". Unlike the `collectHeaders` change in 2.0.0
  this is not reachable through an injected bean — the sink is built by the starter's
  own configuration and its `@Bean` is not `@ConditionalOnMissingBean`, so replacing it
  was never a supported extension point. YAML configuration is unaffected.

## [2.0.0] - 2026-09-02

Major release: the scoped setting overrides finally behave as documented, and
making that possible changed the types of several public settings fields. YAML
configuration is unaffected — see *Changed* for the programmatic API.

### Fixed
- `specific-http-info-settings[].enable-url-masking` and
  `specific-http-info-settings[].enable-request-params-masking` now actually apply.
  Both flags were documented as per-URL overrides but were dead: the per-URL merge
  never copied them, so the resolved settings always fell back to the constructor
  default, and every call site read the flag from the common settings block rather
  than from the settings resolved for the request URL. An override could neither
  turn masking off for a URL where it is globally on, nor turn it on where it is
  globally off. Fixed across all logging points — inbound access logs (including
  the `message` field, which previously snapshotted the flag once at startup and
  could disagree with `http_info.request_url`), RestTemplate, RestClient, Feign,
  WebClient and WebServiceTemplate.

- Per-URL overrides now merge field by field instead of all-or-nothing. Previously an
  override was compared against the library defaults as a whole object, which broke both
  directions: a nested value that happened to equal the default was indistinguishable
  from "not configured" (so `enable-body-masking: false` could never turn masking off for
  a URL, and the documented "hide request bodies globally, show them on one endpoint"
  example did nothing), and configuring a single nested value made the override win
  outright, silently resetting its siblings to library defaults rather than keeping the
  global ones.
- `exclude-request-patterns` now matches inbound requests again. The access-log filter
  tested the patterns against the request line (`GET /actuator/health HTTP/1.1`) rather
  than the URI, so no path pattern could ever match — including the built-in
  `/actuator/.*` default. Every actuator call has been landing in the access log.
- Masking is no longer applied twice to the logged request URL. The outgoing interceptors
  masked the URL when building `http_info` and masked the already-masked value again when
  writing the log message; a non-idempotent handler produced a doubly-transformed URL.
  The same path passed `null` into the handler when `REQUEST_URL` visibility was off.
- A malformed `match-url-pattern` or `exclude-request-patterns` entry no longer breaks the
  HTTP call itself on `WebClient`. Settings resolution happens outside the try/catch that
  guards the other interceptors, so the exception propagated into the reactive chain.
  A per-URL block without a pattern is now skipped instead of throwing on every request.
- A per-URL block that does not configure `request-headers` / `response-headers` now
  inherits the common lists instead of falling back to the library defaults
  (`Content-Type`, `X-Forwarded-For`).
- Kafka per-topic overrides in `specific-topic-settings[]` got the same treatment as the
  HTTP ones. The three masking toggles were merged with a logical AND, which could only
  ever turn masking **off**: with `enable-value-masking: false` globally, no topic
  override could switch it back on. `message-body-settings` and `visibility-settings`
  merged all-or-nothing against the library defaults, so an explicitly configured value
  equal to the default was indistinguishable from "not configured", and setting one nested
  value reset its siblings. Merging is now per field, and an unset value inherits the
  common one.
- `specific-topic-settings[].headers` now actually applies, including the `*` wildcard
  promised for per-topic overrides in `0.1.5`. The header list was read from the common
  settings inside `KafkaLogSink.collectHeaders`, which the interceptors call before the
  per-topic lookup happens, so the override was silently dead.
- A Kafka per-topic block without a `match-topic-pattern` no longer throws on every
  record. The `NullPointerException` was swallowed by the sink's catch-all, which dropped
  the whole log line and emitted an error per message instead.

### Added
- `enable-request-params-masking` is now declared in the configuration metadata
  (globally and per-URL), so IDEs stop flagging it as an unknown property.
- Configuration metadata for `wiretap.rest-client-interceptor` and
  `wiretap.web-client-interceptor`, which previously declared only `.enabled` despite
  supporting the full settings surface, plus the missing `message-body-settings` and
  `visibility-settings` container entries on the Kafka blocks.

### Removed
- Configuration metadata entries that described nothing: `rest-controllers.extra-info-field-name`
  (no such property), `specific-http-info-settings.additional-request-headers` (not part of
  the per-URL type), nested `specific-http-info-settings.specific-http-info-settings`
  (never applied), and `REQUEST_PARAMS` visibility for the WebServiceTemplate interceptor
  (SOAP does not log query parameters). Declared defaults for `max-body-length`,
  `enable-body-truncating` and header visibility now match the code. On the Kafka side,
  `specific-topic-settings[]` entries no longer declare defaults — an unset value there
  inherits the common one — and the `DURATION` / `STATUS` visibility keys are no longer
  described as unused on the consumer side, which the tests contradict.

### Changed
- **Breaking (programmatic API only):** `enableUrlMasking` and
  `enableRequestParamsMasking` on `HttpInfoLogMessageSettings` changed from
  `boolean` to `Boolean`, so an unset override can be told apart from an explicit
  `true` and inherit the common value. The generated accessors are now
  `getEnableUrlMasking()` / `getEnableRequestParamsMasking()`; use
  `isUrlMaskingEnabled()` / `isRequestParamsMaskingEnabled()` to read the effective
  value. YAML keys and the `true` default are unchanged, so configuration files
  need no edits.
- **Breaking (programmatic API only):** the four `HttpBodySettings` fields changed from
  `boolean`/`int` to `Boolean`/`Integer` for the same reason. Read the effective values
  through `isBodyMaskingEnabled()`, `isBodyTruncatingEnabled()`,
  `getEffectiveMaxFieldLength()` and `getEffectiveMaxBodyLength()` — the raw getters may
  return `null` when a setting was not configured. This affects custom `BodyParser`
  implementations and subclasses overriding the `DefaultBodyParser` hooks, since
  `HttpBodySettings` is passed to both. YAML keys and defaults are unchanged.
- **Breaking (programmatic API only):** the same change on the Kafka side. The three
  masking toggles on `KafkaInfoLogMessageSettings` and all three `MessageBodySettings`
  fields are now nullable; read them through `isValueMaskingEnabled()`,
  `isHeadersMaskingEnabled()`, `isTopicMaskingEnabled()`, `isValueTruncatingEnabled()` and
  `getEffectiveMaxValueLength()`. `KafkaLogSink.collectHeaders` now takes the topic as its
  first argument so it can honour per-topic overrides. YAML keys and defaults are
  unchanged.
- Kafka per-topic behaviour change worth checking before upgrading: a configuration with
  masking off globally and on for a topic family previously logged that family unmasked.
  It now masks, as written.

## [1.0.1] - 2026-06-16

### Fixed
- Multipart and binary request bodies are no longer buffered by the logback-access
  tee filter, which fixes broken file uploads. The stock `TeeFilter` eagerly reads
  the whole request stream into a buffer for every non-`form-urlencoded` request;
  for `multipart/form-data` that drained the stream before the controller could read
  `request.getParts()` / `@RequestPart`, so uploads failed (empty parts, HTTP 400).
  Wiretap now registers a content-type-aware tee filter that skips teeing for all
  `multipart/*` and binary / streaming types (octet-stream, pdf, images,
  event-stream), leaving the stream intact; `form-urlencoded`, JSON and other
  parseable bodies are teed and logged exactly as before. Response bodies for the
  skipped request types are no longer captured (their access logs keep method, URL,
  status, headers and timing).
- Correlation headers (`wiretap.headers.forward-to-mdc`) are now forwarded into
  MDC before any servlet filter runs, and MDC is reliably cleared after each
  request. Previously the forwarding happened in a Spring MVC `HandlerInterceptor`,
  which runs inside the `DispatcherServlet` — after the whole servlet filter chain
  (including Spring Security and logging filters), so those filters never saw the
  correlation values. The companion MDC-clearing filter was annotated only with
  `@WebFilter` and was never registered (no `@ServletComponentScan`), so MDC was
  never cleared and forwarded values leaked across pooled request threads. Both
  are replaced by a single high-precedence servlet filter that populates MDC up
  front and clears it in a `finally` block.
- `wiretap.http.overhead` no longer over-reports on failed outgoing calls.
  When a `WebClient`, `RestTemplate`, `RestClient`, or Feign request failed
  (timeout, connection reset, read timeout), the downstream wait was recorded
  as `0`, so the whole time spent waiting on the remote system was attributed
  to wiretap overhead — a 5-second timeout looked like 5 seconds of logging
  overhead on the `outcome="exception"` series. The interceptors now subtract
  the real downstream duration on the exception path, matching the success
  path. Incoming/servlet behaviour is unchanged (it has no downstream call to
  subtract).

## [1.0.0] - 2026-06-01

First public release. It's an early `1.0.0` — the feature set is in place and the
public API follows semantic versioning from here, but expect rough edges; it is
developed in the open and tested on real projects, so please file what breaks.
The public API — configuration properties
under `wiretap.*`, the SPI interfaces (`WiretapAccessFieldProvider`,
`WiretapLogFieldProvider`, `HttpBodyMaskingHandler`, `HttpBodyFieldMaskingHandler`,
`HttpUrlMaskingHandler`, `HttpRequestParamsMaskingHandler`,
`MessageMaskingHandler`, `KafkaValueMaskingHandler`,
`KafkaHeaderMaskingHandler`, `KafkaTopicMaskingHandler`), and the four
published artifact coordinates — is now subject to semver
breaking-change rules.

### Added
- Micrometer metrics for wiretap's own processing pipeline. When a
  `MeterRegistry` bean is present in the Spring context (typically through
  `spring-boot-starter-actuator`), wiretap publishes per-direction overhead
  timers, request / skip counters and body-size distributions for every
  client it instruments (`servlet` / `webclient` / `restclient` /
  `resttemplate` / `feign` / `webservicetemplate`) and for Kafka producer /
  consumer paths. `wiretap.metrics.detailed-timings=true` adds phase-level
  timers (parse / mask / truncate / serialize) and a per-`HttpBodyMaskingHandler`
  invocation timer; `wiretap.metrics.histograms=true` enables percentile
  histograms (p50 / p95 / p99) on every timer. When wiretap is wrapping
  Logback appenders in `AsyncAppender`
  (`wiretap.async-logging.enabled=true`), three gauges expose the queue
  pressure: `wiretap.async.appender.queue.size`,
  `wiretap.async.appender.queue.capacity`,
  `wiretap.async.appender.queue.remaining`. Without a `MeterRegistry` or
  with `wiretap.metrics.enabled=false`, the library installs a no-op
  implementation and does not pull Micrometer onto the classpath.
- Phase-level timers for the Kafka body pipeline, mirroring the HTTP
  side. With `wiretap.metrics.detailed-timings=true` `KafkaLogSink`
  emits `wiretap.body.phase` for `parse` / `mask` / `truncate` and
  `wiretap.body.masker.invocation` for `KafkaValueMaskingHandler`
  calls, tagged `client=kafka` and `direction=producer`/`consumer`.
  The flag default stays off, so the hot path is unchanged for users
  who do not opt in.
- One Maven Central artifact per tested Spring Boot patch version,
  built from the same revision through per-subproject Copy-with-filter
  source rewriting:
  - `io.github.alexander-kuznetsov:wiretap-spring-boot-3.2.7-starter`
    (Logback 1.4 / logback-access 1.x / Jackson 2).
  - `io.github.alexander-kuznetsov:wiretap-spring-boot-3.4.5-starter`
    (Logback 1.5 / logback-access common-API / Jackson 2).
  - `io.github.alexander-kuznetsov:wiretap-spring-boot-3.5.14-starter`
    (Logback 1.5 / logback-access common-API / Jackson 2).
  - `io.github.alexander-kuznetsov:wiretap-spring-boot-4.0.6-starter`
    (Logback 1.5 / logback-access common-API / Jackson 3 /
    `tools.jackson.*`). Combines a slightly broader Copy-with-filter
    (Spring Boot 4 client-module relocations) with hand-written
    Jackson 3 overlays for every wiretap class that touches Jackson:
    `JsonNode.fields()`/`elements()` walks were rewritten to the
    collection-returning Jackson 3 equivalents, `new TextNode(...)`
    replaced by `StringNode.valueOf(...)`, immutable `ObjectMapper`
    constructed via `JsonMapper.builder().build()`,
    `JsonGenerator.writeXxxField` renamed to `writeXxxProperty`, and
    `JsonProcessingException` (now a `RuntimeException`) replaced by
    `JacksonException`.
  Pick the coordinate that matches your Spring Boot version.

### Changed
- Inbound request/response body capture is now on by default. Wiretap
  contributes `logback.access.tee-filter.enabled=true` as an overridable
  default, so logback-access's `TeeFilter` runs and `request_body` /
  `response_body` are populated without extra configuration. The filter buffers
  each body in memory before the access encoder runs — set
  `logback.access.tee-filter.enabled=false` to opt out. Outbound bodies
  (client interceptors) are unaffected.
- Under `wiretap.pretty-print=true` the `stack_trace` field is now
  rendered as a JSON array of strings (one element per line) instead
  of a single embedded string. The change makes long stack traces
  readable in the terminal — `PrettyPrintingJsonGeneratorDecorator`
  cannot wrap inside a string literal, so without splitting the trace
  it produced one long horizontal line. The same depth / length
  limits and `ShortenedThrowableConverter` are reused in both modes;
  with `pretty-print=false` (default) the field remains a single
  string, so log shippers and Elasticsearch / OpenSearch mappings
  keep working unchanged. Do not enable pretty-print in environments
  that index into the same Elasticsearch field as other instances.
- Kafka `kafka_info.key` and `kafka_info.value` payloads that parse as JSON
  objects or arrays are now pretty-printed (multi-line with `\n`) inside the
  log string, matching how HTTP request / response bodies are rendered. Log
  aggregators (Kibana / Splunk / Grafana Loki) display them as a formatted
  block instead of a collapsed one-liner. Plain strings, scalars and
  malformed JSON are emitted verbatim. The field remains a string in the
  emitted JSON — payloads are not embedded as nested objects to avoid
  index-type collisions in Elasticsearch / OpenSearch. The pretty-print step
  runs after the optional `KafkaValueMaskingHandler` and before
  `enable-value-truncating`, so existing single-line regex masks keep
  working and the truncation limit applies to the final pretty text.
- **Breaking — body-masking SPI rename.** The two custom body-masking
  extension points were renamed so they read as a matched pair, with no
  change to their method contracts. The structural, per-URL masker
  `HttpBodyMasker` (added in 0.1.3; `boolean appliesTo(String)` +
  `JsonNode mask(JsonNode)`) is now `HttpBodyMaskingHandler`. The original
  recursive per-field masker `HttpBodyMaskingHandler`
  (`String maskBodyField(String)`) is now `HttpBodyFieldMaskingHandler`.
  Consumers who implemented either SPI on 0.1.x must rename the interface
  they implement (structural → `HttpBodyMaskingHandler`, per-field →
  `HttpBodyFieldMaskingHandler`); the two still compose — structural runs
  first, then per-field.
- Wiretap no longer auto-suppresses the `brave.Tracer` logger. Earlier versions
  shipped a hardcoded Logback `EvaluatorFilter` that dropped its span-dump lines;
  it was an environment-specific default (and unreliable on the Jackson 3 /
  Logback 1.5 starter, where the Janino evaluator fails open), so it has been
  removed. Quiet any noisy logger the standard Spring Boot way instead —
  `logging.level.brave.Tracer: OFF`.

### Removed
- `io.wiretap.configuration.LoggerConfiguration`,
  `io.wiretap.configuration.WiretapLogFieldProvidersInit`,
  `io.wiretap.configuration.WiretapFieldProvidersInit` — empty
  `@Deprecated` source-level stubs kept since the 0.1.0 internal
  refactor. Replacements (`WiretapAutoConfiguration`,
  `WiretapAppLogConfiguration`, `WiretapAccessLogConfiguration`) have
  been in place and wired by auto-configuration for every release
  since 0.1.1; consumers that did not subclass or `@Import` the stubs
  are unaffected.

### Deprecated
- `io.github.alexander-kuznetsov:wiretap` (without
  `-spring-boot-X.Y.Z-starter` suffix). Releases `0.1.4`–`0.1.6` stay
  on Maven Central; no further releases under this artifactId. Migrate
  to `wiretap-spring-boot-3.2.7-starter` for equivalent contents and
  future patches.

## [0.1.6] - 2026-05-19

### Changed
- Kafka logs are emitted once per message, after the operation completes.
  Producer-side: a Spring Kafka `ProducerListener` writes one
  `kafka_info OUTGOING` line in `onSuccess` / `onError` with
  `status=SUCCESS` / `ERROR`, broker-assigned `partition` / `offset`,
  and on failure `error_class` / `error_message` (at `WARN`).
  Consumer-side: a Spring Kafka `RecordInterceptor` (registered via
  `ContainerCustomizer`) logs in `success(...)` / `failure(...)` with
  `duration` (listener invocation time in ms) and `status`, instead of
  the old Kafka-native `ConsumerInterceptor.onConsume(...)`. Both
  hooks live inside the Spring Kafka listener / template observation
  context, so `kafka_info` picks up `traceId` / `spanId` from MDC
  automatically — even when the upstream producer did not propagate a
  trace. If listener observation is disabled, wiretap falls back to
  extracting `b3` / `traceparent` from record headers as before.
  Auto-configured factories from Spring Boot pick up the new
  customizers automatically; manually constructed factories /
  templates need one explicit line — see README sections
  "Custom KafkaTemplate" and "Custom listener container factories".
- Producer-side `client_id` is no longer logged. `ProducerListener`
  does not expose it; Kafka publishes producer client-id through
  JMX / Micrometer metrics instead.

### Removed
- `io.wiretap.kafka.producer.WiretapProducerInterceptor` is gone, along
  with its `DefaultKafkaProducerFactoryCustomizer` registration via
  `interceptor.classes`. Replaced by
  `io.wiretap.kafka.producer.WiretapProducerListener` (Spring Kafka
  SPI).
- `io.wiretap.kafka.consumer.WiretapConsumerInterceptor` is gone, along
  with its `DefaultKafkaConsumerFactoryCustomizer` registration via
  `interceptor.classes`. Replaced by
  `io.wiretap.kafka.consumer.WiretapRecordInterceptor` (Spring Kafka
  SPI).
- Applications that hardcoded either class in
  `spring.kafka.{producer,consumer}.properties.interceptor.classes`
  must drop those references — wiretap-auto-config now registers
  through the Spring Kafka container / template layer.

## [0.1.5] - 2026-05-19

### Added
- `*` wildcard in header allow-lists. A single `*` element in
  `request-headers`, `response-headers` (every HTTP source, including
  SOAP `MimeHeaders` and the underlying transport headers), or the
  Kafka `headers` list makes wiretap log every header from the source.
  Works in both common settings and per-URL / per-topic overrides;
  other elements in the list are ignored when `*` is present (match
  is case-insensitive). Does **not** apply to
  `wiretap.headers.forward-to-mdc` — that list stays explicit by
  design. No built-in blacklist for sensitive headers — register a
  `KafkaHeaderMaskingHandler` or configure `wiretap.message-masking`
  if you turn `*` on.

## [0.1.4] - 2026-05-18

### Fixed
- IDE auto-completion for `wiretap.*` properties. The bundled
  `additional-spring-configuration-metadata.json` (≈190 hand-written
  entries — enum-keyed visibility flags, Kafka and `specific-topic-settings`,
  `wiretap.message-masking`, `wiretap.web-client-interceptor.enabled`,
  etc.) was not merged into the final metadata because Gradle's
  `processResources` runs after `compileJava` by default, so the
  Spring Boot configuration-processor saw an empty `build/resources/main`
  during annotation processing. Forcing
  `compileJava.dependsOn(processResources)` restores the merge —
  the published jar now ships the complete property metadata.

## [0.1.3] - 2026-05-18

### Added
- New `HttpBodyMasker` SPI for structural body masking. Implement
  `boolean appliesTo(String url)` and `JsonNode mask(JsonNode body)` —
  wiretap walks all registered `HttpBodyMasker` beans, applies the
  first one whose `appliesTo` returns `true`, and then still runs the
  recursive `HttpBodyMaskingHandler` (if any) over the result. Lets
  consumers mask specific fields on specific endpoints (e.g.
  `remaining_auth` only on `/api/cardlimits/*`) without subclassing
  `DefaultBodyParser`.

## [0.1.2] - 2026-05-18

### Fixed
- `WiretapAccessFieldProvider` and `WiretapLogFieldProvider` are now
  implementable in consumer projects. The SPI methods take
  `ch.qos.logback.access.spi.IAccessEvent` and
  `ch.qos.logback.classic.spi.ILoggingEvent` as arguments, so the
  underlying jars must be on the compile classpath of consumers; both
  dependencies were promoted from `implementation` to `api` in the
  wiretap build.

## [0.1.1] - 2026-05-18

### Build
- Target Sonatype Central Portal explicitly in
  `mavenPublishing.publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)`.
  The previous attempt at 0.1.0 fell back to the legacy OSSRH endpoint
  (`s01.oss.sonatype.org`) and returned HTTP 402 — the v0.1.0 tag
  exists but no artifact was uploaded; 0.1.1 is the first published
  release.

## [0.1.0] - 2026-05-18 — withdrawn

### Added
- Streaming-aware response logging for `WebClientLoggingFilter` — auto-detects
  `text/event-stream`, `application/x-ndjson`, `application/octet-stream`,
  `multipart/x-mixed-replace`, and gRPC content types and skips body buffering
  for them. The body Flux passes through untouched, so SSE clients no longer
  hang and large downloads no longer pin payload-sized memory.
- Visibility-aware body capture in `WebClientLoggingFilter` — when
  `REQUEST_BODY` or `RESPONSE_BODY` visibility is `false` for a URL, the
  corresponding body is no longer wrapped or drained. Saves memory and CPU.
- Pre-capture body size limit in `WebClientLoggingFilter` — the captured
  string for the log line is hard-capped at `http-body-settings.max-body-length`
  on the way through; bodies above the limit get a `...[truncated]` marker.
- `wiretap.async-logging.*` — optional flag that wraps the built-in
  `CONSOLE` / `FILE-ROLLING` appenders in a Logback `AsyncAppender`.
  Recommended for high-throughput WebClient workloads where synchronous
  appender writes on the reactor event-loop thread become a bottleneck.
  Properties: `enabled`, `queue-size`, `never-block`, `discarding-threshold`.
- `WebClientLoggingFilter` — outgoing `WebClient` calls are now logged automatically
  via `ExchangeFilterFunction`. The filter is registered through `WebClientCustomizer`
  on the auto-configured `WebClient.Builder`, so it covers any client built on top of
  it — including `graphql.kickstart.spring.webclient.boot.GraphQLWebClient`.
  Configuration prefix: `wiretap.web-client-interceptor.*`; disable with
  `wiretap.web-client-interceptor.enabled=false`. The feature activates only when
  `spring-webflux` is on the classpath (`@ConditionalOnClass`).
- `WiretapAccessLogFieldsProperties` — every JSON field name in access logs and
  outgoing HTTP logs is configurable via `wiretap.access-log.fields.*`. Defaults match
  the original Wiretap schema (no breaking change for the defaults themselves).
- `WiretapAccessFieldProvider` SPI — implement as a Spring bean to inject
  arbitrary fields into the JSON HTTP access log.
- `WiretapLogFieldProvider` SPI — implement as a Spring bean to inject arbitrary
  fields into application JSON logs (`log.info(...)`, `log.error(...)`, etc.).
- `WiretapAppLogProperties` — application log field names and per-field visibility
  toggles via `wiretap.app-log.fields.*` and `wiretap.app-log.visibility-settings.*`.
- `WiretapHeadersProperties` — configurable inbound header names for MDC forwarding.
- `logger` field in application logs — emits `event.getLoggerName()` without a
  stack-trace capture; replaces the old `class` / `method` / `line` / `file` fields.
- Caller-data fields (`caller_class`, `caller_method`, `caller_line`, `caller_file`)
  available but disabled by default; enable via `wiretap.app-log.visibility-settings.*`.
- Initial test suite (JUnit 5 + AssertJ + Mockito + WireMock).

### Changed
- Internal configuration refactored: `LoggerConfiguration` (with `@ComponentScan`)
  replaced by a tree of focused `@Configuration` classes (`WiretapAutoConfiguration`
  + per-client sub-configurations imported via `@Import`). All settings classes use
  `@ConfigurationProperties` only (no `@Component`). YAML properties are unchanged.
- **Breaking:** `wiretap.fields.*` renamed to `wiretap.access-log.fields.*`.
  Update your `application.yml` if you customised any access-log field names.
- **Breaking:** application log fields are now written by `WiretapStandardLogFieldsProvider`
  instead of the XML `<pattern>` block. The `logger` field replaces `class`
  (no stack trace required); use `wiretap.app-log.fields.logger-name=class` to
  keep the old JSON key.
- `wiretap.headers.session-key-header` and `wiretap.fields.session-key` removed.
  Re-add via `WiretapAccessFieldProvider` — see the README for an example.
- All packages renamed to `io.wiretap.*` and translated to English.
- All Tinkoff/ATM/Sage-specific defaults (`atm_id`, `eKassir-PointID`,
  `tcs-session-key`, `cluster_name`, `fd_external_id`) removed from the core.
  Re-add via `WiretapAccessFieldProvider` and `WiretapHeadersProperties` if
  your environment needs them.
- Public static mutable fields on the `Lazy*` providers replaced with
  `private static volatile` plus explicit setters.
- `LazyIncomingRequestLogFilter` no longer throws when invoked before the
  Spring context is up — returns `FilterReply.NEUTRAL` instead.
- `logback-access.properties.xml` renamed to `logback-access-properties.xml`
  (matches the include reference in the appender XMLs).

### Removed
- `lb_trace_id`, `parent_id`, `session_key`, `db_query_info` fields from
  application logs — these were infrastructure- or domain-specific and
  `session_key` was broken (MDC key mismatch). Re-add any of them via
  `WiretapLogFieldProvider` if needed.
- Expensive caller fields (`class`, `method`, `line`, `file`) disabled by default
  in application logs; replace with `logger` (no stack trace). Re-enable via
  `wiretap.app-log.visibility-settings.*`.

### Build
- Gradle 9.0, Lombok 1.18.42, Spring Dependency Management 1.1.7.
- Java 17 source/target; the build is tested up to Java 25.
