import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SonatypeHost

// Wiretap built and tested against Spring Boot 3.5.14 (Logback 1.5 / logback-access
// common-API line). Root sources are imported through a Copy-with-filter task
// that rewrites ch.qos.logback.access.spi.IAccessEvent →
// ch.qos.logback.access.common.spi.IAccessEvent.
//
// onlyIf: when the matrix passes -PspringBootVersion, this subproject runs
// only on its target cell; without the property (e.g. during release) all
// three SB-version subprojects build together so vanniktech can publish
// every coordinate in one Gradle invocation.

plugins {
    `java-library`
    `maven-publish`
    signing
    id("io.spring.dependency-management") version "1.1.7"
    id("com.vanniktech.maven.publish") version "0.32.0"
}

group = rootProject.group
version = rootProject.version

val targetSpringBootVersion = "3.5.14"
val targetLogbackAccessSpringVersion = "4.7.1"
val matrixSpringBootVersion = providers.gradleProperty("springBootVersion").orNull
val isActive = matrixSpringBootVersion == null || matrixSpringBootVersion == targetSpringBootVersion
val javaToolchainVersion = providers.gradleProperty("javaToolchain").getOrElse("17").toInt()

val feignCoreVersion = providers.gradleProperty("feignCoreVersion").getOrElse("13.5")
val lombokVersion = "1.18.42"
val jacksonVersion = "2.17.1"
val logstashLogbackEncoderVersion = "8.1"
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
        mavenBom("org.springframework.boot:spring-boot-dependencies:$targetSpringBootVersion")
        mavenBom("com.fasterxml.jackson:jackson-bom:$jacksonVersion")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")

    api("io.micrometer:micrometer-tracing-bridge-brave")
    api("io.github.openfeign:feign-core:$feignCoreVersion")

    api("dev.akkinoc.spring.boot:logback-access-spring-boot-starter:$targetLogbackAccessSpringVersion")
    // logback-access-tomcat is marked <optional> in starter 4.2+ POMs; add it
    // back as api so Tomcat-based consumers get the access valve transparently.
    api("ch.qos.logback.access:logback-access-tomcat:2.0.12")
    api("ch.qos.logback:logback-classic")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoderVersion")
    implementation("ch.qos.logback.contrib:logback-jackson:$logbackJacksonVersion")
    implementation("org.codehaus.janino:janino")

    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.core:jackson-core")
    implementation("com.fasterxml.jackson.core:jackson-annotations")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

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

val rewriteMainSources = tasks.register<Copy>("rewriteMainSources") {
    from(rootProject.layout.projectDirectory.dir("src/main/java"))
    into(layout.buildDirectory.dir("generated/sources/logback-access-common/java/main"))
    filter { line ->
        line.replace(
            "ch.qos.logback.access.spi.IAccessEvent",
            "ch.qos.logback.access.common.spi.IAccessEvent"
        )
    }
}

val rewriteTestSources = tasks.register<Copy>("rewriteTestSources") {
    from(rootProject.layout.projectDirectory.dir("src/test/java"))
    into(layout.buildDirectory.dir("generated/sources/logback-access-common/java/test"))
    filter { line ->
        line.replace(
            "ch.qos.logback.access.spi.IAccessEvent",
            "ch.qos.logback.access.common.spi.IAccessEvent"
        )
    }
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

// Gradle 9 requires explicit producer/consumer wiring for any task that
// reads the generated `main` sourceSet — without it `sourcesJar` (vanniktech
// publish) and `javadoc` would consume `rewriteMainSources` output without
// declaring the dependency and the build fails validation. `sourcesJar` is
// registered by the publish plugin during configure, so wire it in
// `afterEvaluate` rather than at script eval.
afterEvaluate {
    tasks.findByName("sourcesJar")?.dependsOn(rewriteMainSources)
    tasks.findByName("javadoc")?.dependsOn(rewriteMainSources)
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
    coordinates("io.github.alexander-kuznetsov", "wiretap-spring-boot-3.5.14-starter", project.version.toString())
    pom {
        name.set("Wiretap (Spring Boot 3.5.14)")
        description.set("Structured JSON logging for Spring Boot 3.5.14 — captures HTTP and Kafka traffic across all the standard clients. Pinned to the Logback 1.5 / logback-access common-API line; for Spring Boot 3.2 use wiretap-spring-boot-3.2.7-starter instead.")
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
