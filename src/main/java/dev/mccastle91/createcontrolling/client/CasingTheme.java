package dev.mccastle91.createcontrolling.client;

import dev.mccastle91.createcontrolling.panel.ConsoleCasing;

/**
 * The colours a console's screens wear, one set per casing — the way Create's
 * own menus are gold, blue or grey after the block they belong to. To change
 * how a casing's screens look, this is the one place.
 *
 * All colours are opaque RGB. The window's shape, and everything that is the
 * same grey on every Create menu (footer, buttons, slots), is in
 * {@link ConsoleGui}.
 */
public enum CasingTheme {

    // title bar: highlight, fill, shadow, text; body: the lighter of its two checker tones; accent: section headers
    ANDESITE(0xD9DBD2, 0xA9ABA2, 0x4A4C48, 0x33342F, 0x575757, 0xD4D6CE),
    // Create's own brass menu colours (Sequenced Gearshift, Display Link)
    BRASS(0xFFEB8C, 0xE4B763, 0x592424, 0x592424, 0x442B15, 0xE4B763),
    COPPER(0xF7B890, 0xD9845A, 0x5A2A1C, 0x4A1F14, 0x4A2A1E, 0xF0A078),
    // train casing: dark slate with brass lettering
    RAILWAY(0x8388A0, 0x565A6B, 0x1E1F27, 0xF2CE7A, 0x30323D, 0xE4B763),
    INDUSTRIAL_IRON(0x9A9B9E, 0x6F7072, 0x2A2A2C, 0xF0F0F0, 0x3A3B3D, 0xC9CACC),
    WEATHERED_IRON(0xB39A82, 0x8A7560, 0x3A2E24, 0x2E2219, 0x43372D, 0xD0B69C),
    // the copycat console: zinc, whatever block it copies
    COPYCAT(0xD5E3D8, 0xA9BDAF, 0x3F4D45, 0x26312B, 0x4A5650, 0xC6D8CB),
    // no casing at all: the designer opened on a blueprint, in the blue of Create's schematics
    BLUEPRINT(0x9CC4F2, 0x5B8FD6, 0x1D3A6B, 0x12284D, 0x2F4A78, 0xA9CCF5);

    public final int titleHighlight;
    public final int title;
    public final int titleShadow;
    public final int titleText;
    public final int body;
    public final int accent;

    CasingTheme(int titleHighlight, int title, int titleShadow, int titleText, int body, int accent) {
        this.titleHighlight = titleHighlight;
        this.title = title;
        this.titleShadow = titleShadow;
        this.titleText = titleText;
        this.body = body;
        this.accent = accent;
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
