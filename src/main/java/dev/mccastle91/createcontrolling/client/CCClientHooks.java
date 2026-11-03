package dev.mccastle91.createcontrolling.client;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Client-side entry points populated by the loader module's client init.
 * Null on dedicated servers — callers must guard. This class itself is
 * server-safe (no client classes referenced).
 */
public final class CCClientHooks {

    /** Opens the Panel Designer screen for the panel at the given position. */
    public static Consumer<BlockPos> OPEN_DESIGNER;

    /** Sends a designer edit operation to the server. */
    public static BiConsumer<BlockPos, CompoundTag> SEND_EDIT;

    private CCClientHooks() {
    }
}
