plugins {
    `java-library`
    `maven-publish`
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.wiretap"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val lombokVersion = "1.18.42"
val springBootVersion = "3.2.7"
val springCloudVersion = "2023.0.2"
val jacksonVersion = "2.17.1"
val logstashLogbackEncoderVersion = "7.4"
val logbackJacksonVersion = "0.1.5"
val logbackAccessSpringVersion = "4.1.2"
val httpClientVersion = "4.5.14"
val guavaVersion = "33.2.1-jre"
val micrometerTracingVersion = "1.3.1"
val commonsValidatorVersion = "1.9.0"

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
        mavenBom("com.fasterxml.jackson:jackson-bom:$jacksonVersion")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    api("io.micrometer:micrometer-tracing-bridge-brave")
    api("io.github.openfeign:feign-core")

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

    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    compileOnly("org.projectlombok:lombok:$lombokVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
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

// Publication setup will be wired through JReleaser to Maven Central in Phase 7.
// For now keep maven-publish configured locally so consumers can build against snapshots.
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
