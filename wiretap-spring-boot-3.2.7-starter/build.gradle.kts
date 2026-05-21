import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SonatypeHost

// Wiretap built and tested against Spring Boot 3.2.7 (Logback 1.4 line).
// Logback-access SPI is at ch.qos.logback.access.spi.IAccessEvent — root
// sources are imported as-is, no package rewrite needed.
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
    id("com.vanniktech.maven.publish") version "0.30.0"
}

group = rootProject.group
version = rootProject.version

val targetSpringBootVersion = "3.2.7"
val targetLogbackAccessSpringVersion = "4.1.2"
val matrixSpringBootVersion = providers.gradleProperty("springBootVersion").orNull
val isActive = matrixSpringBootVersion == null || matrixSpringBootVersion == targetSpringBootVersion
val javaToolchainVersion = providers.gradleProperty("javaToolchain").getOrElse("17").toInt()

val feignCoreVersion = providers.gradleProperty("feignCoreVersion").getOrElse("13.5")
val lombokVersion = "1.18.42"
val jacksonVersion = "2.17.1"
val logstashLogbackEncoderVersion = "7.4"
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

// Use root sources verbatim — Logback 1.4 ships the legacy IAccessEvent package.
sourceSets {
    main {
        java.setSrcDirs(listOf(rootProject.layout.projectDirectory.dir("src/main/java")))
        resources.setSrcDirs(listOf(rootProject.layout.projectDirectory.dir("src/main/resources")))
    }
    test {
        java.setSrcDirs(listOf(rootProject.layout.projectDirectory.dir("src/test/java")))
        resources.setSrcDirs(listOf(rootProject.layout.projectDirectory.dir("src/test/resources")))
    }
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(tasks.named("processResources"))
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
    coordinates("io.github.alexander-kuznetsov", "wiretap-spring-boot-3.2.7-starter", project.version.toString())
    pom {
        name.set("Wiretap (Spring Boot 3.2.7)")
        description.set("Structured JSON logging for Spring Boot 3.2.7 — captures HTTP and Kafka traffic across all the standard clients. Pinned to the Logback 1.4 / logback-access 1.x line; for Spring Boot 3.3+ use the matching wiretap-spring-boot-3.x.y-starter coordinate.")
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
