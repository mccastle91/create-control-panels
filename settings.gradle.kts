pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // One source tree, two targets. Each target gets its own build script
        // because 1.20.1 is Forge (ModDevGradle legacy) and 1.21.1 is NeoForge.
        version("1.20.1-forge", "1.20.1").buildscript("build.forge.gradle.kts")
        version("1.21.1-neoforge", "1.21.1").buildscript("build.neoforge.gradle.kts")
        vcsVersion = "1.21.1-neoforge"
    }
}

rootProject.name = "create-controlling"
