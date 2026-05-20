# Compatibility

[EN] | [RU](#совместимость-ru)

Wiretap is published as **one artifact per tested Spring Boot patch
version**. The Logback API broke its `IAccessEvent` package between 1.4
and 1.5 (and `logback-access-spring-boot-starter` followed in 4.2.0),
so a single jar cannot satisfy all the active 3.x branches. Naming the
coordinate after the exact Spring Boot version it was built against
makes the choice unambiguous: `wiretap-spring-boot-3.5.14-starter`
means "wiretap built and tested against Spring Boot 3.5.14".

| Maven coordinates | Spring Boot target | Logback |
|---|---|---|
| `io.github.alexander-kuznetsov:wiretap-spring-boot-3.2.7-starter` | 3.2.7 | 1.4 |
| `io.github.alexander-kuznetsov:wiretap-spring-boot-3.4.5-starter` | 3.4.5 | 1.5 |
| `io.github.alexander-kuznetsov:wiretap-spring-boot-3.5.14-starter` | 3.5.14 | 1.5 |

All three come from the same Git revision; the 3.4.5/3.5.14 subprojects
apply a Copy-with-filter task that rewrites
`ch.qos.logback.access.spi.IAccessEvent` to
`ch.qos.logback.access.common.spi.IAccessEvent` in the canonical
sources. Pick the coordinate that matches your Spring Boot patch
version. If the matrix advances to a new patch (e.g. 3.5.14 → 3.5.15),
a new coordinate is published; the previous one stays available on
Maven Central as the last build for that exact Spring Boot.

> **Deprecated:** `io.github.alexander-kuznetsov:wiretap` (without the
> `-spring-boot-X.Y.Z-starter` suffix) covers releases `0.1.4`–`0.1.6`
> and remains on Maven Central for existing consumers, but receives no
> further releases. Migrate to `wiretap-spring-boot-3.2.7-starter` —
> the artifact contents are equivalent.

The build accepts two Gradle properties to switch the matrix cell:

```bash
./gradlew test -PspringBootVersion=3.5.14 -PjavaToolchain=21
```

Without `-PspringBootVersion` all three SB-version subprojects build
together (used by the release workflow). With the property only the
matching subproject runs; the other two are SKIPPED.

## Supported matrix

| Artifact                                  | Spring Boot       | Java        | Status |
|-------------------------------------------|-------------------|-------------|--------|
| `wiretap-spring-boot-3.2.7-starter`       | 3.2.7 (baseline)  | 17, 21      | extended support — kept working for legacy consumers |
| `wiretap-spring-boot-3.4.5-starter`       | 3.4.5             | 17, 21, 25  | actively tested |
| `wiretap-spring-boot-3.5.14-starter`      | 3.5.14            | 17, 21, 25  | actively tested, last 3.x minor |

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

Wiretap публикуется в виде **одного артефакта на каждую тестируемую
patch-версию Spring Boot**. Logback API сломал пакет `IAccessEvent`
между 1.4 и 1.5 (а `logback-access-spring-boot-starter` сделал это в
4.2.0), поэтому один jar не покрывает все активные ветки 3.x.
Именование координаты по точной версии Spring Boot снимает
неоднозначность: `wiretap-spring-boot-3.5.14-starter` значит «wiretap,
собранный и протестированный против Spring Boot 3.5.14».

| Maven-координаты | Spring Boot | Logback |
|---|---|---|
| `io.github.alexander-kuznetsov:wiretap-spring-boot-3.2.7-starter` | 3.2.7 | 1.4 |
| `io.github.alexander-kuznetsov:wiretap-spring-boot-3.4.5-starter` | 3.4.5 | 1.5 |
| `io.github.alexander-kuznetsov:wiretap-spring-boot-3.5.14-starter` | 3.5.14 | 1.5 |

Все три собираются из одной git-ревизии; сабпроекты для 3.4.5/3.5.14
применяют Copy-with-filter task, переименовывающий
`ch.qos.logback.access.spi.IAccessEvent` в
`ch.qos.logback.access.common.spi.IAccessEvent` в копии canonical-исходников.
Подключайте координату, соответствующую вашей patch-версии Spring Boot.
Когда матрица сдвигается на новый patch (например, 3.5.14 → 3.5.15),
публикуется новая координата; предыдущая остаётся на Maven Central как
последняя сборка под точно ту версию.

> **Deprecated:** `io.github.alexander-kuznetsov:wiretap` (без суффикса
> `-spring-boot-X.Y.Z-starter`) — это релизы `0.1.4`–`0.1.6`, остаются
> на Maven Central для существующих потребителей, но новых релизов под
> этим artifactId не выпускается. Мигрируйте на
> `wiretap-spring-boot-3.2.7-starter` — содержимое артефакта то же.

Сборка принимает два Gradle-свойства:

```bash
./gradlew test -PspringBootVersion=3.5.14 -PjavaToolchain=21
```

Без `-PspringBootVersion` собираются все три сабпроекта одновременно
(этот режим использует release-workflow). С property — прогоняется
только соответствующий сабпроект, остальные два SKIPPED.

## Поддерживаемая матрица

| Артефакт                                   | Spring Boot       | Java        | Статус |
|--------------------------------------------|-------------------|-------------|--------|
| `wiretap-spring-boot-3.2.7-starter`        | 3.2.7 (baseline)  | 17, 21      | extended support — для legacy-потребителей |
| `wiretap-spring-boot-3.4.5-starter`        | 3.4.5             | 17, 21, 25  | активно тестируется |
| `wiretap-spring-boot-3.5.14-starter`       | 3.5.14            | 17, 21, 25  | активно тестируется, последний 3.x минор |

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
