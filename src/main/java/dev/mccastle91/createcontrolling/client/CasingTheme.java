package dev.mccastle91.createcontrolling.client;

import dev.mccastle91.createcontrolling.panel.ConsoleCasing;

import java.util.Locale;

/**
 * The look a console's screens wear, one per casing — the way Create's own
 * menus are gold, blue or grey after the block they belong to.
 *
 * A theme is a folder of textures, {@code textures/gui/console/<id>/}: its
 * title bar and body tile, and optionally its own copy of any shared window
 * texture (see {@link ConsoleStyle}). The two text colours a texture can't
 * carry are here as defaults, and a resource pack changes them in
 * {@code console_style.json} under {@code "themes"}. The mod's own textures
 * are drawn by {@code tools/gui_textures.py}.
 */
public enum CasingTheme {

    // title text; accent: section headers
    ANDESITE(0x33342F, 0xD4D6CE),
    // Create's own brass menu colours (Sequenced Gearshift, Display Link)
    BRASS(0x592424, 0xE4B763),
    COPPER(0x4A1F14, 0xF0A078),
    // train casing: dark slate with brass lettering
    RAILWAY(0xF2CE7A, 0xE4B763),
    INDUSTRIAL_IRON(0xF0F0F0, 0xC9CACC),
    WEATHERED_IRON(0x2E2219, 0xD0B69C),
    // the copycat console: zinc, whatever block it copies
    COPYCAT(0x26312B, 0xC6D8CB),
    // no casing at all: the designer opened on a blueprint, in the blue of Create's schematics
    BLUEPRINT(0x12284D, 0xA9CCF5);

    private final String id = name().toLowerCase(Locale.ROOT);
    private final int defaultTitleText;
    private final int defaultAccent;
    /** Opaque RGB, as a resource pack last set them. */
    public int titleText;
    public int accent;

    CasingTheme(int titleText, int accent) {
        this.defaultTitleText = titleText;
        this.defaultAccent = accent;
        reset();
    }

    /** The theme's texture folder and its key in {@code console_style.json}. */
    public String id() {
        return id;
    }

    void reset() {
        titleText = defaultTitleText;
        accent = defaultAccent;
    }

    public static CasingTheme of(ConsoleCasing casing) {
        switch (casing) {
            case BRASS: return BRASS;
            case COPPER: return COPPER;
            case RAILWAY: return RAILWAY;
            case INDUSTRIAL_IRON: return INDUSTRIAL_IRON;
            case WEATHERED_IRON: return WEATHERED_IRON;
            case COPYCAT: return COPYCAT;
            default: return ANDESITE;
        }
    }
}
