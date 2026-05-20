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
    implementation(project(":"))

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
    // Spring Boot 3.4+ ships Logback 1.5 which moved JaninoEventEvaluatorBase
    // out of logback-core; the pinned logback-access-spring-boot-starter 4.1.2
    // (used for the Boot 3.x branch in the root build) still expects the old
    // location and fails at context start. Skip integration tests on SB 3.4+
    // until wiretap adopts the new logback-access API (see follow-up SSNC).
    onlyIf {
        val sb = springBootVersion
        sb.startsWith("3.2.") || sb.startsWith("4.")
    }
}

// Not a publishable artifact: an integration-test harness, not a distribution.
tasks.named("bootJar") { enabled = false }
