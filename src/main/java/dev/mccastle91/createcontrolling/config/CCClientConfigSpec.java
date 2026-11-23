package dev.mccastle91.createcontrolling.config;

import java.util.function.Supplier;

/** Client config (each player's own preferences); values flow into the CCConfig holder. */
public final class CCClientConfigSpec {

    //? if neoforge {
    private static final net.neoforged.neoforge.common.ModConfigSpec.Builder BUILDER =
            new net.neoforged.neoforge.common.ModConfigSpec.Builder();
    //?} else {
    /*private static final net.minecraftforge.common.ForgeConfigSpec.Builder BUILDER =
            new net.minecraftforge.common.ForgeConfigSpec.Builder();
    *///?}

    private static final Supplier<Boolean> ANALOG_POPUP = BUILDER
            .comment("Holding right-click on a knob or slider lets you drag the mouse to set it.",
                    "false: the part on the console moves as you drag.",
                    "true: a small window with a copy of the part opens while you hold, and closes when you let go.")
            .define("analogPopup", false);

    private static final Supplier<Boolean> DESIGNER_PREVIEW = BUILDER
            .comment("Draws the console in 3D beside the Console Designer when the window leaves room for it.")
            .define("designerPreview", true);
    private static final Supplier<Boolean> PART_SOUNDS = BUILDER
            .comment("Whether you hear buttons, switches and knobs on consoles being worked. Other players still hear them.")
            .define("partSounds", true);

    // Built last: static initializers run in declaration order
    //? if neoforge {
    public static final net.neoforged.neoforge.common.ModConfigSpec SPEC = BUILDER.build();
    //?} else {
    /*public static final net.minecraftforge.common.ForgeConfigSpec SPEC = BUILDER.build();
    *///?}

    private CCClientConfigSpec() {
    }

    public static void apply() {
        CCConfig.analogPopup = ANALOG_POPUP.get();
        CCConfig.designerPreview = DESIGNER_PREVIEW.get();
        CCConfig.partSounds = PART_SOUNDS.get();
    }
}
