plugins {
    id("dev.kikugie.stonecutter")
    // Both plugins live in the same artifact; declaring them once here keeps
    // a single version on the shared buildscript classpath
    id("net.neoforged.moddev") version "2.0.148" apply false
    id("net.neoforged.moddev.legacyforge") version "2.0.148" apply false
}

stonecutter active "1.21.1-neoforge"

stonecutter parameters {
    val (version, loader) = current.project.split('-', limit = 2)

    // Lets `[forge."1.20.1"]`-style sections in stonecutter.properties.toml apply
    properties {
        tags(version, loader)
    }

    // Enables `//? if forge {` / `//? if neoforge {` in sources
    constants {
        match(loader, "forge", "neoforge")
    }
}
