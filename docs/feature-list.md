# Feature list

Updated 2026-11-22, after 0.2.7; the 0.2.8 work is marked below. Sources: the earlier planned/parked list in CLAUDE.md, GitHub issues #1–#7, and all 52 CurseForge comments. `GH #n` is an issue on mccastle91/create-control-panels; `CF` is a CurseForge comment.

Where a request is filed under "Planned" or "Add-on", that follows what the author already answered in public. Everything under "Requested, not decided" is waiting for a decision.

## Built for 0.2.8, awaiting the in-game check

Branch `blueprint-designer`. Unit tests and game tests pass on both versions; nothing has been seen in-game yet.

| Feature | Source | Notes |
|---|---|---|
| Material requirements coloured by what the player holds (red none, yellow some, green all) | GH #6 | Palette tooltips read have/need. |
| Blueprint shows the full material list, same colours | GH #6 | Hold Shift on the item, and in the blueprint designer's right column. |
| Blueprint as a free design planner | GH #7 | Use the blueprint in the air to draw on it for free; sneak-click a console to build. The author kept building over a console that has parts (charging the difference) rather than empty consoles only. An empty blueprint copies; a filled one never does. |
| Two-state blueprint icon | Author | Blank sheet when empty, a drawn console when it holds a design. |
| Copycat console: connected textures across joined consoles, textures laid as on the copied block, light from glowing blocks | Known limits in 0.2.7 | Light and the join rule are covered by game tests; the texture work can only be judged in-game. |
| Inverted lamps: lit without a signal, dimmer as it rises | Author | A toggle on Lamp and Big Lamp in the designer. |
| Dyeable face plate on every kind of console | Author, CF (verdis) | Right-click with a dye; gray dye is the standard plate. Faces in other materials (verdis suggested concrete) are not part of this. |

### Added on branch `dev-0.2.8` (2026-11-23), merged into `main` the same day after the author tested it

| Feature | Source | Notes |
|---|---|---|
| Designer that resource packs can restyle | CF (HappyMeek) and others | Window parts are textures, per casing theme; text and line colours are in `console_style.json`. See `docs/resource-packs.md`. |
| Part colours a pack can change (nixie glow, needles, markings, the outline) | Author | Same `console_style.json`. |
| Part sounds a pack can replace | Author | `create_controlling:part.*` sound events, with subtitles. |
| Designer fully translatable | GH #4 | The last hard-coded labels are lang keys. The French file from the issue is still not in the repo. |
| Config: sneak + wrench opens the designer | GH #6 | Server option `sneakWrenchOpensDesigner`; it replaces picking the console up. |
| Config: free components | Author | Server option `componentsCostMaterials`. |
| Config: designer 3D preview on/off, part sounds on/off | Author | Client options `designerPreview`, `partSounds`. |
| Labels shrink to fit their buttons; French translation shipped | GH #4 | Added on `main` after the merge. The French file predates 0.2.7: 36 lines (33 new since then, 3 that described the old blueprint) were translated by Claude and await the contributor's review. Two palette names are still cut short at the smallest size ("Bnd de tubes Nixie", "Panneau d'affichage"). Not seen in-game. |
| Add-on API, version 1 | Author | Components with cost, options and renderer; reading and working consoles. See `docs/api.md`. |

## Built after 0.2.8, awaiting the in-game check

| Feature | Source | Notes |
|---|---|---|
| Consoles work on Create contraptions (elevators, trains and the rest) | The author, 2026-11-26 | Branch `contraption-consoles`. Parts stay on their frequencies and can be clicked while assembled. A game test covers the server side on a real elevator; nothing has been seen in-game. |

## Planned for the main mod

| Feature | Source | Notes |
|---|---|---|
| Contraption consoles: part highlight and hold-and-drag | Follow-up to the contraption work | Stage 3 of the proposal the author approved on 2026-11-26; to do after the in-game check. |
| Goggles overlay redesign, with a per-console off switch | Earlier list, CF (glitchtechscience) | The overlay is switched off since 0.2.5. |
| Icon palette in the designer | Earlier list | Deferred from the 0.2.6 reskin. |
| Small animations (button press, lever swing) | Earlier list | Keep them simple. |
| Translations: finish `fr_fr`, more languages | GH #4 | French ships since 0.2.8 with 36 machine-translated lines; ask the contributor to review those and for shorter names for the two palette buttons that are cut (draft reply in `curseforge/issue-4-reply.md`). The author speaks English and Spanish. |
| Component costs from data packs | Follow-up to the 0.2.8 config work | Today costs are fixed in code, with one switch to make everything free. |
| Replace the AI-made banner and logo | CF (user_dmeww750ra5gvaze) | Author said these two are still to be replaced. |

## Requested, not decided

| Feature | Source | Notes |
|---|---|---|
| Elevator floor parts: a button that calls a floor, a lamp or nixie for the current one | Suggested by Claude, 2026-11-26 | Create's "call to floor" is reachable from the server. Until then a button linked to a receiver at a floor's Elevator Contact does the same. |
| Original Drive-By-Wire and its Sable port | CF and YouTube (scaleshapes) | 0.2.7 supports Drive-By-Sable only, on 1.21.1. The requester asked for both. |
| Trim chosen per component | Earlier note | Left out when trim started following the casing. |
| Stop marks for Min/Max on the analog lever | Earlier note | Knobs and sliders have them; the lever doesn't. |
| Ponder scene for the copycat console and for cables | Earlier note | Scenes don't mention either yet. |

## Add-on candidates (kept out of the main mod)

| Add-on | Contents | Source |
|---|---|---|
| Security | Key and lock with unique copyable keys; per-component "locked" or "bypass"; select all / none; CC functions such as `isLocked()` and `getKeyId()`. A key model and texture were contributed on the issue. | GH #3 |
| Security | Wall cabinet: a console set into the wall behind single or double doors. The author asked back whether mounted, embedded, or both; no answer yet. | GH #5 |
| Security | Key switch component | Earlier list (deferred) |
| Extended components | A wide variety of extra switches, levers and so on. The add-on API (`docs/api.md`) is what these would build on. | Author's reply on GH #3 ("a lot of requests") |
| CC: Tweaked companion | Console parts specific to computers: monitors, keyboards and the like | Earlier decision (2026-11-18) |

## Parked until existing features are polished

- Power Grid-specific parts, including a "Grid Interface" block (wait for player demand)
- Factory Controller as a component; generating a console from a Factory Controller board (1.21.1 only, internal classes)
- Fabric port (confirm with the user before starting)

## Bug reports to follow up

| Report | Source | State |
|---|---|---|
| A wall-mounted console has the collision box of a floor one | CF (user_bugo5qd05h8cgfli) | Author asked for version, loader and mod list; no reply yet. Not reproduced. |
| Clicks land to the right of where the player is aiming | CF (same user) | Same: waiting for details. |
| "Doesn't work on a server" | CF (noogieboogie12) | No details given; needs version, loader and what failed. |

## Still to verify

- Consoles on contraptions in-game: how they are drawn, clicking parts while moving, sounds, trains and other contraption types
- 0.2.6 CC changes and the designer reskin on 1.20.1; reskin themes other than brass
- Weathered Iron casing in-game; the seam-line fix on joined consoles
- Cabled consoles on a moving vehicle, including assembling one
- Compatibility with Power Grid and Factory Controller

## Done since the earlier list, or needing no work

- CC without frequencies (GH #1): shipped in 0.2.6. **The issue is still open and can be closed.**
- Joined consoles as one CC peripheral (CF, Bobster71): 0.2.6
- Camouflage (CF, verdis): Copycat Console, 0.2.7. The face plate part of that request is under "Planned".
- Drive-By-Sable cables (CF, scaleshapes): 0.2.7
- Button and part sounds (CF): 0.2.6
- Hold-and-drag for knobs and sliders, optional dial window (CF, three users): 0.2.5
- Vertical bar meter, guarded switch z-fighting (CF, glitchtechscience): 0.2.5 / 0.2.6
- Tutorial video (CF, jaykayblazer): published, linked in the description
- "Make analog switches slide-based" (CF): already works by hold-and-drag; answered
- HBM compatibility question (CF): works through Redstone Links; answered
- Consoles inactive after reload (GH #2): caused by Create Connected 1.1.10, fixed in its 1.2.3; closed

## Declined

- Timed auto-close for the guarded switch (the author: needless complexity)
