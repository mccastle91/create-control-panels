package dev.mccastle91.createcontrolling.client;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

    /** Opens the designer on the blueprint held in the given hand. */
    public static Consumer<InteractionHand> OPEN_BLUEPRINT;

    /** Sends a designer edit operation for the blueprint held in the given hand. */
    public static BiConsumer<InteractionHand, CompoundTag> SEND_BLUEPRINT_EDIT;

    /** The player at this client, for tooltips that count their inventory. */
    public static Supplier<Player> CLIENT_PLAYER;

    /** Whether Shift is held, for tooltips with more to show. */
    public static BooleanSupplier SHIFT_DOWN;

    private CCClientHooks() {
    }
}
