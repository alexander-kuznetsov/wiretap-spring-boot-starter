import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SonatypeHost

// Wiretap built and tested against Spring Boot 4.0.6. Spring Boot 4 keeps
// Logback on the 1.5.x line (logback-classic 1.5.32) but pulls a new major
// of dev.akkinoc.spring.boot:logback-access-spring-boot-starter (5.0.1),
// whose parent is spring-boot-starter-parent:4.0.6.
//
// SB 4 also ships Jackson 3 (tools.jackson.*), which renamed several JsonNode
// methods (fields()/elements() returning collections instead of Iterators) and
// made ObjectMapper immutable. The text-based Copy-with-filter task that other
// subprojects use cannot patch those API differences in isolation, so every
// wiretap class that touches Jackson lives in src/main/java/ as a hand-written
// overlay against the Jackson 3 API. The Copy task below imports the
// remaining (Jackson-agnostic) sources from the root and rewrites only the
// Spring Boot 4 module relocations.
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
val isActive = matrixSpringBootVersion == null || matrixSpringBootVersion == targetSpringBootVersion
val javaToolchainVersion = providers.gradleProperty("javaToolchain").getOrElse("17").toInt()

val feignCoreVersion = providers.gradleProperty("feignCoreVersion").getOrElse("13.5")
val lombokVersion = "1.18.42"
// 9.0 is the first logstash-logback-encoder line built against tools.jackson
// (Jackson 3), which is what Spring Boot 4 ships.
val logstashLogbackEncoderVersion = "9.0"
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
        // Spring Boot 4 BOM pins both jackson-bom (2.21.x annotations) and
        // tools.jackson:jackson-bom (3.1.x core/databind); no need to import
        // either separately.
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
    api("org.springframework.boot:spring-boot-jackson")

    api("io.micrometer:micrometer-tracing-bridge-brave")
    // SB 4 split tracing autoconfig out of spring-boot-autoconfigure into
    // dedicated modules so the Tracer bean is contributed by BraveAutoConfiguration.
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
    // wiretap does not reference it from source or XML configs, so we omit it
    // here to keep the classpath free of com.fasterxml.jackson.databind alongside
    // tools.jackson.databind.
    implementation("org.codehaus.janino:janino")

    // Jackson 3 lives under the tools.jackson coordinate. Pull both core and
    // databind transitively through spring-boot-jackson, but make them explicit
    // api here so consumers don't have to depend on the SB BOM.
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
    // Brings Micrometer's MeterRegistry / Timer / Counter API in at compile time.
    // Runtime delivery is transitive — spring-boot-starter-actuator pulls it in,
    // and consumers without actuator simply fall back to NoOpWiretapMetrics.
    compileOnly("io.micrometer:micrometer-core")

    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    compileOnly("org.projectlombok:lombok:$lombokVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.kafka:spring-kafka")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.wiremock:wiremock-standalone:3.6.0")
    testImplementation("io.micrometer:micrometer-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testCompileOnly("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")
}

// Jackson-using sources live in this subproject's src/main/java as hand-written
// overlay copies (Jackson 3 API). The Copy task below imports every other
// wiretap source from the root and rewrites only the Spring Boot 4 module
// relocations and the logback-access SPI move. Files in this list are
// excluded from the Copy task so the overlay copy wins.
val overlayMainFiles = listOf(
    "io/wiretap/applog/extra/ExtraAppLogContextKeeper.java",
    "io/wiretap/applog/provider/LazyStandardLogFieldsProvider.java",
    "io/wiretap/applog/provider/WiretapDelegatingLogFieldProvider.java",
    "io/wiretap/applog/provider/WiretapLogFieldProvider.java",
    "io/wiretap/applog/provider/WiretapStandardLogFieldsProvider.java",
    "io/wiretap/http/incoming/provider/httpinfo/HttpInfoMessageProvider.java",
    "io/wiretap/http/incoming/provider/httpinfo/LazyHttpInfoMessageProvider.java",
    "io/wiretap/http/incoming/provider/message/LazyMessageProvider.java",
    "io/wiretap/http/incoming/provider/message/MessageProvider.java",
    "io/wiretap/http/incoming/provider/trace/SpanIdProvider.java",
    "io/wiretap/http/incoming/provider/trace/TraceIdProvider.java",
    "io/wiretap/http/incoming/provider/WiretapAccessFieldProvider.java",
    "io/wiretap/http/incoming/provider/WiretapDelegatingFieldProvider.java",
    "io/wiretap/http/message/HttpMessageInfo.java",
    "io/wiretap/http/message/settings/body/BodyParser.java",
    "io/wiretap/http/message/settings/body/DefaultBodyParser.java",
    "io/wiretap/http/message/settings/body/HttpBodyMasker.java",
    "io/wiretap/http/outgoing/interceptor/feignclient/FeignClientWrapper.java",
    "io/wiretap/http/outgoing/interceptor/rest/RestLoggingInterceptor.java",
    "io/wiretap/http/outgoing/interceptor/webclient/WebClientLoggingFilter.java",
    "io/wiretap/http/outgoing/interceptor/webservicetemplate/WebServiceTemplateLoggingInterceptor.java",
    "io/wiretap/kafka/KafkaLogSink.java",
    "io/wiretap/util/HttpBodyUtils.java",
    "io/wiretap/util/JsonBodyUtils.java",
)
val overlayTestFiles = listOf(
    "io/wiretap/applog/provider/WiretapDelegatingLogFieldProviderTest.java",
    "io/wiretap/http/incoming/provider/LazyDelegatesTest.java",
    "io/wiretap/http/incoming/provider/trace/TraceIdProvidersTest.java",
    "io/wiretap/http/incoming/provider/WiretapDelegatingFieldProviderTest.java",
    "io/wiretap/http/message/settings/body/DefaultBodyParserTest.java",
    "io/wiretap/http/outgoing/interceptor/rest/RestTemplateLoggingInterceptorTest.java",
    "io/wiretap/http/outgoing/interceptor/webclient/WebClientLoggingFilterTest.java",
    "io/wiretap/kafka/consumer/WiretapRecordInterceptorTest.java",
    "io/wiretap/kafka/producer/WiretapProducerListenerTest.java",
    "io/wiretap/util/JsonBodyUtilsTest.java",
)

// Patches Jackson-agnostic wiretap sources for Spring Boot 4: logback-access
// common-API + per-client module relocations. Jackson is untouched here because
// every Jackson-using class is overridden via the overlay above.
fun rewriteForSpringBoot4(line: String): String =
    line
        .replace(
            "ch.qos.logback.access.spi.IAccessEvent",
            "ch.qos.logback.access.common.spi.IAccessEvent"
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
            "org.springframework.boot.web.client.RestTemplateBuilder",
            "org.springframework.boot.restclient.RestTemplateBuilder"
        )
        .replace(
            "org.springframework.boot.web.reactive.function.client.WebClientCustomizer",
            "org.springframework.boot.webclient.WebClientCustomizer"
        )
        .replace(
            "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration",
            "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"
        )

val rewriteMainSources = tasks.register<Copy>("rewriteMainSources") {
    from(rootProject.layout.projectDirectory.dir("src/main/java")) {
        exclude(overlayMainFiles)
    }
    into(layout.buildDirectory.dir("generated/sources/sb4/java/main"))
    filter { rewriteForSpringBoot4(it) }
}

val rewriteTestSources = tasks.register<Copy>("rewriteTestSources") {
    from(rootProject.layout.projectDirectory.dir("src/test/java")) {
        exclude(overlayTestFiles)
    }
    into(layout.buildDirectory.dir("generated/sources/sb4/java/test"))
    filter { rewriteForSpringBoot4(it) }
}

sourceSets {
    main {
        // Overlay first so the hand-written Jackson 3 copies win over anything
        // the Copy task might still produce for the same FQN (defence in depth —
        // the exclude list above is already authoritative).
        java.setSrcDirs(
            listOf(
                layout.projectDirectory.dir("src/main/java"),
                layout.buildDirectory.dir("generated/sources/sb4/java/main"),
            )
        )
        resources.setSrcDirs(listOf(rootProject.layout.projectDirectory.dir("src/main/resources")))
    }
    test {
        java.setSrcDirs(
            listOf(
                layout.projectDirectory.dir("src/test/java"),
                layout.buildDirectory.dir("generated/sources/sb4/java/test"),
            )
        )
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
        description.set("Structured JSON logging for Spring Boot 4.0.6 — captures HTTP and Kafka traffic across all the standard clients. Built against Spring Framework 7 / Jackson 3 / logback-access-spring-boot-starter 5.0.1.")
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
