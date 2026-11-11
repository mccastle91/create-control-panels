plugins {
    id("net.neoforged.moddev.legacyforge")
}

val modId = property("mod.id") as String
val mcVersion = sc.current.version

version = "${property("mod.version")}+$mcVersion"
group = property("mod.group") as String
base.archivesName = "$modId-forge"

// 1.20.1 ships on Java 17: compiling the shared tree at release 17 also keeps
// newer-only language features out of code that both builds share
java {
    withSourcesJar()
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
}

// Per-version data (1.20.1 uses plural data folder names and the older
// recipe result format) and mod metadata
sourceSets.main {
    resources.srcDir(rootProject.file("src/versioned/$mcVersion/resources"))
}

repositories {
    maven("https://maven.createmod.net") { name = "Create" }        // Create, Ponder, Flywheel
    maven("https://maven.ithundxr.dev/mirror") { name = "Registrate" }
    maven("https://maven.blamejared.com") { name = "JEI" }
    maven("https://maven.squiddev.cc") {
        name = "CC: Tweaked"
        content { includeGroup("cc.tweaked") }
    }
}

dependencies {
    // Mod jars on 1.20.1 are published in SRG names; mod* configurations remap
    // them to the Mojang names we compile against. They are not transitive.
    modImplementation("com.simibubi.create:create-$mcVersion:${property("deps.create")}:slim") { isTransitive = false }
    modImplementation("net.createmod.ponder:Ponder-Forge-$mcVersion:${property("deps.ponder")}")
    modCompileOnly("dev.engine-room.flywheel:flywheel-forge-api-$mcVersion:${property("deps.flywheel")}")
    modRuntimeOnly("dev.engine-room.flywheel:flywheel-forge-$mcVersion:${property("deps.flywheel")}")
    modImplementation("com.tterrag.registrate:Registrate:${property("deps.registrate")}")
    jarJar("com.tterrag.registrate:Registrate") {
        version {
            strictly("[MC1.20-1.3.3,MC1.21)")
            prefer(property("deps.registrate") as String)
        }
    }
    // JEI is optional: compile against its API, run the full mod in dev only
    modCompileOnly("mezz.jei:jei-$mcVersion-common-api:${property("deps.jei")}")
    modCompileOnly("mezz.jei:jei-$mcVersion-forge-api:${property("deps.jei")}")
    modRuntimeOnly("mezz.jei:jei-$mcVersion-forge:${property("deps.jei")}")
    // CC: Tweaked is optional: compile against its API, run the full mod in dev only
    modCompileOnly("cc.tweaked:cc-tweaked-$mcVersion-forge-api:${property("deps.cct")}")
    compileOnly("cc.tweaked:cc-tweaked-$mcVersion-core-api:${property("deps.cct")}")
    modRuntimeOnly("cc.tweaked:cc-tweaked-$mcVersion-forge:${property("deps.cct")}")
    // Create's mixins need MixinExtras at runtime; its release jar bundles it
    compileOnly(annotationProcessor("io.github.llamalad7:mixinextras-common:${property("deps.mixinextras")}")!!)
    implementation("io.github.llamalad7:mixinextras-forge:${property("deps.mixinextras")}")
}

legacyForge {
    version = "$mcVersion-${property("deps.forge")}"

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        register("client") {
            client()
            gameDirectory = rootProject.file("run/$mcVersion/client")
        }
        register("server") {
            server()
            programArgument("--nogui")
            gameDirectory = rootProject.file("run/$mcVersion/server")
        }
        register("gametest") {
            type = "gameTestServer"
            systemProperty("forge.enabledGameTestNamespaces", modId)
            gameDirectory = rootProject.file("run/$mcVersion/gametest")
        }
    }
}

// Unit tests run on the 1.21.1 target only (inside FML there); behaviour on
// 1.20.1 is covered by the game tests, which run against this target too
tasks.named("compileTestJava") { enabled = false }
tasks.named("test") { enabled = false }

val resourceProps = mapOf(
    "mod_id" to modId,
    "mod_name" to property("mod.name"),
    "mod_version" to property("mod.version"),
    "mod_authors" to property("mod.authors"),
    "mod_license" to property("mod.license"),
    "mc_compat" to property("mod.mc_compat"),
    "create_min" to property("deps.create_min"),
)

tasks.processResources {
    exclude("META-INF/neoforge.mods.toml")
    inputs.properties(resourceProps)
    filesMatching("META-INF/mods.toml") { expand(resourceProps) }
}

tasks.named("createMinecraftArtifacts") {
    dependsOn("stonecutterGenerate")
}
