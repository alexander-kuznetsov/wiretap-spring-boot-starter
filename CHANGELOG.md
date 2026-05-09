# Changelog

All notable changes are recorded here.
This project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html);
versions before `1.0.0` are pre-release and the public API may change between minors.

## [Unreleased]

### Added
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
