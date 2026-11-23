# Create Controlling

Buildable, customizable **control consoles for [Create](https://github.com/Creators-of-Create/Create)** — place a sloped console, mount 3D buttons, toggle and guarded switches, knobs, sliders, lamps, needle gauges, nixie readouts, display boards and labels on it, bind each component to Create's **Redstone Link** frequencies, and show live text from Create's **Display Links**. Inspired by the "Control Engineering" addon for Immersive Engineering, re-imagined natively for Create: Control Engineering's geometry and feel, Create's materials and textures.

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
| JEI (optional) | 15.x (built against 15.74.0.234) | 19.x (built against 19.70.0.469) |
| CC: Tweaked (optional) | built against 1.121.0 | built against 1.121.0 |
| Drive-By-Sable (optional) | not available | built against r2.1.0 |
| Java | 17 | 21 |

6.0.8 is the newest Create release published for both Minecraft versions, so it's the shared minimum. The companion versions match what each Create release bundles. All versions live in [stonecutter.properties.toml](stonecutter.properties.toml).

## Building & running

Gradle needs a JDK 17+ to run (`JAVA_HOME`); the per-version JDKs (17 and 21) are provisioned automatically through toolchains.

```
./gradlew build                          # both jars, plus unit tests on 1.21.1
./gradlew :1.21.1-neoforge:runClient     # dev client, 1.21.1
./gradlew :1.20.1-forge:runClient        # dev client, 1.20.1
./gradlew :1.21.1-neoforge:runGametest   # in-world integration tests (also :1.20.1-forge:)
./gradlew :1.21.1-neoforge:runGametest -Pcables   # the same with Drive-By-Sable, Sable and Create Aeronautics loaded (also runClient)
```

Jars land in `versions/<version>/build/libs/`. The 1.20.1 jar there is the reobfuscated one to ship; dev runs keep their worlds under `run/<version>/`.

## Playing

- **Craft** a Control Console (three iron sheets over a transmitter, andesite casing and Redstone Link). Place it on a floor, wall or ceiling like a lever.
- **Copycat Control Console**: the same console, crafted with a Copycat Panel in place of the andesite casing. Right-click it with a block and its body takes that block's look — any block Create's own copycats accept. The block's texture lies as it does on the block itself (a log keeps its grain, the top keeps its direction), connected textures such as Create's casings and framed glass connect across joined consoles, and a block that gives off light (glowstone, a lit lamp) makes the console glow the same. Right-click with the same block again to turn it (a log's axis), with a different block to swap it (the old one comes back), or press **Take Block** in the designer (with no component selected) to go back to bare zinc. The face plate stays the standard one, and the trim of the parts stays zinc whatever the block. The block travels with the console when it is picked up or broken. Copycat consoles join each other only when they copy the same block.
- **Sneak-use with an empty hand** to open the Console Designer: add components (each costs parts — hover a palette button to see what, as "have/need": red when you hold none of an item, yellow when you hold some, green when you hold enough), drag them into place, give them a Redstone Link frequency, and set the console's angle. The two frequency slots are coloured like the red and blue markers on Create's Redstone Link; click either one to open the frequency chooser — both slots above your inventory — and drag an item from your inventory onto a slot (nothing is used up); right-click a slot to clear it. With JEI installed, its item list shows beside the chooser and you can drag any item from it onto a slot too. **Color** (and a label's **Text color**) opens a grid of the 16 dye colours; click one to apply it. The designer is one window in the shape of Create's own menus, coloured after the console's casing (brass is Create's gold; the others take their casing's tones), with the casing's real texture around the face. The footer bar holds the console's shape — front height and angle — and a tick that closes the window; changes apply as you make them. Where the screen has room, the console itself is shown beside the window and changes as you edit.
- **Use** a component to operate it; using a knob or slider steps it up one, **sneak-use** steps it down one (**sneak-scroll** also nudges it). Sneak-use anywhere else opens the designer.
- Parts make a **sound** when worked — buttons click down and back up, levers snap, guards clack, knobs and sliders tick — heard by everyone near the console, whether a hand or a computer worked them.
- A **guarded switch** works in two parts: use it to lift the guard, then use the lever to switch on or off as often as you like, and use the raised lid to close the guard again. **Sneak-use** works the guard from anywhere on it. Closing the guard switches off, unless the switch is set to **Closing: keeps state** in the designer, which lets you lock it on under the guard.
- The **analog lever** is Create's own in miniature: its long handle leans from pulled back (0) to pushed forward (15) and a small lamp on its base glows brighter the further it goes. It works like a knob or slider — use steps it up, sneak-use down, hold and drag up or down to set it — and takes the same Min and Max.
- A knob or slider can be **limited** to part of its range: in the designer, the **Min** and **Max** rows step its lowest and highest value with - and +. They can meet but not pass each other. A limited slider shows a red line where its handle stops, and a limited knob a red tick; clicks, dragging and computers all stop there.
- **Hold use** on a knob or slider and drag the mouse to set it without sneaking: right raises it (up, on a vertical slider), one click per step; the mouse wheel works too while you hold. Let go to finish. With `analogPopup = true` in `config/create_controlling-client.toml`, holding opens a small window showing the knob or slider to drag instead, which closes when you let go.
- **Use a Create Clipboard** on a component to copy its frequency, and **punch** a component with it to paste — the same clipboard copies to and from Create's own Redstone Links.
- **Wrench** a console to step through the 0/15/30/45° angles; **sneak-wrench** it to pick it up into your inventory, design included. Floor and ceiling consoles slope one way, towards you; wall consoles start flat and can tilt either way — the wrench runs through the angles with the top edge standing out, then with the bottom edge out (in the designer, the *Top out / Bottom out* button swaps them). **Right-click with a Create casing** (Andesite, Brass, Copper or Train Casing, or a Block of Industrial Iron or Weathered Iron) to re-dress it. The metal trim of the parts on it follows the casing: andesite, brass, copper, brass again on Train Casing, and industrial or weathered iron.
- **Show live text** with a Display Link: mount a **Nixie Strip** (2–5 tubes, one line) or a **Display Board** (always two rows, each with its own line), and set which line each uses in the designer. Right-click the console with a Display Link, place the link on any Create display source, then open the link and pick that line. Anything a Display Link can read works, including sources from other mods. Tubes show one character each: numbers lose decimals, then their unit, to fit, and show dashes if they still can't. Long board text shrinks, then is cut off.
- **Wire it to a computer** (optional, needs [CC: Tweaked](https://tweaked.cc/)): see [Computers](#computers-cc-tweaked) below.
- **Wire it with cables** (optional, needs [Drive-By-Sable](https://modrinth.com/mod/drive-by-sable), 1.21.1 only): see [Cables](#cables-drive-by-sable) below.
- **Labels** never draw past their own width. Turn on **Wrap** in the designer to let one run onto up to three lines; the text box stops taking characters once the label is full. **Bar meters** come as two parts, like sliders: a horizontal one filling left to right and a vertical one filling from the bottom.
- **Lamps can be inverted**: select a Lamp or Big Lamp in the designer and switch **Lit: with signal** to **Lit: without signal**. It is then fully lit with no signal, dimmer the stronger the signal, and dark at full strength.
- **Right-click a console with a dye** to paint its face plate that colour. One dye is used up. Gray dye puts the standard plate back, and it works on the Copycat Console too. The colour stays with the console when it is broken or picked up.
- Breaking a console keeps its design.
- A **Console Blueprint** holds a design and is a designer of its own:
  - **Use it** in the air to open the designer on the blueprint itself. Drawing there is free, and needs no console. The right-hand column lists the materials the drawing would cost, coloured like the palette, and **Clear** in the footer wipes it.
  - **Click a console** with an empty blueprint to copy that console's design. A blueprint that holds a design is never overwritten this way; clear it first.
  - **Sneak-click a console** to build the design there. The console's parts are swapped for the design's: you pay only for parts the console lacks and get back the ones it no longer needs. A plain click builds nothing; it tells you what you are still missing.
  - Hold Shift over the item to see its material list. The icon shows a drawn console once the blueprint holds a design.

## Computers (CC: Tweaked)

With CC: Tweaked installed, a console next to a computer (or on a wired modem) is a `control_console` peripheral. The computer sees every component that has a Redstone Link role, numbered from 1 in the order they were mounted (labels and Display Link readouts are skipped).

**Frequencies are optional.** A component with a frequency travels over Create's link network as usual, so what a computer sets on it is seen by every Redstone Link on that frequency. A component without one belongs to the computer alone: it can still be read and set, and nothing reaches the network.

**Joined consoles.** Consoles placed side by side join into one surface, and a computer touching any of them reaches all of them. Every method takes an optional last `console` argument that picks one:

- nothing: the console the computer touches;
- a string: the console with that **name** (set in the designer: open it with nothing selected and fill in *Console Name*);
- a table with `x`, `y`, `z`: the console at that position. Any entry from `getConsoles()`, or the console table of an event, works as-is.

| Call | What it does |
|---|---|
| `getConsoles()` | The touched console and every console joined to it (the touched one first): `name` (absent if unnamed), `x` / `y` / `z`, `attached` (true for the touched one) and `components` (how many). |
| `getComponents([console])` | A list of tables: `index`, `type` (`button`, `toggle_switch`, `lamp`, ...), `direction` (`"transmit"` for controls, `"receive"` for lamps and meters), `linked` (whether it has a frequency), `value` (0-15), `min` and `max` (knobs, sliders and analog levers only: the range they are limited to), `inverted` (lamps only: whether the lamp is lit without a signal; `value` is the signal either way), `frequency` (`first` / `second` item ids; a blank slot is absent), and `x` / `y` on the face in pixels. |
| `getValue(index[, console])` | The strength (0-15) a control is sending, or a lamp or meter shows. |
| `setValue(index, value[, console])` | On a control: operates it so it sends `value`. Switches and latching buttons take anything above 0 as on, a guarded switch lifts its guard and switches on (0 switches it off and closes the guard), a knob, slider or analog lever moves to `value` (or as near as its Min and Max allow), and a momentary button gives one pulse. On a linked lamp or meter, the console sends `value` on that component's frequency. On an unlinked one, the part just shows `value`. Either lasts until you set 0, or until the last computer on the joined consoles shuts down or is disconnected. |
| `setFrequency(index, first, second[, console])` | Links a component to the frequency of two item ids (for example `"minecraft:redstone"`), as the designer does, and nothing is used up. Pass `nil` for a blank slot; two `nil`s unlink it. |

Whenever a component's signal changes on any of the joined consoles, attached computers get a `console_signal` event with the peripheral's name, the component's index, the new value and the console it is on (a table with `name`, if it has one, and `x` / `y` / `z`).

```lua
local console = peripheral.find("control_console")
for _, c in ipairs(console.getConsoles()) do
  print(c.name or "(unnamed)", c.x, c.y, c.z, c.components)
end

console.setValue(2, 15)                 -- light the lamp that is component 2 of the touched console
console.setValue(1, 15, "Reactor")      -- and component 1 of the console named Reactor
while true do
  local _, name, index, value, source = os.pullEvent("console_signal")
  if source.name == "Reactor" and index == 3 then   -- a switch on Reactor
    console.setValue(2, value)
  end
end
```

Editing a console in the designer can renumber its components; call `getComponents()` again after changing a design. If a computer touches two consoles of the same group (or several are on its modems), each one is its own peripheral and each reports the group's events.

## Cables (Drive-By-Sable)

With [Drive-By-Sable](https://modrinth.com/mod/drive-by-sable) installed (NeoForge 1.21.1; it needs Sable), every control, lamp, meter and nixie readout on a console is a cable endpoint of its own. No frequency is needed.

- Hold Drive-By-Sable's **Cable**, aim at a **control** (button, switch, knob, slider, lever) and use it to pick it as the source, then use the block face, or the lamp or meter on any console, that it should drive. The cable carries whatever the control sends, 0–15.
- A **lamp, meter or nixie readout** takes a cable the same way, from a console control or from anything else Drive-By-Sable can use as a source (a lever, a sensor, a controller hub).
- A part that also has a frequency keeps using it: a control sends on both, and a lamp or meter shows whichever is stronger, the cable or the frequency.
- While you hold the Cable or the Cable Cutter, clicking a part only wires it: knobs, sliders and the rest don't move, and you don't need to sneak.
- The cable has to be aimed at a part; the bare face of a console takes none. Each part shows as its own channel, named after the part with a short code, such as `Knob 3FA2`. The code stays with the part however the console is edited.
- Removing a part in the designer drops its cables (the cable items are not returned). Breaking the console is handled by Drive-By-Sable like any other block.

This is built against Drive-By-Sable r2.1.0. It uses that mod's internal classes, since it publishes no API, so a later Drive-By-Sable update may need a matching update here. The original Drive-By-Wire and its own Sable port are not supported.

## Layout

One source tree, built twice with [Stonecutter](https://stonecutter.kikugie.dev/):

- `src/main/` — all code and shared assets. The few places where the two versions differ are marked with Stonecutter comments (`//? if neoforge {` … `//?} else {` … `//?}`); most vanilla differences are funnelled through `util/McCompat` and `client/ClientCompat`.
- `src/versioned/<mc>/resources/` — what can't carry comments: mod metadata (`mods.toml` vs `neoforge.mods.toml`) and data files (1.20.1 uses plural folder names and an older recipe format).
- `build.forge.gradle.kts` / `build.neoforge.gradle.kts` — one build script per loader (ModDevGradle legacy for Forge, ModDevGradle for NeoForge).
- `stonecutter.gradle.kts` — sets the version the `src/` tree currently shows (`stonecutter active`), which is 1.21.1.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the data model and how frequency binding hooks into Create.
