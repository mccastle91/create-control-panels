# Architecture

## Build layout: one tree, two Minecraft versions (Stonecutter)

```
src/main/                  all code + shared assets, shown as the active version (1.21.1)
src/versioned/<mc>/        mod metadata and data files that differ per version
build.forge.gradle.kts     1.20.1 target: Forge 47, ModDevGradle legacy, Java 17
build.neoforge.gradle.kts  1.21.1 target: NeoForge 21.1, ModDevGradle, Java 21
versions/<target>/build/   per-target outputs (Stonecutter generates each target's source view here)
```

**Why Stonecutter over separate branches?** Control Engineering keeps 1.20.1 and 1.21.1 as long-lived branches; they've drifted by ~4,400 lines and its 1.21.1 port is unfinished. Stonecutter keeps one tree and marks only the differences with comments, so every fix lands in both builds at once.

**Where the differences live.** Create's APIs used here are identical on both versions, so the Create binding needs no branching. Vanilla differences (item-stack serialization, item custom data, dye colours, reach checks, `ResourceLocation`) go through `util/McCompat`; client rendering differences through `client/ClientCompat`. Loader differences (entry point, config, networking, client events, game-test annotations) are `//? if neoforge {` / `else` blocks in the handful of files that touch the loader; networking sits behind `network/CCNetwork`.

### Rules

1. Shared code must compile at Java 17 — the 1.20.1 build enforces this.
2. Shared code may only use Create APIs present in 6.0.8 — the 1.20.1 build compiles against 6.0.8, so it enforces this too.
3. Every direct Create API call stays behind a small internal interface (see frequency binding below), so a future Fabric target could bind it differently.
4. Registration uses Registrate (same library Create uses), so blocks/items inherit Create-style conveniences (lang merging, creative tabs).
5. Unit tests run on the 1.21.1 target inside FML (`gradlew :1.21.1-neoforge:test`); game tests run on both targets.

## Data model

```
PanelBlockEntity                – one console: ConsoleShape (angle, front height), ConsoleCasing, PanelLayout
 └─ PanelLayout                 – components on the 16×16 px face (design area 1..15 px); capped by config
     └─ PanelElement (abstract) – UUID id, float px position on a 0.5 px grid, FrequencyPair; size fixed by type (labels, bar meters and readouts set their own width)
         ├─ transmitters        – ButtonElement (button, large_button; pulseTicks), LatchingButtonElement,
         │                        SwitchElement, CoveredSwitchElement (closed/open/armed),
         │                        AnalogElement → KnobElement, SliderElement (value 0-15)
         ├─ receivers           – ReceiverElement → LampElement (lamp, large_lamp), NeedleMeterElement,
         │                        BarMeterElement, NixieElement
         ├─ readouts            – ReadoutElement (line per row, size) → NixieStripElement (tubes),
         │                        DisplayBoardElement (two rows, width)
         └─ passive             – LabelElement (text, colour, scale, alignment, width)
```

- Component types live in a string-keyed registry (`PanelElementType`, populated by `PanelElements`), each with a footprint and height (labels and bar meters override the width per element); unknown types are skipped on load with a warning. Layouts saved by v1 (cell grid, `Version` < 2) are dropped rather than misplaced.
- `FrequencyPair` = two item stacks (either optional, normalized to count 1), Create's frequency metaphor.
- Persistent state = design + latching component state; received strengths are runtime-only and resync from the link network via a `Runtime` list piggybacked on the update tag.
- Display Link text belongs to the console, by line number (`PanelBlockEntity.displayLine`), not to readouts, so readouts can mirror a line and re-pointing one takes effect at once. `ConsoleDisplayTarget` (registered in Create's display target registry and bound to the block in common setup) writes it: Link line 0 is console line 1, and the console offers lines up to the highest a readout shows. Like received strengths it is runtime-only, riding the update tag as component JSON so clients translate it; it is never saved, so blueprints and dropped consoles carry no old readings, and links resend it within seconds of a load.
- Server-authoritative: designer edits arrive as NBT ops (`PanelEditOps`: add, move, delete, set_frequency, set_option, adjust, set_shape) and `markLayoutChanged()` persists and pushes the whole console to clients.
- Costs: `ComponentCosts` maps each type to cost lines, each an item id or an `#item tag` (an unknown item id costs nothing). `CostPayer` charges the player's inventory when a part is mounted and returns the exact stacks taken, which the element keeps (`PanelElement.paid()`, saved as `Paid`) and refunds on removal; creative pays nothing, so creative parts refund nothing. Blueprint pastes go through `ComponentCosts.settle`, which reuses the old parts' paid items, charges the shortfall and refunds the rest.
- Unit tests in `src/test` run inside FML on the 1.21.1 target: `gradlew :1.21.1-neoforge:test`.

## Frequency binding (Phase 4, implemented — verified against Create 6.0.10 source)

We do **not** invent a signal system; we ride Create's Redstone Link network:

- Create's model (from `RedstoneLinkNetworkHandler` source): per-`LevelAccessor` map of `Couple<Frequency>` → set of `IRedstoneLinkable`; `Frequency.of(ItemStack)` keys by item + dyed color; strongest live in-range transmitter wins (`updateNetworkOf`); range is the `linkRange` server config (default 256).
- Common seam ([link/LinkPort.java](src/main/java/dev/mccastle91/createcontrolling/link/LinkPort.java), [link/LinkPlatform.java](src/main/java/dev/mccastle91/createcontrolling/link/LinkPlatform.java)): `LinkPlatform.createPort(level, pos, frequency, listening, transmittedStrength, receivedCallback, alive)` returns a `LinkPort` (`notifyStrengthChanged()` / `remove()`). No Create types cross this seam.
- Create binding ([CreateLinkPlatform.java](src/main/java/dev/mccastle91/createcontrolling/compat/create/CreateLinkPlatform.java)): one `IRedstoneLinkable` per panel *element* (not `LinkBehaviour`, which assumes one link per BlockEntity), registered with `Create.REDSTONE_LINK_NETWORK_HANDLER`, mirroring `LinkBehaviour`'s lifecycle: add on port create, remove on unload/delete/re-key, `updateNetworkOf` on value change. Fresh receivers self-compute their initial strength (the handler's push loop excludes the actor itself).
- Lifecycle hooks: `onLoad`/`onChunkUnloaded` are NeoForge patches, on NeoForge and Forge alike, so `CreatePanelBlockEntity` subclasses the plain BE to call rebuild/unload (it also supplies the goggles overlay and the body model's data); the common BE also lazily initializes ports on first server tick as a fallback and removes them in `setRemoved`.
- Designer frequency slots: slot 1 red, slot 2 blue — the two marker colours on Create's `redstone_bridge` texture (the first-frequency pad uses the red one). With JEI present, [compat/jei](src/main/java/dev/mccastle91/createcontrolling/compat/jei/) registers the designer as a plain-screen GUI (so JEI's list shows beside it; while the frequency chooser is open the designer hides and reports only the chooser's bounds, so the list fits at any GUI scale) and a ghost-ingredient handler whose only targets are the two frequency slots (the chooser's while it is open), dropping a dragged item through the same `set_frequency` op. JEI is an optional, client-side dependency compiled against its API only; nothing outside `compat/jei` references it, and its `IGuiProperties` method names differ per version (a Stonecutter branch).
- Component semantics: buttons pulse 15 for `pulseTicks` (server-ticked countdown), latching buttons and switches latch 0/15, the covered switch sends 15 only when armed, knobs and sliders hold 0–15; receivers take pushes into transient state synced to clients.

### Verified by GameTests (`gradlew :1.21.1-neoforge:runGametest`, `gradlew :1.20.1-forge:runGametest`)

Real Create Redstone Link blocks + our panel in a live server: button pulse powers a Create receiver then expires; knob analog 11 arrives as exactly 11; a Create transmitter's analog 7 lights our lamp at exactly 7; a player's click ray on a 15° sloped console presses the button under it; real Display Links write through the target Create resolves for a console, filling lines, clearing lines a source stops filling, and stopping at a line another link owns. Hard-won test infrastructure notes:

1. `@GameTestHolder(MOD_ID)` sets the namespace the `neoforge.enabledGameTestNamespaces` filter matches — the `template` string's namespace does NOT count.
2. Place Create links in transmitter state and `toggleMode()` to receiver afterwards: freshly placed `SmartBlockEntity`s only attach their deferred `LinkBehaviour` via the first-tick mode-mismatch swap; `RECEIVER=true` from the start is a state gameplay never produces (behaviour never attaches).
3. Tests share one world and link range spans all test structures — every test needs a unique frequency pair or the strongest transmitter leaks into neighboring tests.
4. Face-attached blocks need an in-test floor; don't rely on the structure template for support blocks.

## Console block

One block, `PanelBlock` (face-attached like a lever: floor, wall, ceiling × four facings), plus a block entity. Everything spatial derives from one mapping, `ConsoleOrientation`: model space is a floor console facing north (player standing south), and each of the 12 placements is a rotation of it. `PanelFaceGeometry` describes the sloped face in that space (origin at design (0, 0), `u` right, `v` down the design, outward normal), so the body mesh, the component renderer, the click ray (`ConsolePicker`), the voxel shapes (`ConsoleShapes`) and the highlight (`ConsoleOutline`, which draws the aimed component or the true wedge instead of the stepped selection shape) agree by construction. Where a casing has Create connected textures, the walls of joined consoles use them (`ConsoleMesh`).

- **Shape:** angle, front (hinge) height and which model edge is raised, stored on the block entity and normalised to what the placement allows (`ConsoleShape.forFace`): floor consoles rise away from the player; ceiling consoles hang their far edge down; wall consoles start flat and may raise either their top or their bottom edge. The ceiling's design frame is turned 180° from the floor's so the face reads upright from below, which puts its hinge at model z = 0 — so a ceiling slope is "reversed" in model space while looking the same as before in the world. A console item keeps its old shape, but a console placed on a wall always starts flat (`PanelBlock.setPlacedBy`). The sneak-wrench steps through the 0/15/30/45° presets (walls: top out, then bottom out, then flat); the designer sets any angle and, on walls, the raised edge.
- **Casing:** right-click with a Create casing (andesite, brass, copper, railway, industrial iron) to re-dress the console; the old casing comes back.
- **Connections:** the blockstate records which edges meet an identical neighbour (same placement, shape and casing; sloped consoles only join side by side). Joined edges lose their frame so a row of consoles reads as one surface. Changes to shape or casing refresh the 3×3×3 neighbourhood.
- **Drops** keep the design (block-entity data on the item, restored on placement).
- **Rendering:** see [docs/v2-design.md](docs/v2-design.md#rendering) — a bake-time `ConsoleBakedModel` for the body (chunk-lit, so it shades correctly at any angle) and a block-entity renderer for the 3D components.

## Interaction model

- **Play** (right-click): the player's eye ray, transformed into face space, is intersected with each component's 3D box; the nearest hit handles the click at the exact point. A click steps a knob or slider up one; a sneak-click steps it down one (`PanelElement.takesSneakClicks` lets it claim the sneak-click before the designer does). Sneak + scroll also nudges it by one.
- **Edit** (sneak + empty hand, not on a knob or slider): opens the Console Designer. Every change is sent as an edit op and the screen redraws from the synced block entity.
- **Blueprint**: use an empty one to copy a design, a filled one to paste (sneak-use re-copies).
