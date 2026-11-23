# Add-on API

For mods that add their own components to consoles, or that read and work the consoles in a world. API version 1, first shipped in Create Controlling 0.2.8.

## What is stable

- The packages `dev.mccastle91.createcontrolling.api` and `api.client`.
- The classes they hand out, to the extent listed in the `ConsoleApi` class comment: `PanelElement` and its `ReceiverElement`, `AnalogElement` and `ColorableElement` relatives, `PanelElementType`, `ComponentCosts.Cost`, `PartSound`, `ComponentPainter`, and a handful of methods on `PanelBlockEntity`.

Anything else can change between versions. `ConsoleApi.VERSION` goes up when something here changes in a way an add-on could notice.

## Setting up

Depend on the mod's jar at compile time (CurseMaven or a local file both work) and declare `create_controlling` as a required dependency ordered before your mod. There is no separate API jar.

## A component, start to finish

A component is a subclass of `PanelElement` and a type registered for it. This one is a key switch: it sends a signal while on, and the designer can set whether it springs back.

```java
public class KeySwitch extends PanelElement {
    private boolean on;
    private boolean springs;

    public KeySwitch(PanelElementType<KeySwitch> type) { super(type); }

    // it sends on its frequency; it doesn't read one
    @Override public boolean isTransmitter() { return true; }
    @Override public boolean isReceiver() { return false; }
    @Override public int transmittedStrength() { return on ? 15 : 0; }

    // a click, on the server; true means the state changed
    @Override public boolean click(float localX, float localY, boolean sneaking) {
        on = !on;
        sound(PartSound.lever(on));
        return true;
    }

    // what a computer's setValue() does
    @Override public boolean operate(int strength) {
        if (on == strength > 0) return false;
        on = !on;
        return true;
    }

    // survives a restart while it is on
    @Override public boolean hasPersistentState() { return true; }

    // what the designer shows when it is selected
    @Override public List<PartOption> options() {
        return List.of(PartOption.toggle("springs",
                Component.translatable("gui.mymod.springs"), springs));
    }
    @Override public boolean setOption(String key, int value) {
        if (!key.equals("springs")) return false;
        springs = value != 0;
        return true;
    }

    @Override protected void saveOptions(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("On", on);
        tag.putBoolean("Springs", springs);
    }
    @Override protected void loadOptions(CompoundTag tag, HolderLookup.Provider registries) {
        on = tag.getBoolean("On");
        springs = tag.getBoolean("Springs");
    }

    public boolean isOn() { return on; }
}
```

Register it during mod construction, on both sides:

```java
public static final PanelElementType<KeySwitch> KEY_SWITCH =
        ConsoleApi.registerComponent("mymod:key_switch", KeySwitch::new, 3, 3, 1.5f);

static {
    ConsoleApi.setCost(KEY_SWITCH, "minecraft:lever", 1, "#minecraft:wooden_buttons", 1);
}
```

The three numbers are the footprint across and down the 16 px face and how far the part stands off it. The id is saved with every such part, so it must never change.

On the client, during client setup, say how it is drawn:

```java
ConsoleClientApi.registerRenderer(MyMod.KEY_SWITCH, new ComponentRenderer<KeySwitch>() {
    @Override public void render(KeySwitch part, PartRenderContext c) {
        ComponentPainter p = c.painter();
        // a trim-metal base, 0.5 px tall, over the whole footprint
        p.box(0, 0, 0, part.width(), part.depth(), 0.5f, c.trim(), 0xFFFFFFFF, c.light());
        // a barrel that turns a quarter when on
        p.push();
        p.translate(part.width() / 2, part.depth() / 2, 0.5f);
        p.rotateZ(part.isOn() ? 90 : 0);
        p.box(-0.3f, -1, 0, 0.3f, 1, 1, c.plain(), 0xFF202020, c.light());
        p.pop();
    }

    @Override public int designerColor(KeySwitch part) {
        return part.isOn() ? 0xFFB08850 : 0xFF6E5A3C;
    }
});
```

Coordinates are face pixels from the part's far-left corner: x right, y towards the player, z out of the face. A part with no renderer shows as a flat grey plate, so you can test the server side first.

Add three translations:

```json
"gui.create_controlling.add.mymod.key_switch": "Key",
"gui.create_controlling.element.mymod.key_switch": "Key Switch",
"gui.mymod.springs": "Springs back"
```

The first is the palette button (58 px wide, so keep it short); the second is the part's name everywhere else.

## What you get without doing anything

- The palette button, with the cost in its tooltip. Past 20 component types the palette turns pages.
- Placing, dragging, selecting and removing in the designer; the cost charged and refunded; blueprints.
- Frequency slots, the click ray, the outline under the crosshair (`pickBoxes()` if the part has more than one click zone).
- Saving, syncing to clients, the dropped console keeping its design.
- CC: Tweaked: the part is listed by `getComponents()` under its id, with whatever `describe()` adds, and works with `getValue`/`setValue`.
- Drive-By-Sable: a transmitter is a cable source and a receiver a cable sink.

## Options

`options()` is asked every frame while the part is selected, so build it from the part's current state. Up to four rows are shown.

- `PartOption.toggle(key, label, on)` is one button that reads `label` and is green while on.
- `PartOption.stepper(key, label, value, min, max, step)` is `label` between a - and a + button. Put the value in the label ("Delay: 5 t").

A click sends the new value to the server, which calls `setOption(key, value)`. Clamp it there. A part that implements `ColorableElement` also gets the Color button.

## Reading and working consoles

```java
ConsoleApi.console(level, pos).ifPresent(console -> {
    for (PanelElement part : ConsoleApi.components(console)) {
        int signal = ConsoleApi.signal(part);          // 0-15, sent or shown
        ConsoleApi.setSignal(console, part, 15);       // server only
    }
});

ConsoleApi.addSignalListener((console, part, strength) -> { /* server only */ });
```

`setSignal` works a control the way a hand would; on a lamp or meter it makes the console send on that part's frequency, or show the value directly if the part has none. `ConsoleApi.release(console)` ends that.

## Not in version 1

- Typed-text options and options with their own widgets.
- New casings and designer themes.
- A dial window for add-on analog parts (hold-and-drag works; the optional popup window draws only the mod's own knobs, sliders and levers).
- Costs from data packs.
