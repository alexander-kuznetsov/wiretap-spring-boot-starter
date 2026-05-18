plugins {
    // Auto-provision JDKs for Java toolchain. Lets CI / scripts/test-compatibility.sh
    // run `-PjavaToolchain=21` even on hosts that only have a different JDK installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "wiretap"
