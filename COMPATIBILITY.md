# Compatibility

[EN] | [RU](#совместимость-ru)

Wiretap is published as **one artifact per tested Spring Boot patch
version**. The Logback API broke its `IAccessEvent` package between 1.4
and 1.5 (and `logback-access-spring-boot-starter` followed in 4.2.0),
and Spring Boot 4 ships Jackson 3 (`tools.jackson.*`) plus per-client
module relocations — a single jar cannot satisfy all the active
branches. Naming the coordinate after the exact Spring Boot version it
was built against makes the choice unambiguous:
`wiretap-spring-boot-4.0.6-starter` means "wiretap built and tested
against Spring Boot 4.0.6".

| Maven coordinates | Spring Boot target | Logback | Jackson |
|---|---|---|---|
| `io.github.alexander-kuznetsov:wiretap-spring-boot-3.2.7-starter` | 3.2.7 | 1.4 | 2 |
| `io.github.alexander-kuznetsov:wiretap-spring-boot-3.4.5-starter` | 3.4.5 | 1.5 | 2 |
| `io.github.alexander-kuznetsov:wiretap-spring-boot-3.5.14-starter` | 3.5.14 | 1.5 | 2 |
| `io.github.alexander-kuznetsov:wiretap-spring-boot-4.0.6-starter` | 4.0.6 | 1.5 | 3 |

All four come from the same Git revision. The 3.4.5 / 3.5.14
subprojects apply a Copy-with-filter task that rewrites
`ch.qos.logback.access.spi.IAccessEvent` to
`ch.qos.logback.access.common.spi.IAccessEvent` in the canonical
sources. The 4.0.6 subproject combines a slightly broader Copy-with-filter
(Spring Boot 4 client-module relocations) with hand-written Jackson 3
overlays in its own `src/main/java/`, because the Jackson 2 → 3
migration touches APIs (`JsonNode.fields()`/`elements()` returning
collections instead of `Iterator`, immutable `ObjectMapper`,
`JsonGenerator.writeXxxField` → `writeXxxProperty`) that a text rewrite
cannot patch cleanly. Pick the coordinate that matches your Spring Boot
patch version. If the matrix advances to a new patch (e.g. 3.5.14 →
3.5.15), a new coordinate is published; the previous one stays
available on Maven Central as the last build for that exact Spring
Boot.

> **Deprecated:** `io.github.alexander-kuznetsov:wiretap` (without the
> `-spring-boot-X.Y.Z-starter` suffix) covers releases `0.1.4`–`0.1.6`
> and remains on Maven Central for existing consumers, but receives no
> further releases. Migrate to `wiretap-spring-boot-3.2.7-starter` —
> the artifact contents are equivalent.

The build accepts two Gradle properties to switch the matrix cell:

```bash
./gradlew test -PspringBootVersion=3.5.14 -PjavaToolchain=21
```

Without `-PspringBootVersion` all four SB-version subprojects build
together (used by the release workflow). With the property only the
matching subproject runs; the other three are SKIPPED.

## Supported matrix

| Artifact                                  | Spring Boot       | Java        | Status |
|-------------------------------------------|-------------------|-------------|--------|
| `wiretap-spring-boot-3.2.7-starter`       | 3.2.7 (baseline)  | 17, 21      | extended support — kept working for legacy consumers |
| `wiretap-spring-boot-3.4.5-starter`       | 3.4.5             | 17, 21, 25  | actively tested |
| `wiretap-spring-boot-3.5.14-starter`      | 3.5.14            | 17, 21, 25  | actively tested, last 3.x minor |
| `wiretap-spring-boot-4.0.6-starter`       | 4.0.6             | 17, 21, 25  | actively tested, first SB 4 / Jackson 3 line |

`3.3.x` is **not** in the matrix — there were no relevant API breaks
between 3.2 and 3.4/3.5 from wiretap's point of view, and 3.3 is out of
OSS support. Patch versions of the listed minors should work — the matrix
pins a specific patch only to keep CI deterministic; adopt a newer patch
and run `./scripts/test-compatibility.sh` to verify locally.

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

- **0.1.x** — pre-release. Four per-SB-version artifacts covering
  Boot 3.2.x, 3.4.x, 3.5.x and 4.0.x.
- **1.0.0** — first stable. Same coverage. Released via the
  `release-to-central` workflow.

## Updating the matrix

1. Edit the matrix in three places:
   - `.github/workflows/compatibility.yml`
   - `scripts/test-compatibility.sh`
   - this file
2. When the matrix gains a new Spring Boot patch version (or replaces
   an existing one with a newer patch), add or rename the matching
   `wiretap-spring-boot-X.Y.Z-starter` subproject — coordinates encode
   the exact target patch. The 4.0.x subproject additionally carries
   hand-written Jackson 3 overlays in `src/main/java/`; on a Boot 4 patch
   bump check whether the overlay files need to be re-synced with the
   root sources they shadow.
3. Run `./scripts/test-compatibility.sh` locally before pushing — if a
   matrix cell breaks, fix the root cause or document the exception.

---

<a id="совместимость-ru"></a>

# Совместимость (RU)

Wiretap публикуется в виде **одного артефакта на каждую тестируемую
patch-версию Spring Boot**. Logback API сломал пакет `IAccessEvent`
между 1.4 и 1.5 (а `logback-access-spring-boot-starter` сделал это в
4.2.0), а Spring Boot 4 принёс Jackson 3 (`tools.jackson.*`) и разнёс
клиентские Customizer'ы по отдельным модулям, поэтому один jar не
покрывает все активные ветки. Именование координаты по точной версии
Spring Boot снимает неоднозначность:
`wiretap-spring-boot-4.0.6-starter` значит «wiretap, собранный и
протестированный против Spring Boot 4.0.6».

| Maven-координаты | Spring Boot | Logback | Jackson |
|---|---|---|---|
| `io.github.alexander-kuznetsov:wiretap-spring-boot-3.2.7-starter` | 3.2.7 | 1.4 | 2 |
| `io.github.alexander-kuznetsov:wiretap-spring-boot-3.4.5-starter` | 3.4.5 | 1.5 | 2 |
| `io.github.alexander-kuznetsov:wiretap-spring-boot-3.5.14-starter` | 3.5.14 | 1.5 | 2 |
| `io.github.alexander-kuznetsov:wiretap-spring-boot-4.0.6-starter` | 4.0.6 | 1.5 | 3 |

Все четыре собираются из одной git-ревизии. Сабпроекты для 3.4.5 и
3.5.14 применяют Copy-with-filter task, переименовывающий
`ch.qos.logback.access.spi.IAccessEvent` в
`ch.qos.logback.access.common.spi.IAccessEvent` в копии canonical-исходников.
Сабпроект 4.0.6 совмещает чуть более широкий Copy-with-filter (перенос
клиентских Customizer'ов Spring Boot 4) с написанными руками
Jackson 3-overlay-копиями в собственном `src/main/java/` — миграция
Jackson 2 → 3 затрагивает API (`JsonNode.fields()`/`elements()` теперь
возвращают коллекции вместо `Iterator`, `ObjectMapper` стал immutable,
`JsonGenerator.writeXxxField` переименован в `writeXxxProperty`),
которые текстовый rewrite чисто не покрывает. Подключайте координату,
соответствующую вашей patch-версии Spring Boot. Когда матрица сдвигается
на новый patch (например, 3.5.14 → 3.5.15), публикуется новая
координата; предыдущая остаётся на Maven Central как последняя сборка
под точно ту версию.

> **Deprecated:** `io.github.alexander-kuznetsov:wiretap` (без суффикса
> `-spring-boot-X.Y.Z-starter`) — это релизы `0.1.4`–`0.1.6`, остаются
> на Maven Central для существующих потребителей, но новых релизов под
> этим artifactId не выпускается. Мигрируйте на
> `wiretap-spring-boot-3.2.7-starter` — содержимое артефакта то же.

Сборка принимает два Gradle-свойства:

```bash
./gradlew test -PspringBootVersion=3.5.14 -PjavaToolchain=21
```

Без `-PspringBootVersion` собираются все четыре сабпроекта одновременно
(этот режим использует release-workflow). С property — прогоняется
только соответствующий сабпроект, остальные три SKIPPED.

## Поддерживаемая матрица

| Артефакт                                   | Spring Boot       | Java        | Статус |
|--------------------------------------------|-------------------|-------------|--------|
| `wiretap-spring-boot-3.2.7-starter`        | 3.2.7 (baseline)  | 17, 21      | extended support — для legacy-потребителей |
| `wiretap-spring-boot-3.4.5-starter`        | 3.4.5             | 17, 21, 25  | активно тестируется |
| `wiretap-spring-boot-3.5.14-starter`       | 3.5.14            | 17, 21, 25  | активно тестируется, последний 3.x минор |
| `wiretap-spring-boot-4.0.6-starter`        | 4.0.6             | 17, 21, 25  | активно тестируется, первая SB 4 / Jackson 3 линия |

`3.3.x` **не** включён в матрицу — между 3.2 и 3.4/3.5 не было
breaking-изменений, релевантных wiretap, а сам 3.3 вышел из OSS-поддержки.
Patch-версии перечисленных миноров должны работать; матрица фиксирует
конкретный patch только для детерминированности CI.

## Локальная проверка

```bash
./scripts/test-compatibility.sh                       # вся матрица
SPRING_BOOTS="3.5.14" JAVAS="17 21" ./scripts/test-compatibility.sh
```

CI запускает тот же скрипт-аналог в каждой ячейке матрицы — см.
`.github/workflows/compatibility.yml`.

## Версионная политика

- **0.1.x** — pre-release. Четыре per-SB-version артефакта,
  покрывающие Boot 3.2.x, 3.4.x, 3.5.x и 4.0.x.
- **1.0.0** — первый стабильный релиз. Тот же охват. Публикуется через
  workflow `release-to-central`.
