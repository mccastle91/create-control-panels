package dev.mccastle91.createcontrolling.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Consumer;

/**
 * Server → client: the state of a console that rides a contraption, as a
 * placed console's block update would carry it. Create sends a contraption's
 * blocks once, as they were when it was assembled.
 */
//? if neoforge {
public record ContraptionSyncPayload(int entityId, BlockPos localPos, CompoundTag tag)
        implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    public static final Type<ContraptionSyncPayload> TYPE = new Type<>(dev.mccastle91.createcontrolling.util.McCompat.id(
            dev.mccastle91.createcontrolling.CreateControlling.MOD_ID, "contraption_sync"));

    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, ContraptionSyncPayload>
            STREAM_CODEC = net.minecraft.network.codec.StreamCodec.ofMember(ContraptionSyncPayload::write, ContraptionSyncPayload::read);

    @Override
    public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }
//?} else {
/*public record ContraptionSyncPayload(int entityId, BlockPos localPos, CompoundTag tag) {
*///?}

    /** What a client does with it; set by the client's init, null on a dedicated server. */
    public static Consumer<ContraptionSyncPayload> CLIENT_HANDLER;

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeBlockPos(localPos);
        buf.writeNbt(tag);
    }

    public static ContraptionSyncPayload read(FriendlyByteBuf buf) {
        return new ContraptionSyncPayload(buf.readVarInt(), buf.readBlockPos(), buf.readNbt());
    }

    /** Runs on the client thread. */
    public void handleClient() {
        if (CLIENT_HANDLER != null && tag != null)
            CLIENT_HANDLER.accept(this);
    }
}
