plugins {
    id("net.neoforged.moddev")
}

val modId = property("mod.id") as String
val mcVersion = sc.current.version

version = "${property("mod.version")}+$mcVersion"
group = property("mod.group") as String
base.archivesName = "$modId-neoforge"

java {
    withSourcesJar()
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

// Per-version data (1.21 uses singular data folder names and a newer recipe
// format) and mod metadata, which JSON/TOML can't express via Stonecutter comments
sourceSets.main {
    resources.srcDir(rootProject.file("src/versioned/$mcVersion/resources"))
}

repositories {
    maven("https://maven.createmod.net") { name = "Create" }          // Create, Ponder, Flywheel
    maven("https://maven.ithundxr.dev/snapshots") { name = "Registrate" }
    maven("https://maven.blamejared.com") { name = "JEI" }
    maven("https://maven.squiddev.cc") {
        name = "CC: Tweaked"
        content { includeGroup("cc.tweaked") }
    }
}

dependencies {
    // Slim = Create without its bundled jars; we supply the companions below at
    // the exact versions Create's release ships with
    implementation("com.simibubi.create:create-$mcVersion:${property("deps.create")}:slim") { isTransitive = false }
    implementation("net.createmod.ponder:ponder-neoforge:${property("deps.ponder")}+mc$mcVersion")
    compileOnly("dev.engine-room.flywheel:flywheel-neoforge-api-$mcVersion:${property("deps.flywheel")}")
    runtimeOnly("dev.engine-room.flywheel:flywheel-neoforge-$mcVersion:${property("deps.flywheel")}")
    implementation("com.tterrag.registrate:Registrate:${property("deps.registrate")}")
    // Bundled so the jar stands alone; JarJar resolves to the newest copy present
    jarJar("com.tterrag.registrate:Registrate") {
        version {
            strictly("[MC1.21-1.3.0,MC1.22)")
            prefer(property("deps.registrate") as String)
        }
    }

    // JEI is optional: compile against its API, run the full mod in dev only
    compileOnly("mezz.jei:jei-$mcVersion-common-api:${property("deps.jei")}")
    compileOnly("mezz.jei:jei-$mcVersion-neoforge-api:${property("deps.jei")}")
    runtimeOnly("mezz.jei:jei-$mcVersion-neoforge:${property("deps.jei")}")

    // CC: Tweaked is optional: compile against its API, run the full mod in dev only
    // (its NeoForge artifacts keep the "forge" name)
    compileOnly("cc.tweaked:cc-tweaked-$mcVersion-forge-api:${property("deps.cct")}") { isTransitive = false }
    compileOnly("cc.tweaked:cc-tweaked-$mcVersion-core-api:${property("deps.cct")}") { isTransitive = false }
    runtimeOnly("cc.tweaked:cc-tweaked-$mcVersion-forge:${property("deps.cct")}") { isTransitive = false }

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

neoForge {
    version = property("deps.neoforge") as String

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
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
            gameDirectory = rootProject.file("run/$mcVersion/gametest")
        }
    }

    // Unit tests run inside FML so NeoForge-patched vanilla classes bootstrap
    unitTest {
        enable()
        testedMod = mods.getByName(modId)
    }
}

tasks.test {
    useJUnitPlatform()
}

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
    exclude("META-INF/mods.toml", "pack.mcmeta")
    inputs.properties(resourceProps)
    filesMatching("META-INF/neoforge.mods.toml") { expand(resourceProps) }
}

tasks.named("createMinecraftArtifacts") {
    dependsOn("stonecutterGenerate")
}
