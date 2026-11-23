package dev.mccastle91.createcontrolling.network;

import dev.mccastle91.createcontrolling.item.PanelBlueprintItem;
import dev.mccastle91.createcontrolling.util.McCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Client → server: one designer edit on the blueprint the player holds,
 * applied via {@link PanelBlueprintItem#edit}. The hand is all the address
 * there is: the edit lands on whatever blueprint is in it when it arrives.
 */
//? if neoforge {
public record BlueprintEditPayload(boolean offHand, CompoundTag op)
        implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

    public static final Type<BlueprintEditPayload> TYPE =
            new Type<>(McCompat.id(dev.mccastle91.createcontrolling.CreateControlling.MOD_ID, "blueprint_edit"));

    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, BlueprintEditPayload>
            STREAM_CODEC = net.minecraft.network.codec.StreamCodec.composite(
                    net.minecraft.network.codec.ByteBufCodecs.BOOL, BlueprintEditPayload::offHand,
                    net.minecraft.network.codec.ByteBufCodecs.COMPOUND_TAG, BlueprintEditPayload::op,
                    BlueprintEditPayload::new);

    @Override
    public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
        return TYPE;
    }
//?} else {
/*public record BlueprintEditPayload(boolean offHand, CompoundTag op) {

    public void write(net.minecraft.network.FriendlyByteBuf buf) {
        buf.writeBoolean(offHand);
        buf.writeNbt(op);
    }

    public static BlueprintEditPayload read(net.minecraft.network.FriendlyByteBuf buf) {
        return new BlueprintEditPayload(buf.readBoolean(), buf.readNbt());
    }
*///?}

    public BlueprintEditPayload(InteractionHand hand, CompoundTag op) {
        this(hand == InteractionHand.OFF_HAND, op);
    }

    /** Runs on the server thread. */
    public void handle(ServerPlayer player) {
        ItemStack stack = player.getItemInHand(offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (op == null || !(stack.getItem() instanceof PanelBlueprintItem))
            return;
        if (PanelBlueprintItem.edit(stack, op, player.level().registryAccess()))
            player.getInventory().setChanged();
    }
}
