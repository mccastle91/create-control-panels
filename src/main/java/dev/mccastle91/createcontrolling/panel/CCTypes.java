package dev.mccastle91.createcontrolling.panel;

import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

/**
 * Registry object holders populated by the active loader module during
 * registration, so common code can reference registered types without
 * depending on any loader's registration API.
 */
public final class CCTypes {

    // Wildcard: loader modules register their own PanelBlockEntity subclass
    // to supply loader-specific lifecycle hooks (load/chunk-unload).
    public static Supplier<BlockEntityType<? extends PanelBlockEntity>> PANEL_BLOCK_ENTITY;

    private CCTypes() {
    }
}
