package dev.mccastle91.createcontrolling.config;

/**
 * Runtime config values, populated by the loader module's config system
 * (NeoForge ModConfigSpec). Defaults apply until a config loads.
 */
public final class CCConfig {

    public static int maxElementsPerPanel = 64;
    public static int maxPulseTicks = 200;
    /** Whether mounting a component takes its materials; off, every part is free (and refunds nothing new). */
    public static boolean componentsCostMaterials = true;
    /** Sneak + wrench opens the designer instead of picking the console up. */
    public static boolean sneakWrenchOpensDesigner = false;

    /** Client: holding right-click on a knob or slider opens a dial window instead of dragging in place. */
    public static boolean analogPopup = false;
    /** Client: the console is drawn in 3D beside the designer where there is room. */
    public static boolean designerPreview = true;
    /** Client: whether this player hears buttons, switches and knobs being worked. */
    public static boolean partSounds = true;

    private CCConfig() {
    }
}
