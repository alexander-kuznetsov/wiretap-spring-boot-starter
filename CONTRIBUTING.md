# Contributing to Wiretap

Thanks for your interest. Wiretap is in the early days of being open-sourced; the
public API is not yet stable, so contributions that change behaviour or signatures
are welcome but should come with a discussion in an issue first.

## Development setup

```bash
git clone git@github.com:alexander-kuznetsov/verbatim-spring-boot-starter.git
cd verbatim-spring-boot-starter
./gradlew build
```

Requires JDK 17 or later. The build is tested on JDK 17, 21, and 25.

## Running tests

```bash
./gradlew test
```

Tests live under `src/test/java/io/wiretap/`. They use JUnit 5 + AssertJ + Mockito;
integration tests for the outgoing interceptors use WireMock.

## Coding conventions

- Java 17 source/target. Lombok is allowed (already used throughout `HttpMessageInfo`,
  `RestLogMessageSettings`, etc.).
- All public types have a Javadoc comment. Internal types may skip it when the
  name is self-explanatory.
- Prefer constructor injection over field injection.
- For new `wiretap.*` properties, add a corresponding entry in
  `src/main/resources/META-INF/spring-configuration-metadata.json` so IDE
  auto-completion works.
- Don't reintroduce removed Tinkoff/ATM-specific fields into the core. Anything
  domain-specific belongs in a downstream `WiretapAccessFieldProvider` SPI bean.

## Pull requests

- Keep PRs focused. Prefer multiple small PRs over one large PR.
- Include tests for new behaviour or bug fixes.
- Update `CHANGELOG.md` under the `## [Unreleased]` section.

## Reporting bugs

Use [GitHub issues](https://github.com/alexander-kuznetsov/verbatim-spring-boot-starter/issues)
and include:
- Wiretap version
- Spring Boot version
- A minimal reproduction (failing test or `application.yml` snippet preferred)
