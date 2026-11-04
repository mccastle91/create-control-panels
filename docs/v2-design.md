# v2 design: Control-Engineering feel, Create identity

Decisions come from the 2026-10-27 review of Control Engineering (CE, MIT) — see the README status table for progress.

## What changes from v1

| | v1 | v2 |
|---|---|---|
| Body | 2px slab, tilt 0 / 22.5° / 45° via model rotation | One-block sloped **console**: angle 0–45° continuous with snap presets (0/15/30/45), adjustable front height. Floor and ceiling tilt one way; walls start flat and tilt either way |
| Body rendering | JSON multipart models | Custom baked model generated from the console's shape → correct chunk lighting/AO at any angle (CE's technique) |
| Tiers | copper / andesite / brass blocks | **One console.** Look chosen by right-clicking with a Create casing (andesite, brass, copper, railway, industrial iron) |
| Placement | integer cells, resizable elements | **Pixel coordinates on a 0.5px grid**, 16×16 px face, **fixed physical sizes** per component type |
| Components | flat coloured quads | **3D** parts built from boxes, Create textures, raised caps, lever rods, knobs, needles |
| Clicking | face-plane cell lookup | **Ray vs each component's 3D box**; knobs/sliders read the exact hit point |
| Cost | free | **Each component costs items**, paid from inventory in the designer, refunded on removal; creative is free |
| Kept | | Redstone Link frequencies (0–15), connected borders (same shape + orientation + casing), labels, blueprints, drops keep design, inventory frequency picker, goggles overlay, Ponder |

## Console shape

Stored on the block entity: `angle` (degrees, 0–45), `frontHeight` (px) and `reversed` (which edge is raised). The hinge edge stands `frontHeight`; the opposite edge rises by `16·tan(angle)` px, so `frontHeight ≤ 16 − 16·tan(angle)` (a 45° console has a knife front edge). The 16×16 px design area sits centred on the slope: the longer face's extra length (`16/cos(angle) − 16` px) is bare plate split evenly above and below it, and the frame stays 1 px all round.

Face frame (block space, before orientation): origin at design (0,0), `u` = right, `v` = down the slope towards the player, `n` = outward normal. Everything — body model, component renderer, click ray, selection shape — derives from this one frame.

Which way a console slopes depends on where it hangs:

- **Floor:** the edge nearest the player is the hinge; the far edge rises.
- **Ceiling:** the edge nearest the player hugs the ceiling; the far edge hangs lower, so the face looks down at the player. The face's design frame is turned 180° from the floor's (`u` to the player's right, `v` away from them), so text and nixie digits read upright from below.
- **Wall:** starts flat, and may raise either its top edge (face tilts down, for consoles mounted high) or its bottom edge (face tilts up, like a lectern) — one at a time. The design always reads top to bottom.

A console item remembers its old shape, but a console placed on a wall always starts flat.

Wrench: sneak-use cycles the angle presets; on a wall it runs 15/30/45° with the top edge out, then 15/30/45° with the bottom edge out, then flat. The designer's shape controls give continuous control.

## Components

| id | footprint (px) | signal | interaction |
|---|---|---|---|
| button | 3×3 | out, 15 for a pulse | click |
| latching_button | 3×3 | out, toggles 0/15 | click |
| large_button | 5×5 | out, 15 for a pulse | click |
| toggle_switch | 2×3 | out, latching | click |
| covered_switch | 2×3 | out, 15 only when armed | click: closed → open → armed → closed |
| knob | 4×4 | out, analog | click sets value from hit angle; sneak-scroll ±1 |
| slider_h / slider_v | 8×1.5 / 1.5×8 | out, analog | click sets value from hit position |
| lamp / large_lamp | 2×2 / 4×4 | in, glow by strength | — |
| needle_meter | 6×5 | in, needle | — |
| bar_meter | 3–14 × 2 (length set per meter, default 6) | in, bar | — |
| nixie | 6×4 | in, two nixie digits 0–15 | — |
| nixie_strip | 5.5–13 × 4 (2–5 tubes, default 4) | Display Link, one line | — |
| display_board | 12–14 × 6 (default 12, centred) | Display Link, two rows, each its own line or off | — |
| label | text-sized | — | — (passes clicks through) |

Costs (lines name an item or an item tag; an item that doesn't resolve makes that line free):

| Component | Cost |
|---|---|
| button | any wooden button + glowstone dust |
| latching_button | lever + glowstone dust |
| large_button | any wooden pressure plate + glowstone dust |
| toggle_switch | lever |
| covered_switch | lever + iron sheet |
| knob, slider_h, slider_v | Create analog lever |
| lamp | redstone lamp |
| large_lamp | 2 redstone lamps |
| needle_meter, bar_meter | Create speedometer |
| nixie | Create nixie tube |
| nixie_strip | 2 Create nixie tubes |
| display_board | Create display board |
| label | any dye (`c:dyes` on NeoForge, `forge:dyes` on Forge) |

Each component records the exact stacks paid for it; removing it refunds those (so a part mounted in creative refunds nothing). A blueprint paste reuses the old components' paid items for the new ones, charges only the shortfall and refunds the rest.

## Rendering

- **Body:** one blockstate variant pointing at a placeholder JSON model (particle texture, item transforms, render type). At bake time (`ModelEvent.ModifyBakingResult`, same shape on Forge 1.20.1 and NeoForge 1.21.1) every console blockstate's model and the item model are wrapped in `ConsoleBakedModel` — simpler than a geometry loader, whose registration differs between the loaders. It reads the console's shape and casing from model data (`CreatePanelBlockEntity.getModelData`), plus connection flags from the blockstate, and `ConsoleMesh` emits quads: sides/back/bottom in the casing texture, the face as the `console_plate` texture inside a 1 px frame sampled from the casing texture's second row. Quads are packed as raw `BLOCK`-format vertex data, identical on both versions. Cached per (orientation, shape, casing, connections). The bottom quad is culled against the supporting block.
- **Components:** `PanelBlockEntityRenderer` + `ComponentPainter` draw textured boxes (Create's brass block and industrial iron textures, plus our tintable `component` texture) into `RenderType.solid()` with block-style diffuse shading from each face's real normal, so they sit naturally next to chunk geometry. Lit lamps, bar fills and nixie digits render full-bright. Nixie tubes are all glass above a brass socket, like Create's; the glass goes into our own `CCRenderTypes.GLASS` (translucent, no depth write), so the digits floating inside show through it whatever the draw order. Text render types cull back faces, so the digits go through `DoubleSidedBuffers`, which re-emits each glyph quad with reversed winding: from behind a digit shows mirrored, like a real filament. On ceiling consoles the digits turn half a turn so they read upright from below; on wall consoles they lie parallel to the face, facing out, read end-on through the glass like a top-view tube. Textures in `textures/block/` join the block atlas automatically. Nixie strips share the nixie's tube drawing. A display board has no bezel: `ComponentPainter.displayBoard` wraps a slab in Create's `flap_display_*` textures the way the real block's front slab does. The front texture's two flap rows always span the board's depth, one per text row, repeating across its width at the same scale, and the stripes run on down the sides.
- **Proportions** (from the 2026-10-29 review): toggle and guarded switches share one 2×3 px footprint, base and short lever, the guard is just a lid over it, hinged at its top far edge on two brass posts and a pin (hidden inside the closed lid), so it flips up to stand upright within the footprint; buttons large and small stand 1.5 px; lamp lenses sit flush with their bezel; knobs stand 1.25 px; slider thumbs are 1.5 px long and 1 px tall.
- **Animation:** none yet — states snap. Easing caps and levers is a small follow-up once the look is approved.

## Interaction and shapes

- The click handler transforms the player's eye ray into face space and intersects each component's 3D box; nearest hit wins. Labels never take clicks.
- Sneak + empty hand always opens the designer (never a component click), so play-mode clicks are plain clicks; fine adjustment of knobs and sliders is sneak + scroll, which sends an `adjust` op.
- The selection shape is the wedge as a few stacked boxes plus each component's bounds, cached on the block entity and rebuilt when the layout or shape changes. Collision is the wedge only.

## Designer

Sneak + empty hand on the console. Palette on the left (item costs in each button's tooltip), the 16×16 px face in the middle with 0.5 px snapping and drag-to-move, the selected component's frequency slots and options on the right. Beneath the face: an angle slider that snaps to the presets within 2° and updates the console live, plus front-height buttons in 0.5 px steps; on wall consoles a *Top out / Bottom out* button picks the raised edge.

## Build order

1. ✅ Data model: console shape, casing, pixel-space layout, new component types and costs; edit ops; unit tests.
2. ✅ Console block + baked body model + connections + casing swap + wrench presets + face geometry + selection shape.
3. ✅ Component renderer (3D) + ray-based clicking + behaviours for all component types.
4. ✅ Designer v2 with costs and the shape controls.
5. ✅ Blueprints (paste charges only the difference), goggles, Ponder, game tests, docs.
6. ⬜ In-game visual review with the user; then small animations and polish.
