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
    // Actuator brings in micrometer-core + autoconfigures a MeterRegistry, which
    // activates wiretap's metrics autoconfiguration end-to-end for these tests.
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // SB 4 moved KafkaAutoConfiguration out of spring-boot-autoconfigure into
    // the dedicated spring-boot-kafka module; spring-boot-starter-kafka bundles
    // both the autoconfig and spring-kafka itself. On older Spring Boot lines
    // we keep the direct spring-kafka dependency unchanged.
    if (springBootVersion.startsWith("4.")) {
        implementation("org.springframework.boot:spring-boot-starter-kafka")
    } else {
        implementation("org.springframework.kafka:spring-kafka")
    }
    // Logback 1.5+ (Spring Boot 3.4+) requires janino at runtime for the <if>
    // tags inside wiretap's bundled logback-access XML. Wiretap depends on
    // janino as `implementation`, which means consumers don't get it
    // transitively — re-declare it here.
    implementation("org.codehaus.janino:janino")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.awaitility:awaitility:4.2.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // SB 4 moved TestRestTemplate from spring-boot-test into the standalone
    // spring-boot-resttestclient module. On older Spring Boot lines the class
    // is already on the classpath via spring-boot-starter-test.
    if (springBootVersion.startsWith("4.")) {
        testImplementation("org.springframework.boot:spring-boot-resttestclient")
    }
}

tasks.test {
    useJUnitPlatform()
    if (javaToolchainVersion >= 25) {
        systemProperty("net.bytebuddy.experimental", "true")
    }
}

// On SB 3.3+ rewrite the logback-access SPI import to match the common-API
// build of wiretap (the same rewrite the wiretap-spring-boot-3.4.5/3.5.14
// subprojects apply to root sources). SB 4.0+ additionally relocates the
// {RestTemplate,RestClient,WebClient}-related types into per-client modules
// and TestRestTemplate into spring-boot-resttestclient.
if (!springBootVersion.startsWith("3.2.")) {
    val isSpringBoot4 = springBootVersion.startsWith("4.")

    fun rewriteLine(line: String): String {
        var rewritten = line.replace(
            "ch.qos.logback.access.spi.IAccessEvent",
            "ch.qos.logback.access.common.spi.IAccessEvent"
        )
        if (isSpringBoot4) {
            rewritten = rewritten
                .replace(
                    "org.springframework.boot.web.client.RestTemplateBuilder",
                    "org.springframework.boot.restclient.RestTemplateBuilder"
                )
                .replace(
                    "org.springframework.boot.web.client.RestTemplateCustomizer",
                    "org.springframework.boot.restclient.RestTemplateCustomizer"
                )
                .replace(
                    "org.springframework.boot.web.client.RestClientCustomizer",
                    "org.springframework.boot.restclient.RestClientCustomizer"
                )
                .replace(
                    "org.springframework.boot.web.reactive.function.client.WebClientCustomizer",
                    "org.springframework.boot.webclient.WebClientCustomizer"
                )
                .replace(
                    "org.springframework.boot.test.web.client.TestRestTemplate",
                    "org.springframework.boot.resttestclient.TestRestTemplate"
                )
                // SB 4 no longer creates the TestRestTemplate bean implicitly
                // for @SpringBootTest(webEnvironment=RANDOM_PORT). Inject the
                // explicit auto-configuration annotation right after the
                // @EmbeddedKafka block on the base class.
                .replace(
                    "@ExtendWith(OutputCaptureExtension.class)",
                    "@org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate\n" +
                        "@ExtendWith(OutputCaptureExtension.class)"
                )
                // Spring Boot 4 ships Jackson 3 (tools.jackson.*). Integration-tests
                // parse captured JSON via JsonLogCapture; rewrite its imports and
                // the no-arg ObjectMapper construction so it compiles on Jackson 3.
                .replace("com.fasterxml.jackson.core.", "tools.jackson.core.")
                .replace("com.fasterxml.jackson.databind.", "tools.jackson.databind.")
                .replace(
                    "new ObjectMapper()",
                    "tools.jackson.databind.json.JsonMapper.builder().build()"
                )
        }
        return rewritten
    }

    val rewriteMainSources = tasks.register<Copy>("rewriteMainSources") {
        from("src/main/java")
        into(layout.buildDirectory.dir("generated/sources/logback-access-common/java/main"))
        filter { rewriteLine(it) }
    }
    val rewriteTestSources = tasks.register<Copy>("rewriteTestSources") {
        from("src/test/java")
        into(layout.buildDirectory.dir("generated/sources/logback-access-common/java/test"))
        filter { rewriteLine(it) }
    }
    sourceSets.main {
        java.setSrcDirs(listOf(layout.buildDirectory.dir("generated/sources/logback-access-common/java/main")))
    }
    sourceSets.test {
        java.setSrcDirs(listOf(layout.buildDirectory.dir("generated/sources/logback-access-common/java/test")))
    }
    tasks.named("compileJava") { dependsOn(rewriteMainSources) }
    tasks.named("compileTestJava") { dependsOn(rewriteTestSources) }
}

// Not a publishable artifact: an integration-test harness, not a distribution.
tasks.named("bootJar") { enabled = false }
