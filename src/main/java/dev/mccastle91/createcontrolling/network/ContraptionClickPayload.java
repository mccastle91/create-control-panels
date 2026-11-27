package dev.mccastle91.createcontrolling.network;

import dev.mccastle91.createcontrolling.compat.create.contraption.MovingConsole;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Client → server: a click on a part of a console that rides a contraption.
 * The client names the part and the spot on it, since only it knows where
 * the moving console was on screen when the player clicked.
 */
//? if neoforge {
public record ContraptionClickPayload(int entityId, BlockPos localPos, UUID elementId, int zone,
                                      float localX, float localY, boolean sneaking)
        implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    public static final Type<ContraptionClickPayload> TYPE = new Type<>(dev.mccastle91.createcontrolling.util.McCompat.id(
            dev.mccastle91.createcontrolling.CreateControlling.MOD_ID, "contraption_click"));

    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, ContraptionClickPayload>
            STREAM_CODEC = net.minecraft.network.codec.StreamCodec.ofMember(ContraptionClickPayload::write, ContraptionClickPayload::read);

    @Override
    public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }
//?} else {
/*public record ContraptionClickPayload(int entityId, BlockPos localPos, UUID elementId, int zone,
                                      float localX, float localY, boolean sneaking) {
*///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeBlockPos(localPos);
        buf.writeUUID(elementId);
        buf.writeVarInt(zone);
        buf.writeFloat(localX);
        buf.writeFloat(localY);
        buf.writeBoolean(sneaking);
    }

    public static ContraptionClickPayload read(FriendlyByteBuf buf) {
        return new ContraptionClickPayload(buf.readVarInt(), buf.readBlockPos(), buf.readUUID(), buf.readVarInt(),
                buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    /** Runs on the server thread. */
    public void handle(ServerPlayer player) {
        MovingConsole.click(player, entityId, localPos, elementId, zone, localX, localY, sneaking);
    }
}
