import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SonatypeHost

// Wiretap built and tested against Spring Boot 4.0.6. Spring Boot 4 keeps
// Logback on the 1.5.x line (logback-classic 1.5.32) but pulls a new major
// of dev.akkinoc.spring.boot:logback-access-spring-boot-starter (5.0.1),
// whose parent is spring-boot-starter-parent:4.0.6. The IAccessEvent SPI is
// in the same common-API package as for 3.4.5/3.5.14, so we reuse the same
// Copy-with-filter rewrite (ch.qos.logback.access.spi.IAccessEvent →
// ch.qos.logback.access.common.spi.IAccessEvent).
//
// onlyIf: when the matrix passes -PspringBootVersion, this subproject runs
// only on its target cell; without the property (e.g. during release) all
// four SB-version subprojects build together so vanniktech can publish
// every coordinate in one Gradle invocation.

plugins {
    `java-library`
    `maven-publish`
    signing
    id("io.spring.dependency-management") version "1.1.7"
    id("com.vanniktech.maven.publish") version "0.30.0"
}

group = rootProject.group
version = rootProject.version

val targetSpringBootVersion = "4.0.6"
val targetLogbackAccessSpringVersion = "5.0.1"
val targetLogbackAccessCommonVersion = "2.0.12"
val matrixSpringBootVersion = providers.gradleProperty("springBootVersion").orNull
// WIP: this subproject is checked in as scaffolding for the Spring Boot 4
// artifact but is intentionally disabled. Spring Boot 4 ships Jackson 3
// (tools.jackson.*), which changed not only the namespace but also several
// JsonNode method names (`fields()` → `properties()`, `elements()` → `values()`)
// and turned ObjectMapper immutable; the source-rewrite Copy task cannot
// patch those API differences in isolation. Re-enable by flipping isActive
// to `matrixSpringBootVersion == null || matrixSpringBootVersion == targetSpringBootVersion`
// once the dedicated Jackson 3 migration lands under its own SSNC ticket.
val isActive = false
val javaToolchainVersion = providers.gradleProperty("javaToolchain").getOrElse("17").toInt()

val feignCoreVersion = providers.gradleProperty("feignCoreVersion").getOrElse("13.5")
val lombokVersion = "1.18.42"
val jacksonVersion = "2.17.1"
// 9.0 is the first logstash-logback-encoder line built against tools.jackson
// (Jackson 3), which is what Spring Boot 4 ships. rewriteForSpringBoot4 below
// patches wiretap sources to match.
val logstashLogbackEncoderVersion = "9.0"
val logbackJacksonVersion = "0.1.5"
val httpClientVersion = "4.5.14"
val guavaVersion = "33.2.1-jre"
val commonsValidatorVersion = "1.9.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaToolchainVersion)
    }
}

tasks.withType<Javadoc>().configureEach {
    (options as? StandardJavadocDocletOptions)?.addStringOption("Xdoclint:none", "-quiet")
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        // Spring Boot 4 BOM already pins both Jackson 2.21.x (legacy annotations)
        // and tools.jackson 3.1.x; no need to import jackson-bom separately.
        mavenBom("org.springframework.boot:spring-boot-dependencies:$targetSpringBootVersion")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    // Spring Boot 4 moved the {RestTemplate,RestClient,WebClient}Customizer
    // interfaces into dedicated modules; pull them in so the rewritten imports
    // resolve at compile time and at runtime when consumers configure clients.
    api("org.springframework.boot:spring-boot-restclient")
    api("org.springframework.boot:spring-boot-webclient")
    // SB 4 split JacksonAutoConfiguration out into spring-boot-jackson.
    // Used by AutoConfigurationSmokeTest at test time, but also pulled in as
    // api so consumer applications can depend on the same configuration.
    api("org.springframework.boot:spring-boot-jackson")

    api("io.micrometer:micrometer-tracing-bridge-brave")
    // SB 4 split tracing autoconfig out of spring-boot-autoconfigure into
    // dedicated modules; pull them in so the Tracer bean is contributed by
    // BraveAutoConfiguration as expected by wiretap's IncomingHttpConfiguration.
    api("org.springframework.boot:spring-boot-micrometer-tracing")
    api("org.springframework.boot:spring-boot-micrometer-tracing-brave")
    api("io.github.openfeign:feign-core:$feignCoreVersion")

    api("dev.akkinoc.spring.boot:logback-access-spring-boot-starter:$targetLogbackAccessSpringVersion")
    // logstash-logback-encoder 9.0 still pins logback-access-common 2.0.6, while
    // the starter brings 2.0.12. Force the higher one explicitly so the Spring
    // dependency-management plugin doesn't downgrade us back to the encoder's pin.
    api("ch.qos.logback.access:logback-access-common:$targetLogbackAccessCommonVersion")
    // logback-access-tomcat is marked <optional> in starter 4.2+ / 5.0+ POMs;
    // add it back as api so Tomcat-based consumers get the access valve transparently.
    api("ch.qos.logback.access:logback-access-tomcat:$targetLogbackAccessCommonVersion")
    api("ch.qos.logback:logback-classic")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoderVersion")
    // logback-jackson:0.1.5 is Jackson 2-only and has no Jackson 3 release.
    // It is not referenced from wiretap's source or XML configs, so we just
    // omit it here to avoid pulling com.fasterxml.jackson.databind alongside
    // tools.jackson.databind.
    implementation("org.codehaus.janino:janino")

    // Jackson 3 lives under the tools.jackson coordinate. We pull both core
    // and databind transitively through spring-boot-jackson, but make them
    // explicit api here so consumers don't have to depend on the SB BOM.
    api("tools.jackson.core:jackson-databind")
    api("tools.jackson.core:jackson-core")
    // Jackson 3 keeps annotations in the legacy com.fasterxml namespace for
    // backward-compat; the autoconfigure JacksonAnnotationIntrospector reflects
    // on JsonSerializeAs which only ships in jackson-annotations 2.21+.
    implementation("com.fasterxml.jackson.core:jackson-annotations")
    // jsr310 datatype is folded into jackson-databind in Jackson 3.

    implementation("org.apache.httpcomponents:httpclient:$httpClientVersion")
    implementation("com.google.guava:guava:$guavaVersion")
    implementation("commons-validator:commons-validator:$commonsValidatorVersion")
    api("org.apache.commons:commons-lang3:3.12.0")

    implementation("org.springframework.ws:spring-ws-core")
    implementation("javax.xml.soap:javax.xml.soap-api:1.4.0")

    compileOnly("org.springframework.boot:spring-boot-starter-webflux")
    compileOnly("org.springframework.kafka:spring-kafka")

    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    compileOnly("org.projectlombok:lombok:$lombokVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.kafka:spring-kafka")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.wiremock:wiremock-standalone:3.6.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testCompileOnly("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")
}

// Spring Boot 4 split spring-boot-autoconfigure into per-client / per-feature
// modules, moved Customizer / AutoConfiguration classes, and bumped Jackson
// from the com.fasterxml.jackson.* namespace to tools.jackson.*. The source
// rewrite below patches every relocation that wiretap's canonical sources hit.
fun rewriteForSpringBoot4(line: String): String =
    line
        // logback-access SPI: same rewrite as for SB 3.4+
        .replace(
            "ch.qos.logback.access.spi.IAccessEvent",
            "ch.qos.logback.access.common.spi.IAccessEvent"
        )
        // Spring Boot 4 module relocations
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
            "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration",
            "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"
        )
        // Jackson 2 → Jackson 3 namespace migration. Annotations stayed at
        // com.fasterxml.jackson.annotation, so we only swap core/databind.
        .replace("com.fasterxml.jackson.core.", "tools.jackson.core.")
        .replace("com.fasterxml.jackson.databind.", "tools.jackson.databind.")
        // Class renames: JsonProcessingException → JacksonException (now a
        // RuntimeException, but throws/catch declarations still compile).
        .replace("JsonProcessingException", "JacksonException")
        // new TextNode(value) → StringNode.construct(value) (Jackson 3 made
        // text nodes immutable via factory; the old class is gone). The
        // constructor sites get a fully-qualified replacement, so we drop the
        // now-unused TextNode import to avoid "class not found" errors.
        .replace("new TextNode(", "tools.jackson.databind.node.StringNode.construct(")
        .replace("import tools.jackson.databind.node.TextNode;", "")
        // ObjectMapper became immutable in Jackson 3 — patch the one fluent
        // pattern wiretap uses to construct it.
        .replace(
            "new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)",
            "tools.jackson.databind.json.JsonMapper.builder().serializationInclusion(JsonInclude.Include.NON_NULL).build()"
        )

val rewriteMainSources = tasks.register<Copy>("rewriteMainSources") {
    from(rootProject.layout.projectDirectory.dir("src/main/java"))
    into(layout.buildDirectory.dir("generated/sources/logback-access-common/java/main"))
    filter { rewriteForSpringBoot4(it) }
}

val rewriteTestSources = tasks.register<Copy>("rewriteTestSources") {
    from(rootProject.layout.projectDirectory.dir("src/test/java"))
    into(layout.buildDirectory.dir("generated/sources/logback-access-common/java/test"))
    filter { rewriteForSpringBoot4(it) }
}

sourceSets {
    main {
        java.setSrcDirs(listOf(layout.buildDirectory.dir("generated/sources/logback-access-common/java/main")))
        resources.setSrcDirs(listOf(rootProject.layout.projectDirectory.dir("src/main/resources")))
    }
    test {
        java.setSrcDirs(listOf(layout.buildDirectory.dir("generated/sources/logback-access-common/java/test")))
        resources.setSrcDirs(listOf(rootProject.layout.projectDirectory.dir("src/test/resources")))
    }
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(rewriteMainSources)
    dependsOn(tasks.named("processResources"))
}
tasks.named<JavaCompile>("compileTestJava") {
    dependsOn(rewriteTestSources)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("net.bytebuddy.experimental", "true")
}

if (!isActive) {
    tasks.matching { it.name != "help" }.configureEach {
        onlyIf("matrix targets Spring Boot $matrixSpringBootVersion, not $targetSpringBootVersion") { false }
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    configure(JavaLibrary(javadocJar = JavadocJar.Javadoc(), sourcesJar = true))
    coordinates("io.github.alexander-kuznetsov", "wiretap-spring-boot-4.0.6-starter", project.version.toString())
    pom {
        name.set("Wiretap (Spring Boot 4.0.6)")
        description.set("Structured JSON logging for Spring Boot 4.0.6 — captures HTTP and Kafka traffic across all the standard clients. Built against Spring Framework 7 / logback-access-spring-boot-starter 5.0.1.")
        url.set("https://github.com/alexander-kuznetsov/wiretap-spring-boot-starter")
        inceptionYear.set("2026")
        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("alexander-kuznetsov")
                name.set("Aleksandr Kuznetsov")
                email.set("suntey.kuznetsov@gmail.com")
                url.set("https://github.com/alexander-kuznetsov")
            }
        }
        scm {
            url.set("https://github.com/alexander-kuznetsov/wiretap-spring-boot-starter")
            connection.set("scm:git:https://github.com/alexander-kuznetsov/wiretap-spring-boot-starter.git")
            developerConnection.set("scm:git:ssh://git@github.com/alexander-kuznetsov/wiretap-spring-boot-starter.git")
        }
        issueManagement {
            system.set("GitHub")
            url.set("https://github.com/alexander-kuznetsov/wiretap-spring-boot-starter/issues")
        }
    }
}
