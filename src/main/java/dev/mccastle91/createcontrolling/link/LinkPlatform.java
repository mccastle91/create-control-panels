package dev.mccastle91.createcontrolling.link;

import dev.mccastle91.createcontrolling.panel.FrequencyPair;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * The seam between the loader-agnostic panel logic and Create's redstone
 * link network. The NeoForge module binds this to Create's
 * {@code RedstoneLinkNetworkHandler}; a future Fabric module binds it to
 * whatever the Create port there exposes.
 */
public interface LinkPlatform {

    /**
     * Join the network on the given frequency. Server side only.
     *
     * @param listening           true for receivers (they get {@code receivedCallback}
     *                            pushes), false for transmitters (queried via
     *                            {@code transmittedStrength})
     * @param transmittedStrength current 0-15 output of the element
     * @param receivedCallback    invoked with the network's current 0-15 strength
     * @param alive               false once the owning block entity is gone;
     *                            lets the network prune stale entries
     */
    LinkPort createPort(Level level, BlockPos pos, FrequencyPair frequency, boolean listening,
                        IntSupplier transmittedStrength, IntConsumer receivedCallback, BooleanSupplier alive);
}
