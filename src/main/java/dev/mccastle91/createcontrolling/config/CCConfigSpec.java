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

    private static final Supplier<Boolean> COMPONENTS_COST = BUILDER
            .comment("Whether components cost materials to mount.",
                    "false: every component is free, in survival too. Parts paid for earlier still give their items back.")
            .define("componentsCostMaterials", true);
    private static final Supplier<Boolean> SNEAK_WRENCH_DESIGNER = BUILDER
            .comment("What sneak + wrench does to a console.",
                    "false: picks the console up, as on any Create block.",
                    "true: opens the Console Designer instead (break the console to pick it up; it keeps its design).")
            .define("sneakWrenchOpensDesigner", false);

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
        CCConfig.componentsCostMaterials = COMPONENTS_COST.get();
        CCConfig.sneakWrenchOpensDesigner = SNEAK_WRENCH_DESIGNER.get();
    }
}
