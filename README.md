# Wiretap

> Structured JSON logging for Spring Boot applications, with HTTP request/response
> capture across servlet, RestTemplate, RestClient, FeignClient, and WebServiceTemplate.

**Status:** `0.1.0-SNAPSHOT` — work in progress, public API not yet stable. Do not use in production.

## What is this

Wiretap is a Spring Boot starter that provides:

- One-line setup for structured JSON logging via logback + logback-access
- Out-of-the-box capture of inbound and outbound HTTP traffic in a unified JSON shape
- Configurable per-endpoint visibility (which fields end up in logs, which are masked)
- Built-in masking utilities for common sensitive data (PAN, phone numbers, etc.)
- Helpers for MDC propagation across async/parallel execution

## Status

This project is being open-sourced from a previously internal logging library.
Refactoring is in progress to:

- Split into a clean multi-module layout
- Replace static / `ThreadLocal`-based state with proper SPI
- Make the JSON output format configurable (presets: ECS, Datadog, Splunk, generic, custom)
- Add a comprehensive test suite
- Translate documentation to English

A more thorough README will be published with the first stable release.

## License

Apache License 2.0 — see [LICENSE](./LICENSE).
