# Changelog

All notable changes are recorded here.
This project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html);
versions before `1.0.0` are pre-release and the public API may change between minors.

## [Unreleased]

### Added
- `WiretapFieldNamesProperties` — every JSON field name in access logs and
  outgoing HTTP logs is configurable via `wiretap.fields.*`. Defaults match
  the original Wiretap schema (no breaking change).
- `WiretapAccessFieldProvider` SPI — implement as a Spring bean to inject
  arbitrary fields into the JSON access log.
- `WiretapHeadersProperties` — configurable inbound header names for MDC
  forwarding and the `session_key` lookup.
- Initial test suite (JUnit 5 + AssertJ + Mockito + WireMock).

### Changed
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

### Build
- Gradle 9.0, Lombok 1.18.42, Spring Dependency Management 1.1.7.
- Java 17 source/target; the build is tested up to Java 25.
