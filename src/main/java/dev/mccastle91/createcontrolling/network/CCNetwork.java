package dev.mccastle91.createcontrolling.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
//? if neoforge {
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
//?} else {
/*import dev.mccastle91.createcontrolling.CreateControlling;
import dev.mccastle91.createcontrolling.util.McCompat;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
*///?}

/** The mod's packets, behind one API so callers don't see loader networking. */
public final class CCNetwork {

    private static final String VERSION = "1";

    private CCNetwork() {
    }

    //? if neoforge {
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(CCNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION).playToServer(PanelEditPayload.TYPE, PanelEditPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player)
                        payload.handle(player);
                }));
    }

    public static void sendEditToServer(BlockPos pos, CompoundTag op) {
        PacketDistributor.sendToServer(new PanelEditPayload(pos, op));
    }
    //?} else {
    /*private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            McCompat.id(CreateControlling.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register(IEventBus modEventBus) {
        CHANNEL.messageBuilder(PanelEditPayload.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PanelEditPayload::write)
                .decoder(PanelEditPayload::read)
                .consumerMainThread((payload, context) -> {
                    ServerPlayer player = context.get().getSender();
                    if (player != null)
                        payload.handle(player);
                })
                .add();
    }

    public static void sendEditToServer(BlockPos pos, CompoundTag op) {
        CHANNEL.sendToServer(new PanelEditPayload(pos, op));
    }
    *///?}
}
