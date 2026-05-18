plugins {
    `java-library`
    `maven-publish`
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.wiretap"
version = "0.1.0-SNAPSHOT"

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

    implementation("dev.akkinoc.spring.boot:logback-access-spring-boot-starter:$logbackAccessSpringVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoderVersion")
    implementation("ch.qos.logback.contrib:logback-jackson:$logbackJacksonVersion")
    implementation("ch.qos.logback:logback-classic")
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

// Maven Central via JReleaser is Phase 7. Until then we publish snapshots to
// GitHub Packages and let consumers build against mavenLocal.
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("Wiretap")
                description.set("Structured JSON logging for Spring Boot — captures HTTP and Kafka traffic across all the standard clients.")
                url.set("https://github.com/alexander-kuznetsov/wiretap-spring-boot-starter")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                scm {
                    url.set("https://github.com/alexander-kuznetsov/wiretap-spring-boot-starter")
                    connection.set("scm:git:https://github.com/alexander-kuznetsov/wiretap-spring-boot-starter.git")
                    developerConnection.set("scm:git:ssh://git@github.com/alexander-kuznetsov/wiretap-spring-boot-starter.git")
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/alexander-kuznetsov/wiretap-spring-boot-starter")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .getOrElse("")
                password = providers.gradleProperty("gpr.token")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .getOrElse("")
            }
        }
    }
}

/**
 * Maps Spring Boot major.minor → compatible logback-access-spring-boot-starter version.
 * Update the 4.x branch once dev.akkinoc.spring.boot ships a Spring Boot 4 line.
 */
fun defaultLogbackAccessFor(boot: String): String = when {
    boot.startsWith("3.") -> "4.1.2"
    boot.startsWith("4.") -> "5.0.0"
    else -> error("Unsupported Spring Boot version: $boot")
}
