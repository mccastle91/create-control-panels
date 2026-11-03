# Create Controlling

Buildable, customizable **control consoles for [Create](https://github.com/Creators-of-Create/Create)** — place a sloped console, mount 3D buttons, toggle and guarded switches, knobs, sliders, lamps, needle gauges, nixie readouts and labels on it, and bind each component to Create's **Redstone Link** frequencies. Inspired by the "Control Engineering" addon for Immersive Engineering, re-imagined natively for Create: Control Engineering's geometry and feel, Create's materials and textures.

## Status

v1 (phases 1–8 below) was the flat-panel version. **v2** redesigns it towards Control Engineering's look and feel — sloped console bodies with adjustable angles, chunky 3D components you click directly, Create casings, and material costs instead of panel tiers — and builds for both Minecraft 1.20.1 and 1.21.1 from one source tree. The v2 redesign is implemented and tested on both versions; it is awaiting an in-game visual review (see [docs/v2-design.md](docs/v2-design.md)).

| Phase | Goal | Status |
|---|---|---|
| 1 | Gradle scaffold, deps resolve, loads with Create | ✅ |
| 2 | Panel block + BlockEntity, empty layout NBT, Create-styled model | ✅ |
| 3 | Element data model, NBT serialization, client sync | ✅ |
| 4 | Redstone Link binding (send + receive, analog) | ✅ |
| 5 | Panel Designer GUI (edit mode) + play-mode interaction | ✅ |
| 6 | Full v1 element set with per-type options | ✅ |
| 7 | Design copy/paste, config, aesthetics pass | ✅ |
| 8 | Ponder scenes, JEI/EMI integration | ✅ |
| v2.0 | One source tree building for 1.20.1 (Forge) and 1.21.1 (NeoForge) | ✅ |
| v2.1 | Sloped consoles (0–45°), casings, 14 component types with costs, 3D rendering, designer v2 | ✅ |
| v2.2 | In-game review, animations, polish | ⬜ |
| — | Fabric target | parked |

## Versions

| | 1.20.1 | 1.21.1 |
|---|---|---|
| Loader | Forge 47.4.28 | NeoForge 21.1.265 |
| Create (players) | 6.0.8 or newer | 6.0.8 or newer |
| Create (built/tested against) | 6.0.8 | 6.0.10 |
| Ponder / Flywheel / Registrate | 1.0.91 / 1.0.5 / MC1.20-1.3.3 | 1.0.82 / 1.0.6 / MC1.21-1.3.0+67 |
| Java | 17 | 21 |

6.0.8 is the newest Create release published for both Minecraft versions, so it's the shared minimum. The companion versions match what each Create release bundles. All versions live in [stonecutter.properties.toml](stonecutter.properties.toml).

## Building & running

Gradle needs a JDK 17+ to run (`JAVA_HOME`); the per-version JDKs (17 and 21) are provisioned automatically through toolchains.

```
./gradlew build                          # both jars, plus unit tests on 1.21.1
./gradlew :1.21.1-neoforge:runClient     # dev client, 1.21.1
./gradlew :1.20.1-forge:runClient        # dev client, 1.20.1
./gradlew :1.21.1-neoforge:runGametest   # in-world integration tests (also :1.20.1-forge:)
```

Jars land in `versions/<version>/build/libs/`. The 1.20.1 jar there is the reobfuscated one to ship; dev runs keep their worlds under `run/<version>/`.

## Playing

- **Craft** a Control Console (three iron sheets over a transmitter, andesite casing and Redstone Link). Place it on a floor, wall or ceiling like a lever.
- **Sneak-use with an empty hand** to open the Console Designer: add components (each costs parts — hover a palette button to see what), drag them into place, give them a Redstone Link frequency, and set the console's angle.
- **Use** a component to operate it; **sneak-scroll** over a knob or slider to fine-tune it.
- **Sneak-wrench** a console to step through the 0/15/30/45° angles. Floor and ceiling consoles slope one way, towards you; wall consoles start flat and can tilt either way — the wrench runs through the angles with the top edge standing out, then with the bottom edge out (in the designer, the *Top out / Bottom out* button swaps them). **Right-click with a Create casing** (Andesite, Brass, Copper or Train Casing, or a Block of Industrial Iron) to re-dress it.
- Breaking a console keeps its design; a **Console Blueprint** copies a design onto other consoles, charging only for parts the target lacks.

## Layout

One source tree, built twice with [Stonecutter](https://stonecutter.kikugie.dev/):

- `src/main/` — all code and shared assets. The few places where the two versions differ are marked with Stonecutter comments (`//? if neoforge {` … `//?} else {` … `//?}`); most vanilla differences are funnelled through `util/McCompat` and `client/ClientCompat`.
- `src/versioned/<mc>/resources/` — what can't carry comments: mod metadata (`mods.toml` vs `neoforge.mods.toml`) and data files (1.20.1 uses plural folder names and an older recipe format).
- `build.forge.gradle.kts` / `build.neoforge.gradle.kts` — one build script per loader (ModDevGradle legacy for Forge, ModDevGradle for NeoForge).
- `stonecutter.gradle.kts` — sets the version the `src/` tree currently shows (`stonecutter active`), which is 1.21.1.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the data model and how frequency binding hooks into Create.
