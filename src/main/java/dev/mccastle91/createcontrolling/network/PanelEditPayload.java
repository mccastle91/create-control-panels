package dev.mccastle91.createcontrolling.network;

import dev.mccastle91.createcontrolling.panel.CostPayer;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.PanelEditOps;
import dev.mccastle91.createcontrolling.util.McCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Client → server: one designer edit operation, applied via {@link PanelEditOps}. */
//? if neoforge {
public record PanelEditPayload(BlockPos pos, CompoundTag op)
        implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    public static final Type<PanelEditPayload> TYPE =
            new Type<>(McCompat.id(dev.mccastle91.createcontrolling.CreateControlling.MOD_ID, "panel_edit"));

    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, PanelEditPayload>
            STREAM_CODEC = net.minecraft.network.codec.StreamCodec.composite(
                    BlockPos.STREAM_CODEC, PanelEditPayload::pos,
                    net.minecraft.network.codec.ByteBufCodecs.COMPOUND_TAG, PanelEditPayload::op,
                    PanelEditPayload::new);

    @Override
    public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }
//?} else {
/*public record PanelEditPayload(BlockPos pos, CompoundTag op) {

    public void write(net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeNbt(op);
    }

    public static PanelEditPayload read(net.minecraft.network.FriendlyByteBuf buf) {
        return new PanelEditPayload(buf.readBlockPos(), buf.readNbt());
    }
*///?}

    /** Runs on the server thread. */
    public void handle(ServerPlayer player) {
        var level = player.level();
        if (op == null || !level.isLoaded(pos) || !McCompat.canReach(player, pos))
            return;
        if (level.getBlockEntity(pos) instanceof PanelBlockEntity panel)
            PanelEditOps.apply(panel, op, level.registryAccess(), CostPayer.of(player));
    }
}
