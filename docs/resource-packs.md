# Restyling Create Controlling with a resource pack

Everything below lives under `assets/create_controlling/` in your pack. Nothing here needs a mod or a config change: drop the pack in, press F3+T, and the change shows.

## The designer window

The Console Designer, the frequency chooser and the dial window are built from small textures in `textures/gui/console/`. Each is stretched to whatever size is needed: its corners are drawn as they are, and the strips between them are stretched (or, where noted, repeated). The "kept" column is how many pixels on each side keep their size.

| File | Size | Kept (left, top, right, bottom) | What it is |
|---|---|---|---|
| `window_frame.png` | 16×16 | 2, 1, 2, 0 | The thin frame round the dark body. The middle is transparent. |
| `plaque.png` | 16×16 | 2, 2, 2, 2 | The frame of the small dial window. The middle is transparent. |
| `panel.png` | 16×16 | 3, 2, 3, 2 | The light grey footer bar and the colour picker's backing |
| `slot.png` | 16×16 | 1, 1, 1, 1 | Item slots and colour swatches |
| `field.png` | 16×14 | 1, 3, 1, 1 | The field behind typed text |
| `button.png` | 16×16 | 1, 1, 1, 1 | A text button |
| `button_hover.png` | 16×16 | 1, 1, 1, 1 | ...under the mouse |
| `button_green.png` | 16×16 | 1, 1, 1, 1 | ...switched on |
| `button_green_hover.png` | 16×16 | 1, 1, 1, 1 | ...switched on, under the mouse |
| `button_disabled.png` | 16×16 | 1, 1, 1, 1 | ...that can't be pressed |
| `slider_track.png` | 16×8 | 1, 0, 1, 0 | The angle slider's track. The middle repeats. |
| `slider_handle.png` | 16×14 | 1, 2, 1, 2 | The slider's handle. The middle repeats. |
| `slider_handle_hover.png` | 16×14 | 1, 2, 1, 2 | ...under the mouse or being dragged |

Each casing has a theme folder with the two textures that give its window its colour:

| File | Size | Kept | What it is |
|---|---|---|---|
| `<theme>/title.png` | 16×15 | 3, 2, 3, 2 | The title bar |
| `<theme>/body.png` | 16×16 | (tiles) | The dark background, repeated across the body |

The themes are `andesite`, `brass`, `copper`, `railway`, `industrial_iron`, `weathered_iron`, `copycat` (the Copycat Console) and `blueprint` (the designer opened on a Console Blueprint).

**One look for one casing.** Any file from the first table can also go in a theme folder, and that theme then uses it instead of the shared one. `textures/gui/console/brass/button.png` restyles the buttons on brass consoles only.

**Higher resolutions.** The sizes above are in screen pixels, not file pixels. A 64×64 `panel.png` is read as the same 16×16 cells, four times as sharp; keep the proportions and scale the kept borders with it.

The square icon buttons (the tick and the bin) and the inventory in the frequency chooser are Create's own textures, so a pack that restyles Create restyles those.

## Colours

Text and line colours can't come from a texture, so they are in one file: `console_style.json`, next to the `textures` folder. List only what you want to change. A colour is `"#RRGGBB"`, or `"#AARRGGBB"` where see-through makes sense.

```json
{
  "colors": {
    "nixie_glow": "#33CCFF",
    "grid_major": "#40FFFFFF"
  },
  "themes": {
    "brass": { "title_text": "#592424", "accent": "#E4B763" }
  }
}
```

Packs stack: a pack higher in the list overrides the keys it names and leaves the rest to the packs below it. A colour the game can't read is reported in the log and left as it was.

Each theme has two colours: `title_text` (the window title) and `accent` (section headings such as "Frequency").

### Screens

| Key | Default | What it colours |
|---|---|---|
| `text` | `#FFFFFF` | Text on the dark body |
| `text_hint` | `#DDDDDD` | The hint shown while nothing is selected |
| `text_dim` | `#A0A0A0` | "Nothing yet" in an empty material list |
| `panel_text` | `#3D3C48` | Text on the light grey footer and inventory |
| `button_text` | `#FFFFFF` | Button labels |
| `button_text_disabled` | `#A0A0A0` | ...on a button that can't be pressed |
| `slider_text` | `#442B15` | The angle on the slider's handle |
| `slider_notch` | `#8A6A3F` | The preset marks on the slider's track |
| `frequency_1`, `frequency_1_lit` | `#97333B`, `#C4505A` | The ring of the first frequency slot, and under the mouse |
| `frequency_2`, `frequency_2_lit` | `#5371C6`, `#7E98E0` | The second slot |
| `slot_hover` | `#80FFFFFF` | An inventory slot under the mouse |
| `slot_drop` | `#60FFFFFF` | A frequency slot an item is dragged over |

### The face in the designer

| Key | Default | What it colours |
|---|---|---|
| `face_border` | `#000000` | The line round the face |
| `face_shadow` | `#70000000` | The shadow where the plate sits below its frame |
| `grid_major`, `grid_minor` | `#30FFFFFF`, `#14FFFFFF` | Grid lines: every fourth, and the rest |
| `blueprint_frame`, `blueprint_paper` | `#284B85`, `#335F9E` | A blueprint's drafting paper: its edge, and where parts go |
| `part_outline` | `#2A2724` | The edge of every part's rectangle |
| `part_plain` | `#5A5A60` | Parts with no colour of their own |
| `part_gauge`, `part_gauge_active` | `#6E5A3C`, `#B08850` | Gauges and nixies: idle, and showing a signal |
| `part_board` | `#2B2B2D` | Display boards |
| `part_mark_dark`, `part_mark_light` | `#202020`, `#FFFFFF` | The marks on parts (a lever's line, a knob's bar) |
| `label_outline` | `#30FFFFFF` | The outline of a label's area |
| `selection` | `#FFFFFF` | The frame round the selected part |
| `drag_valid`, `drag_invalid` | `#60FFFFFF`, `#70FF4040` | Where a dragged part would land: free, or blocked |
| `swatch_ring`, `swatch_current` | `#FFFFFF`, `#15110C` | The colour picker: the ring under the mouse, and the mark on the current colour |

### Parts on the console

These change the parts in the world (and in the dial window).

| Key | Default | What it colours |
|---|---|---|
| `dial_face` | `#EDE4D4` | A needle gauge's face |
| `needle` | `#B3261E` | Gauge needles, and the red stop marks on limited knobs and sliders |
| `marking` | `#1C1C1F` | Tick marks, slider slots, an unlit bar meter |
| `pointer` | `#F0F0F0` | The line on a knob that shows where it points |
| `lever_lamp` | `#FF2A1A` | The small lamp on an analog lever |
| `actors_running` | `#3DDC5A` | The lamp on an actor switch while the contraption's actors run |
| `actors_stopped` | `#E0352B` | The same lamp while they are stopped |
| `guarded_lever` | `#DDDDDD` | A guarded switch's lever tip |
| `nixie_glass` | `#40F2C9A0` | Nixie tube glass; the first two digits are how solid it is |
| `nixie_glow` | `#FF9A2E` | Nixie digits |
| `board_text` | `#EDEDED` | Text on a display board |
| `highlight` | `#66000000` | The outline of the console or part under the crosshair |

## The console and its parts

| File (under `textures/block/`) | What it is |
|---|---|
| `console_plate.png` | The standard face plate |
| `console_plate_<dye>.png` | The plate after a dye is used on the console (15 colours; gray dye gives the standard plate) |
| `component.png` | The plain surface every coloured cap, lens and marking is tinted from |

The body takes its casing's own Create texture, the parts' metal trim the matching metal block, and display boards Create's flap display, so those follow a pack that restyles Create.

Item icons: `textures/item/panel_blueprint.png` and `panel_blueprint_filled.png`.

## Sounds

Each sound a part makes is its own event, so `sounds.json` in your pack can point any of them at a sound of yours:

| Event | When | Default |
|---|---|---|
| `part.button_on`, `part.button_off` | A button goes down, comes back up | Stone button click |
| `part.lever` | A toggle or guarded switch is thrown | Lever click |
| `part.lid_open`, `part.lid_close` | A guarded switch's lid | Iron trapdoor |
| `part.detent` | One step of a knob, slider or analog lever | Lever click |

## Text

Every label in the designer is in `lang/en_us.json`. A label too long for its button is drawn smaller to fit, down to 60% of its size, and cut short with "…" past that. The palette buttons are 58 pixels wide, so their names (`gui.create_controlling.add.*`) have the least room: about nine letters at full size, fifteen at the smallest.
