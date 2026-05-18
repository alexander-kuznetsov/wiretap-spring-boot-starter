# Compatibility

[EN] | [RU](#совместимость-ru)

Wiretap is published as a **single artifact** that compiles against the
oldest supported Spring Boot version (the *baseline*) and is tested
against every supported minor on every supported Java toolchain.

The build accepts two Gradle properties to switch the matrix cell:

```bash
./gradlew test -PspringBootVersion=3.5.14 -PjavaToolchain=21
```

## Supported matrix

| Wiretap | Spring Boot | Java | Status |
|---------|-------------|------|--------|
| 0.1.x   | 3.2.7 (baseline)  | 17, 21      | extended support — kept working for legacy consumers |
| 0.1.x   | 3.4.5             | 17, 21, 25  | actively tested |
| 0.1.x   | 3.5.14            | 17, 21, 25  | actively tested, last 3.x minor |

`3.3.x` is **not** in the matrix — there were no relevant API breaks
between 3.2 and 3.4/3.5 from wiretap's point of view, and 3.3 is out of
OSS support. Patch versions of the listed minors should work — the matrix
pins a specific patch only to keep CI deterministic; adopt a newer patch
and run `./scripts/test-compatibility.sh` to verify locally.

## Spring Boot 4.x

Spring Boot 4.0 introduced enough breaking changes
(`spring-boot-starter-aop` was dropped from the BOM, the
`org.springframework.boot.autoconfigure.kafka` package was relocated,
`logback-access-spring-boot-starter` has no logback 2.x line yet) that
single-artifact coexistence with the Boot 3.x line is not realistic.
Boot 4 support is planned as **wiretap 2.x** — a separate major.

## How to verify locally

```bash
./scripts/test-compatibility.sh                       # full matrix
SPRING_BOOTS="3.5.14" JAVAS="17 21" ./scripts/test-compatibility.sh   # narrow run
```

CI runs the same command per matrix cell — see
`.github/workflows/compatibility.yml`.

## How to verify the dependency graph

After bumping a Spring Boot version in the matrix:

```bash
./gradlew dependencies -PspringBootVersion=3.5.14 \
    --configuration compileClasspath | less
```

Spring Boot's BOM is the single source of versions for `logback-classic`,
`spring-kafka`, `jackson-*`, `tomcat-embed-core`, etc.
`logback-access-spring-boot-starter` is pinned independently via
`logbackAccessSpringVersion` (default chosen by `defaultLogbackAccessFor()`
in `build.gradle.kts`) — override with `-PlogbackAccessSpringVersion=...`
when needed.

`feign-core` is pinned directly (`feignCoreVersion`, default `13.5`) —
wiretap no longer imports the Spring Cloud BOM. This decouples the build
from the Spring Cloud release train and removes one variable from the
compatibility matrix.

## Versioning policy

- **0.1.x** — pre-release. Single artifact, Boot 3.2.x – 3.5.x.
- **1.0.0** — first stable. Same coverage. Released via JReleaser to
  Maven Central.
- **2.0.0** — Boot 4.0+. Separate major because of Boot 4's breaking
  changes. The `0.x` / `1.x` lines remain on a `spring-boot-3.x` branch
  for legacy patches.

## Updating the matrix

1. Edit the matrix in three places:
   - `.github/workflows/compatibility.yml`
   - `scripts/test-compatibility.sh`
   - this file
2. Run `./scripts/test-compatibility.sh` locally before pushing — if a
   matrix cell breaks, fix the root cause or document the exception.
3. Bump the `defaultLogbackAccessFor()` mapping in `build.gradle.kts`
   if you cross a Spring Boot major.

---

<a id="совместимость-ru"></a>

# Совместимость (RU)

Wiretap публикуется как **один артефакт**, скомпилированный против
самой старой поддерживаемой версии Spring Boot (*baseline*) и
протестированный на каждой поддерживаемой версии × Java toolchain.

Сборка принимает два Gradle-свойства:

```bash
./gradlew test -PspringBootVersion=3.5.14 -PjavaToolchain=21
```

## Поддерживаемая матрица

| Wiretap | Spring Boot | Java | Статус |
|---------|-------------|------|--------|
| 0.1.x   | 3.2.7 (baseline)  | 17, 21      | extended support — для legacy-потребителей |
| 0.1.x   | 3.4.5             | 17, 21, 25  | активно тестируется |
| 0.1.x   | 3.5.14            | 17, 21, 25  | активно тестируется, последний 3.x минор |

`3.3.x` **не** включён в матрицу — между 3.2 и 3.4/3.5 не было
breaking-изменений, релевантных wiretap, а сам 3.3 вышел из OSS-поддержки.
Patch-версии перечисленных миноров должны работать; матрица фиксирует
конкретный patch только для детерминированности CI.

## Spring Boot 4.x

В Boot 4.0 достаточно ломающих изменений (нет
`spring-boot-starter-aop`, переехал пакет
`org.springframework.boot.autoconfigure.kafka`,
`logback-access-spring-boot-starter` пока не имеет logback 2.x ветки),
чтобы single-artifact сосуществование с веткой Boot 3.x было нереалистично.
Поддержка Boot 4 — отдельным major'ом **wiretap 2.x**.

## Локальная проверка

```bash
./scripts/test-compatibility.sh                       # вся матрица
SPRING_BOOTS="3.5.14" JAVAS="17 21" ./scripts/test-compatibility.sh
```

CI запускает тот же скрипт-аналог в каждой ячейке матрицы — см.
`.github/workflows/compatibility.yml`.

## Версионная политика

- **0.1.x** — pre-release. Один артефакт, Boot 3.2.x – 3.5.x.
- **1.0.0** — первый стабильный релиз. Тот же охват. Публикуется через
  JReleaser в Maven Central.
- **2.0.0** — Boot 4.0+. Отдельный major из-за breaking-изменений в Boot 4.
  Линейки `0.x` / `1.x` остаются на ветке `spring-boot-3.x` для legacy-патчей.
