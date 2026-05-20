pluginManagement {
    // The Spring Boot Gradle Plugin version follows the same matrix knob as the
    // BOM in the root build (-PspringBootVersion=...). Letting the plugin version
    // drift from the BOM produces hard-to-diagnose runtime mismatches.
    val springBootVersion = providers.gradleProperty("springBootVersion").orElse("3.2.7").get()
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("org.springframework.boot") version springBootVersion
    }
}

plugins {
    // Auto-provision JDKs for Java toolchain. Lets CI / scripts/test-compatibility.sh
    // run `-PjavaToolchain=21` even on hosts that only have a different JDK installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "wiretap"

include(":wiretap-spring-boot-3.2.7-starter")
include(":wiretap-spring-boot-3.4.5-starter")
include(":wiretap-spring-boot-3.5.14-starter")
include(":wiretap-spring-boot-4.0.6-starter")
include(":integration-tests")
