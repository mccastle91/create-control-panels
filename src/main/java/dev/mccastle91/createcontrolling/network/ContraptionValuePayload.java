package dev.mccastle91.createcontrolling.network;

import dev.mccastle91.createcontrolling.compat.create.contraption.MovingConsole;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Client → server: a knob, slider or analog lever of a console that rides a
 * contraption, set by hand. {@code value} is where a drag has brought it, or,
 * with {@code relative}, the step of a sneak-scroll (up or down one).
 */
//? if neoforge {
public record ContraptionValuePayload(int entityId, BlockPos localPos, UUID elementId, int value, boolean relative)
        implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    public static final Type<ContraptionValuePayload> TYPE = new Type<>(dev.mccastle91.createcontrolling.util.McCompat.id(
            dev.mccastle91.createcontrolling.CreateControlling.MOD_ID, "contraption_value"));

    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, ContraptionValuePayload>
            STREAM_CODEC = net.minecraft.network.codec.StreamCodec.ofMember(ContraptionValuePayload::write, ContraptionValuePayload::read);

    @Override
    public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }
//?} else {
/*public record ContraptionValuePayload(int entityId, BlockPos localPos, UUID elementId, int value, boolean relative) {
*///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeBlockPos(localPos);
        buf.writeUUID(elementId);
        buf.writeVarInt(value);
        buf.writeBoolean(relative);
    }

    public static ContraptionValuePayload read(FriendlyByteBuf buf) {
        return new ContraptionValuePayload(buf.readVarInt(), buf.readBlockPos(), buf.readUUID(), buf.readVarInt(),
                buf.readBoolean());
    }

    /** Runs on the server thread. */
    public void handle(ServerPlayer player) {
        MovingConsole.setValue(player, entityId, localPos, elementId, value, relative);
    }
}
