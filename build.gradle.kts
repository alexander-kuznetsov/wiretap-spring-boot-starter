import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    `java-library`
    `maven-publish`
    signing
    id("io.spring.dependency-management") version "1.1.7"
    id("com.vanniktech.maven.publish") version "0.30.0"
}

group = "io.github.alexander-kuznetsov"
version = "0.1.4-SNAPSHOT"

// ---------------------------------------------------------------------------
// Version matrix knobs. Override via -PspringBootVersion=... / -PjavaToolchain=21
// (see scripts/test-compatibility.sh and .github/workflows/compatibility.yml).
// ---------------------------------------------------------------------------
val springBootVersion = providers.gradleProperty("springBootVersion").getOrElse("3.2.7")
val logbackAccessSpringVersion = providers.gradleProperty("logbackAccessSpringVersion")
    .getOrElse(defaultLogbackAccessFor(springBootVersion))
val feignCoreVersion = providers.gradleProperty("feignCoreVersion").getOrElse("13.5")
val javaToolchain = providers.gradleProperty("javaToolchain").getOrElse("17").toInt()

val lombokVersion = "1.18.42"
val jacksonVersion = "2.17.1"
val logstashLogbackEncoderVersion = "7.4"
val logbackJacksonVersion = "0.1.5"
val httpClientVersion = "4.5.14"
val guavaVersion = "33.2.1-jre"
val micrometerTracingVersion = "1.3.1"
val commonsValidatorVersion = "1.9.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaToolchain)
    }
    // sources/javadoc jars enabled implicitly by the vanniktech plugin's
    // JavaLibrary publish profile (see mavenPublishing block).
}

// Javadoc на этой версии Java падает на странных constructs из логбэка /
// micrometer, поэтому ослабляем strict-mode — релиз-блокер не нужен.
tasks.withType<Javadoc>().configureEach {
    (options as? StandardJavadocDocletOptions)?.addStringOption("Xdoclint:none", "-quiet")
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
        mavenBom("com.fasterxml.jackson:jackson-bom:$jacksonVersion")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")

    api("io.micrometer:micrometer-tracing-bridge-brave")
    api("io.github.openfeign:feign-core:$feignCoreVersion")

    // api: WiretapAccessFieldProvider exposes ch.qos.logback.access.spi.IAccessEvent
    // in its method signature; consumers implementing the SPI need it on compile classpath.
    api("dev.akkinoc.spring.boot:logback-access-spring-boot-starter:$logbackAccessSpringVersion")
    // api: WiretapLogFieldProvider exposes ch.qos.logback.classic.spi.ILoggingEvent.
    // Usually present transitively via spring-boot-starter-logging, but we don't rely on that.
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

tasks.test {
    useJUnitPlatform()
    // Byte Buddy / Mockito do not officially support Java 25 yet; opt into experimental support.
    systemProperty("net.bytebuddy.experimental", "true")
}

// JaCoCo coverage will be added once a release supports Java 25 class files
// (current 0.8.13 stops at Java 24). Track on https://github.com/jacoco/jacoco/issues

// Maven Central via Sonatype Central Portal (vanniktech plugin) for releases.
// GitHub Packages remains for SNAPSHOTs from main (see .github/workflows/publish.yml).
mavenPublishing {
    // Sonatype Central Portal (the post-June-2024 endpoint).
    // Without an explicit host the plugin falls back to the legacy OSSRH
    // (s01.oss.sonatype.org), which returns HTTP 402 for Central-Portal accounts.
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    // Tells vanniktech which artifacts to produce: jar + sources + javadoc.
    configure(JavaLibrary(javadocJar = JavadocJar.Javadoc(), sourcesJar = true))

    coordinates("io.github.alexander-kuznetsov", "wiretap", project.version.toString())

    pom {
        name.set("Wiretap")
        description.set("Structured JSON logging for Spring Boot — captures HTTP and Kafka traffic across all the standard clients.")
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

// Snapshot publishing handled by the same vanniktech plugin: when version
// ends with -SNAPSHOT, publishToMavenCentral targets the Central snapshots
// repository (https://central.sonatype.com/repository/maven-snapshots/),
// readable by anyone without credentials.

/**
 * Maps Spring Boot major.minor → compatible logback-access-spring-boot-starter version.
 * Update the 4.x branch once dev.akkinoc.spring.boot ships a Spring Boot 4 line.
 */
fun defaultLogbackAccessFor(boot: String): String = when {
    boot.startsWith("3.") -> "4.1.2"
    boot.startsWith("4.") -> "5.0.0"
    else -> error("Unsupported Spring Boot version: $boot")
}
