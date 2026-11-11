# Create Controlling — notes for Claude Code

A Create addon (mod id `create_controlling`, package `dev.mccastle91.createcontrolling`): buildable, sloped control consoles carrying 3D components (buttons, switches, knobs, sliders, lamps, gauges, nixie readouts, labels) bound to Create's Redstone Link frequencies. Inspired by Control Engineering (Immersive Engineering addon), rebuilt natively for Create. Read [README.md](README.md), [ARCHITECTURE.md](ARCHITECTURE.md) and [docs/v2-design.md](docs/v2-design.md) before changing anything structural.

## Environment

- Gradle needs a JDK 17+ to run; set `JAVA_HOME` to it first (on the original machine there was no system Java — a portable Temurin 21 lived in `~/.jdks`). The per-version JDKs (17 for 1.20.1, 21 for 1.21.1) are provisioned by Gradle toolchains.
- One source tree builds two targets with Stonecutter: `1.20.1-forge` (Forge 47, ModDevGradle legacy, Create 6.0.8) and `1.21.1-neoforge` (NeoForge 21.1, ModDevGradle, Create 6.0.10). The `src/` tree shows the active version, 1.21.1.

```
./gradlew build                          # both jars + unit tests (1.21.1)
./gradlew :1.21.1-neoforge:test          # unit tests (inside FML)
./gradlew :1.21.1-neoforge:runGametest   # in-world tests; also :1.20.1-forge:runGametest
./gradlew :1.21.1-neoforge:runClient     # dev client; also :1.20.1-forge:runClient
```

## Rules of the codebase

- Shared code must compile at Java 17 (no switch patterns) and use only Create APIs present in 6.0.8.
- Version/loader differences use Stonecutter comments (`//? if neoforge {` … `//?} else {` … `//?}`, `//? if >=1.21 {`); inactive blocks are `/* … */` comments, nested comments inside them must be `/^ … ^/`. Vanilla differences go through `util/McCompat`, client rendering ones through `client/ClientCompat`.
- Per-version data files (recipes, loot tables, mod metadata) live in `src/versioned/<mc>/resources/` — 1.20.1 uses plural folder names and `"item"` in recipe results, 1.21.1 singular folders and `"id"`.
- Never invent a parallel signal system — always ride Create's `RedstoneLinkNetworkHandler` (behind `link/LinkPlatform`). Verify Create APIs against Create 6 source, not older tutorials.
- Everything spatial on a console derives from `ConsoleOrientation` + `PanelFaceGeometry`; keep body mesh, renderer, click ray and outline going through them.

## Working agreement with the user

- Work like a senior mod developer, in verifiable stages; keep README.md and ARCHITECTURE.md current; commit logically.
- Ask one crisp question only when a structural choice is genuinely ambiguous; otherwise proceed.
- The user tests visuals in-game themselves; say clearly what was and wasn't verified.
- The Fabric target is parked — confirm with the user before starting it.

## Status (2026-11-11)

- `master`: the Control-Engineering-style v2 redesign, merged from `v2-redesign` on 2026-10-28 — one console block, 0–45° slope with presets, Create casings applied by right-click, 14 component types with material costs, generated body model, 3D component renderer, designer v2, blueprints that charge the difference. Unit tests and gametests pass on both versions.
- First in-game review round (2026-10-28) done: custom highlight outline, solid levers, connected casing walls, visible item, constant 1 px frame with centred design area, Create-style glass nixie tubes; recipe is iron sheets over transmitter + andesite casing + Redstone Link, makes 1.
- Release **0.2.1** (2026-10-29, tag `v0.2.1`): second in-game review round — wall consoles start flat and tilt either way (top or bottom edge out), ceiling faces read upright from below, double-sided nixie digits (end-on on walls), all-glass nixie tubes, guarded switch = toggle switch + flip-up lid on brass hinge posts, flush lamps, lower buttons/knobs/sliders, bar meters with their own length.
- Release **0.2.3** (2026-11-04, tag `v0.2.3`; 0.2.2 was never uploaded): consoles are Display Link targets; Nixie Strip and Display Board components choose their lines in the designer. The user reviewed the look in-game and is publishing to gather feedback on it. Compatibility with Power Grid, Factory Controller, Sable and Aeronautics is still untested.
- Release **0.2.4** (2026-11-11, tag `v0.2.4`) ships the next two items.
- Optional CC: Tweaked peripheral `control_console` (`compat/computercraft`) — computers list components, read and set their signals, and get `console_signal` events; see README "Computers". Automated checks pass on both versions with and without CC: Tweaked; the user has not yet tried it from an in-game computer. The user wants more optional mod compat after this one.
- Ponder rebuilt as three animated scenes (building, linking, Display Links) in `compat/ponder/CCPonderScenes`, with short texts in `en_us.json` (numbered in scene order — keep both in step). Schematics come from `GameTestStructureGenerator` (delete an `.nbt` under `assets/.../ponder` and run the unit tests to regenerate); consoles in them face south because Ponder's camera looks from the north. Scenes set part states directly — nothing runs the link network there. The user watched them in-game and approved them.
- **Next:** the user keeps reviewing in-game and reports what to tune. Then small animations (button press, lever swing — the user wants these kept simple) and polish. Deferred by the user: key switch / lock-and-key components.
- Decisions behind v2 (all from the user): one-block wedge console, no tiers, casing look by right-click, large button/lamp as costlier components, keep both needle and bar meters, nixie readout, sneak + empty hand opens the designer (fine adjust is sneak + scroll), old worlds need not carry over.
