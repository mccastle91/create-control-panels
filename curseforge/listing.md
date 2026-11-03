# CurseForge listing kit

Everything needed to submit Create Controlling to CurseForge. The page text is [description.md](description.md); the art is in [images/](images/) and is drawn from [art/banners.html](art/banners.html) (re-render with `bash curseforge/art/render.sh`).

## Project fields

| Field | Value |
|---|---|
| Name | Create Controlling (see the naming note below) |
| Summary | Build sloped control consoles with buttons, switches, knobs, lamps, gauges and nixie readouts, all wired to Create's Redstone Links. |
| Avatar | `images/logo.png` (400×400 PNG) |
| Main category | Technology → Redstone |
| More categories | Technology, Addons → Create (if offered in the category picker) |
| License | MIT |
| Description editor | Markdown; paste `description.md` after swapping in the image URLs |
| Issues / Source | https://github.com/mccastle91/create-control-panels/issues and https://github.com/mccastle91/create-control-panels |

## Putting the description together

CurseForge serves description images from its own CDN, so upload them first:

1. Create the project, then open its **Images** tab and upload every file in `images/` except `logo.png`.
2. Open each uploaded image and copy its URL (it starts with `https://media.forgecdn.net/`).
3. In `description.md`, replace each placeholder with its URL:

| Placeholder | File |
|---|---|
| `IMG_HEADER` | header.png |
| `IMG_COMPONENTS` | components.png |
| `IMG_STEPS` | steps.png |
| `IMG_DIVIDER_FEATURES` | divider-features.png |
| `IMG_DIVIDER_COMPONENTS` | divider-components.png |
| `IMG_DIVIDER_GETTING_STARTED` | divider-getting-started.png |
| `IMG_DIVIDER_CASINGS` | divider-casings.png |
| `IMG_DIVIDER_COMPATIBILITY` | divider-compatibility.png |
| `IMG_DIVIDER_FAQ` | divider-faq.png |
| `IMG_DIVIDER_CREDITS` | divider-credits.png |

4. Switch the description editor to **Markdown**, paste, and check the preview.

The banners are rendered at 2× (2400 px wide) so they stay sharp on high-DPI screens; CurseForge scales them to the page width.

## Gallery

Add 4–6 **real in-game screenshots** to the Images tab and mark the best one as featured. The banners are illustrations; reviewers and players both want to see the actual mod. Good shots:

- a finished control desk of several connected consoles in a Create build
- the Console Designer open, mid-layout
- consoles in two or three different casings and angles
- a nixie readout and needle gauge reading a live value at night
- the Ponder scene

## Uploading files

Two jars per release, from `./gradlew build`. The current release is **0.2.1**:

| File | From | Game version | Loader | Java |
|---|---|---|---|---|
| `create_controlling-forge-0.2.1+1.20.1.jar` (not `-sources`) | `versions/1.20.1-forge/build/libs/` | 1.20.1 | Forge | 17 |
| `create_controlling-neoforge-0.2.1+1.21.1.jar` (not `-sources`) | `versions/1.21.1-neoforge/build/libs/` | 1.21.1 | NeoForge | 21 |

For each file:

- **Release type:** Beta while the in-game review and animations are still in progress; Release once it settles. Only Release files sync to the CurseForge app by default.
- **Related projects:** Create, as **Required Dependency**, so the app installs it automatically.
- **Environment:** client and server.
- **Changelog:** required on every file. A short list of what changed since the previous upload is enough.

## Before submitting

- [ ] **Name check.** CurseForge requires unique names and checks for similar ones. "Controlling" is already a very popular keybinding mod, so a reviewer may ask about this name. "Create Controlling" is probably fine because it follows the Create-addon naming pattern; have a fallback ready, e.g. "Create: Control Consoles".
- [ ] **Mod metadata.** The `description` in `mods.toml` / `neoforge.mods.toml` still describes v1 ("control panels", "dials", "indicator lights"). Update it to match v2 so the in-game mod list agrees with the page.
- [x] **Issue tracker.** `issueTrackerURL` in both mod metadata files points at `https://github.com/mccastle91/create-control-panels/issues`.
- [ ] **Screenshots.** At least a few real in-game images in the gallery (see above).
- [ ] **Credits.** The description credits Create and names Control Engineering as the inspiration. If any code, models or textures were adapted from either (Control Engineering is MIT), add that attribution and license notice too.
- [ ] **Changelog** written for the first file. For 0.2.1: wall consoles start flat and tilt either way (top or bottom edge out); ceiling faces read upright from below; all-glass nixie tubes with digits readable from both sides; guarded switch redrawn as a toggle switch under a flip-up lid on brass hinge posts; flush lamps; lower buttons, knobs and sliders; bar meters with an adjustable length.
- [ ] Don't delete and re-upload to fix a rejection; edit the project and resubmit, or open a support ticket.

## Sources

- [Creating and submitting a project](https://support.curseforge.com/en/support/solutions/articles/9000197241-creating-and-submitting-a-project)
- [Moderation policies](https://support.curseforge.com/support/solutions/articles/9000197279)
- [Project submission guide](https://support.curseforge.com/support/solutions/articles/9000199552)
- [How to create the best project page](https://blog.curseforge.com/how-to-create-the-best-project-page/)
- [How to pass moderation review](https://blog.curseforge.com/how-to-pass-moderation-review-on-curseforge-2/)
