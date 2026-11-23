package dev.mccastle91.createcontrolling.client;

/**
 * Every colour the mod draws from code rather than from a texture: text and
 * markings on its screens, and the fixed colours of the parts on a console.
 * Each has the mod's own value as its default; a resource pack changes any of
 * them in {@code assets/create_controlling/console_style.json} under
 * {@code "colors"}, by the key given here (see {@link ConsoleStyle}).
 */
public enum StyleColor {

    // --- Screens: text ---
    TEXT("text", 0xFFFFFFFF),
    TEXT_HINT("text_hint", 0xFFDDDDDD),
    TEXT_DIM("text_dim", 0xFFA0A0A0),
    /** Text on the light grey footer and inventory panel. */
    PANEL_TEXT("panel_text", 0xFF3D3C48),
    BUTTON_TEXT("button_text", 0xFFFFFFFF),
    BUTTON_TEXT_DISABLED("button_text_disabled", 0xFFA0A0A0),
    SLIDER_TEXT("slider_text", 0xFF442B15),
    SLIDER_NOTCH("slider_notch", 0xFF8A6A3F),

    // --- Screens: frequency slots, after the two markers on Create's Redstone Link ---
    FREQUENCY_1("frequency_1", 0xFF97333B),
    FREQUENCY_1_LIT("frequency_1_lit", 0xFFC4505A),
    FREQUENCY_2("frequency_2", 0xFF5371C6),
    FREQUENCY_2_LIT("frequency_2_lit", 0xFF7E98E0),
    SLOT_HOVER("slot_hover", 0x80FFFFFF),
    SLOT_DROP("slot_drop", 0x60FFFFFF),

    // --- Designer: the face and the parts drawn flat on it ---
    FACE_BORDER("face_border", 0xFF000000),
    FACE_SHADOW("face_shadow", 0x70000000),
    GRID_MAJOR("grid_major", 0x30FFFFFF),
    GRID_MINOR("grid_minor", 0x14FFFFFF),
    BLUEPRINT_FRAME("blueprint_frame", 0xFF284B85),
    BLUEPRINT_PAPER("blueprint_paper", 0xFF335F9E),
    PART_OUTLINE("part_outline", 0xFF2A2724),
    PART_PLAIN("part_plain", 0xFF5A5A60),
    PART_GAUGE("part_gauge", 0xFF6E5A3C),
    PART_GAUGE_ACTIVE("part_gauge_active", 0xFFB08850),
    PART_BOARD("part_board", 0xFF2B2B2D),
    PART_MARK_DARK("part_mark_dark", 0xFF202020),
    PART_MARK_LIGHT("part_mark_light", 0xFFFFFFFF),
    LABEL_OUTLINE("label_outline", 0x30FFFFFF),
    SELECTION("selection", 0xFFFFFFFF),
    DRAG_VALID("drag_valid", 0x60FFFFFF),
    DRAG_INVALID("drag_invalid", 0x70FF4040),
    SWATCH_RING("swatch_ring", 0xFFFFFFFF),
    SWATCH_CURRENT("swatch_current", 0xFF15110C),

    // --- Parts on the console, in the world (and in the dial window) ---
    /** A needle gauge's face. */
    DIAL_FACE("dial_face", 0xFFEDE4D4),
    /** Gauge needles, and the stop marks of limited knobs and sliders. */
    NEEDLE("needle", 0xFFB3261E),
    /** Tick marks, slider slots, a bar meter's unlit window. */
    MARKING("marking", 0xFF1C1C1F),
    /** The line on a knob that shows where it points. */
    POINTER("pointer", 0xFFF0F0F0),
    /** The small lamp on an analog lever. */
    LEVER_LAMP("lever_lamp", 0xFFFF2A1A),
    /** A guarded switch's lever tip. */
    GUARDED_LEVER("guarded_lever", 0xFFDDDDDD),
    /** Nixie tube glass; its alpha is how see-through it is. */
    NIXIE_GLASS("nixie_glass", 0x40F2C9A0),
    NIXIE_GLOW("nixie_glow", 0xFFFF9A2E),
    BOARD_TEXT("board_text", 0xFFEDEDED),
    /** The outline of the console or part under the crosshair. */
    HIGHLIGHT("highlight", 0x66000000);

    private final String key;
    private final int fallback;
    private int value;

    StyleColor(String key, int fallback) {
        this.key = key;
        this.fallback = fallback;
        this.value = fallback;
    }

    public String key() {
        return key;
    }

    /** With alpha: for fills, and for text (which treats a zero alpha as opaque). */
    public int argb() {
        return value;
    }

    /** Without alpha. */
    public int rgb() {
        return value & 0xFFFFFF;
    }

    void set(int argb) {
        value = argb;
    }

    void reset() {
        value = fallback;
    }
}
