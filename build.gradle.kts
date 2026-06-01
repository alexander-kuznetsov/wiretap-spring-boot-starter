// Aggregator root for the per-Spring-Boot-version wiretap subprojects.
// The library is published under three coordinates that share one source tree:
//
//   io.github.alexander-kuznetsov:wiretap-spring-boot-3.2.7-starter  (Logback 1.4)
//   io.github.alexander-kuznetsov:wiretap-spring-boot-3.4.5-starter  (Logback 1.5)
//   io.github.alexander-kuznetsov:wiretap-spring-boot-3.5.14-starter (Logback 1.5)
//
// See `src/main` for canonical sources, `src/test` for canonical unit tests —
// each subproject re-targets these directories (3.2.7 directly, 3.4+ via a
// Copy-with-filter that rewrites the logback-access SPI package).
//
// The root project intentionally has no `java` plugin and no publication of
// its own; releases come from the three subprojects via the vanniktech
// `publishAndReleaseToMavenCentral` aggregator task.

group = "io.github.alexander-kuznetsov"
version = "1.0.0"
