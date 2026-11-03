package dev.mccastle91.createcontrolling.compat.create;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import dev.mccastle91.createcontrolling.link.LinkPlatform;
import dev.mccastle91.createcontrolling.link.LinkPort;
import dev.mccastle91.createcontrolling.panel.FrequencyPair;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Binds panel elements into Create's Redstone Link network. One
 * {@link IRedstoneLinkable} per panel element (all sharing the panel's
 * position), mirroring {@code LinkBehaviour}'s lifecycle: add on create,
 * remove on unload/delete, {@code updateNetworkOf} on value change.
 * Panels therefore interoperate with vanilla Create links, Linked
 * Controllers, and anything else on the network.
 */
public final class CreateLinkPlatform implements LinkPlatform {

    @Override
    public LinkPort createPort(Level level, BlockPos pos, FrequencyPair frequency, boolean listening,
                               IntSupplier transmittedStrength, IntConsumer receivedCallback, BooleanSupplier alive) {
        PanelLinkPort port = new PanelLinkPort(level, pos, frequency, listening,
                transmittedStrength, receivedCallback, alive);
        Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, port);
        port.refreshReceivedStrength();
        return port;
    }

    private static final class PanelLinkPort implements IRedstoneLinkable, LinkPort {

        private final Level level;
        private final BlockPos pos;
        private final Couple<Frequency> networkKey;
        private final boolean listening;
        private final IntSupplier transmittedStrength;
        private final IntConsumer receivedCallback;
        private final BooleanSupplier alive;

        private boolean removed;

        private PanelLinkPort(Level level, BlockPos pos, FrequencyPair frequency, boolean listening,
                              IntSupplier transmittedStrength, IntConsumer receivedCallback, BooleanSupplier alive) {
            this.level = level;
            this.pos = pos;
            this.networkKey = Couple.create(Frequency.of(frequency.first()), Frequency.of(frequency.second()));
            this.listening = listening;
            this.transmittedStrength = transmittedStrength;
            this.receivedCallback = receivedCallback;
            this.alive = alive;
        }

        // IRedstoneLinkable

        @Override
        public int getTransmittedStrength() {
            return listening || removed ? 0 : Mth.clamp(transmittedStrength.getAsInt(), 0, 15);
        }

        @Override
        public void setReceivedStrength(int power) {
            if (listening && !removed)
                receivedCallback.accept(Mth.clamp(power, 0, 15));
        }

        @Override
        public boolean isListening() {
            return listening;
        }

        @Override
        public boolean isAlive() {
            return !removed && alive.getAsBoolean();
        }

        @Override
        public Couple<Frequency> getNetworkKey() {
            return networkKey;
        }

        @Override
        public BlockPos getLocation() {
            return pos;
        }

        // LinkPort

        @Override
        public void notifyStrengthChanged() {
            if (!removed)
                Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, this);
        }

        @Override
        public void remove() {
            if (removed)
                return;
            removed = true;
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, this);
        }

        /**
         * {@code updateNetworkOf} pushes power to every listener except the
         * actor itself, so a freshly added receiver computes its own initial
         * value the same way the handler does (strongest in-range transmitter).
         */
        void refreshReceivedStrength() {
            if (!listening)
                return;
            int power = 0;
            for (IRedstoneLinkable other : Create.REDSTONE_LINK_NETWORK_HANDLER.getNetworkOf(level, this))
                if (other != this && other.isAlive() && RedstoneLinkNetworkHandler.withinRange(this, other))
                    power = Math.max(power, other.getTransmittedStrength());
            receivedCallback.accept(Math.min(power, 15));
        }
    }
}
