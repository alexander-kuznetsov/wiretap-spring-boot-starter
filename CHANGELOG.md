# Changelog

All notable changes are recorded here.
This project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html);
versions before `1.0.0` are pre-release and the public API may change between minors.

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
