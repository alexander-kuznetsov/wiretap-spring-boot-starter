// Integration tests subproject. Verifies wiretap behavior from a real
// consumer's perspective: boots a Spring context with wiretap on the
// classpath, drives HTTP and Kafka traffic, and asserts the JSON shape
// emitted to stdout.
//
// Versions follow the same matrix knobs as the root build:
//   -PspringBootVersion (default 3.2.7) — pinned via settings.gradle.kts
//   -PjavaToolchain     (default 17)

plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.github.alexander-kuznetsov"
version = rootProject.version

val springBootVersion = providers.gradleProperty("springBootVersion").getOrElse("3.2.7")
val javaToolchainVersion = providers.gradleProperty("javaToolchain").getOrElse("17").toInt()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaToolchainVersion)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
    }
}

dependencies {
    // Pick the wiretap subproject whose coordinates match the chosen
    // matrix cell. springBootVersion is expected to exactly equal one of
    // the published artifact targets; an unknown value fails fast with
    // "project not found".
    implementation(project(":wiretap-spring-boot-${springBootVersion}-starter"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.kafka:spring-kafka")
    // Logback 1.5+ (Spring Boot 3.4+) requires janino at runtime for the <if>
    // tags inside wiretap's bundled logback-access XML. Wiretap depends on
    // janino as `implementation`, which means consumers don't get it
    // transitively — re-declare it here.
    implementation("org.codehaus.janino:janino")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.awaitility:awaitility:4.2.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    if (javaToolchainVersion >= 25) {
        systemProperty("net.bytebuddy.experimental", "true")
    }
}

// On SB 3.3+ rewrite the logback-access SPI import to match the common-API
// build of wiretap (the same rewrite the wiretap-spring-boot-3.4.5/3.5.14
// subprojects apply to root sources).
if (!springBootVersion.startsWith("3.2.")) {
    val rewriteMainSources = tasks.register<Copy>("rewriteMainSources") {
        from("src/main/java")
        into(layout.buildDirectory.dir("generated/sources/logback-access-common/java/main"))
        filter { line ->
            line.replace(
                "ch.qos.logback.access.spi.IAccessEvent",
                "ch.qos.logback.access.common.spi.IAccessEvent"
            )
        }
    }
    sourceSets.main {
        java.setSrcDirs(listOf(layout.buildDirectory.dir("generated/sources/logback-access-common/java/main")))
    }
    tasks.named("compileJava") { dependsOn(rewriteMainSources) }
}

// Not a publishable artifact: an integration-test harness, not a distribution.
tasks.named("bootJar") { enabled = false }
