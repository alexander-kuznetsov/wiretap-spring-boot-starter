# Security Policy

## Reporting a vulnerability

If you discover a security vulnerability in Wiretap, please **do not**
open a public issue. Instead, email the maintainer with the details:

> alexander.kuznetsov [at] users.noreply.github.com

We will acknowledge receipt within 7 days and aim to publish a fix or
mitigation within 30 days, depending on severity. Reporters who follow
this disclosure process will be credited (with consent) in the
CHANGELOG entry that ships the fix.

## What to include

- A clear description of the issue and the impact you observed.
- Reproduction steps or a minimal proof-of-concept.
- Affected versions (`./gradlew dependencies` output helps).
- Any suggested fix or mitigation, if you have one.

## Supported versions

Only the **latest minor release line** receives security patches:

| Version | Supported          |
|---------|--------------------|
| 1.x     | :white_check_mark: |
| < 1.0   | :x:                |

Compatibility across Spring Boot / Java is documented in
[`COMPATIBILITY.md`](./COMPATIBILITY.md).

## Hardening defaults

Wiretap captures HTTP and Kafka payloads by design. Several knobs help
keep sensitive data out of the log stream:

- `wiretap.rest-controllers.http-body-settings.enable-body-masking` +
  a `HttpBodyFieldMaskingHandler` bean — per-field masking of bodies.
- `wiretap.rest-controllers.enable-request-params-masking` +
  a `HttpRequestParamsMaskingHandler` — per-param masking.
- `wiretap.rest-controllers.enable-url-masking` +
  a `HttpUrlMaskingHandler` — full-URL masking.
- `wiretap.kafka-*-interceptor.enable-value-masking` +
  a `KafkaValueMaskingHandler` — Kafka key/value masking.
- `wiretap.message-masking` + a `MessageMaskingHandler` — app-log
  message masking.
- Per-URL / per-topic overrides via
  `specific-http-info-settings` / `specific-topic-settings`.
- `visibility-settings.<FIELD>: false` — drop a field entirely.

Use these to keep secrets, tokens and personal data out of logs.
