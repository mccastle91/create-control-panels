package dev.mccastle91.createcontrolling.config;

import java.util.function.Supplier;

/** Server config; values flow into the loader-agnostic CCConfig holder. */
public final class CCConfigSpec {

    //? if neoforge {
    private static final net.neoforged.neoforge.common.ModConfigSpec.Builder BUILDER =
            new net.neoforged.neoforge.common.ModConfigSpec.Builder();
    //?} else {
    /*private static final net.minecraftforge.common.ForgeConfigSpec.Builder BUILDER =
            new net.minecraftforge.common.ForgeConfigSpec.Builder();
    *///?}

    private static final Supplier<Integer> MAX_ELEMENTS = BUILDER
            .comment("Maximum number of elements on one control panel")
            .defineInRange("maxElementsPerPanel", 64, 1, 256);
    private static final Supplier<Integer> MAX_PULSE_TICKS = BUILDER
            .comment("Maximum configurable button pulse length, in ticks")
            .defineInRange("maxPulseTicks", 200, 1, 1200);

    // Built last: static initializers run in declaration order
    //? if neoforge {
    public static final net.neoforged.neoforge.common.ModConfigSpec SPEC = BUILDER.build();
    //?} else {
    /*public static final net.minecraftforge.common.ForgeConfigSpec SPEC = BUILDER.build();
    *///?}

    private CCConfigSpec() {
    }

    public static void apply() {
        CCConfig.maxElementsPerPanel = MAX_ELEMENTS.get();
        CCConfig.maxPulseTicks = MAX_PULSE_TICKS.get();
    }
}
