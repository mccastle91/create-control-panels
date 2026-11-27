package dev.mccastle91.createcontrolling.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
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
        event.registrar(VERSION).playToServer(BlueprintEditPayload.TYPE, BlueprintEditPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player)
                        payload.handle(player);
                }));
        event.registrar(VERSION).playToServer(ContraptionClickPayload.TYPE, ContraptionClickPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player)
                        payload.handle(player);
                }));
        event.registrar(VERSION).playToClient(ContraptionSyncPayload.TYPE, ContraptionSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(payload::handleClient));
    }

    public static void sendEditToServer(BlockPos pos, CompoundTag op) {
        PacketDistributor.sendToServer(new PanelEditPayload(pos, op));
    }

    public static void sendToServer(ContraptionClickPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    /** To every player who has the entity (a contraption) in view. */
    public static void sendToTracking(Entity entity, ContraptionSyncPayload payload) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, payload);
    }

    public static void sendToPlayer(ServerPlayer player, ContraptionSyncPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendBlueprintEditToServer(InteractionHand hand, CompoundTag op) {
        PacketDistributor.sendToServer(new BlueprintEditPayload(hand, op));
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
        CHANNEL.messageBuilder(BlueprintEditPayload.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BlueprintEditPayload::write)
                .decoder(BlueprintEditPayload::read)
                .consumerMainThread((payload, context) -> {
                    ServerPlayer player = context.get().getSender();
                    if (player != null)
                        payload.handle(player);
                })
                .add();
        CHANNEL.messageBuilder(ContraptionClickPayload.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ContraptionClickPayload::write)
                .decoder(ContraptionClickPayload::read)
                .consumerMainThread((payload, context) -> {
                    ServerPlayer player = context.get().getSender();
                    if (player != null)
                        payload.handle(player);
                })
                .add();
        CHANNEL.messageBuilder(ContraptionSyncPayload.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ContraptionSyncPayload::write)
                .decoder(ContraptionSyncPayload::read)
                .consumerMainThread((payload, context) -> payload.handleClient())
                .add();
    }

    public static void sendEditToServer(BlockPos pos, CompoundTag op) {
        CHANNEL.sendToServer(new PanelEditPayload(pos, op));
    }

    public static void sendToServer(ContraptionClickPayload payload) {
        CHANNEL.sendToServer(payload);
    }

    /^* To every player who has the entity (a contraption) in view. ^/
    public static void sendToTracking(Entity entity, ContraptionSyncPayload payload) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> entity), payload);
    }

    public static void sendToPlayer(ServerPlayer player, ContraptionSyncPayload payload) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendBlueprintEditToServer(InteractionHand hand, CompoundTag op) {
        CHANNEL.sendToServer(new BlueprintEditPayload(hand, op));
    }
    *///?}
}
