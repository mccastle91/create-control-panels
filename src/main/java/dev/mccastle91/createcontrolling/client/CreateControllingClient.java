package dev.mccastle91.createcontrolling.client;

import dev.mccastle91.createcontrolling.client.model.ConsoleBakedModel;
import dev.mccastle91.createcontrolling.compat.ponder.CCPonderPlugin;
import dev.mccastle91.createcontrolling.item.PanelBlueprintItem;
import dev.mccastle91.createcontrolling.util.McCompat;
import dev.mccastle91.createcontrolling.network.CCNetwork;
import dev.mccastle91.createcontrolling.panel.ConsoleCasing;
import dev.mccastle91.createcontrolling.panel.PanelBlock;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.PanelEditOps;
import dev.mccastle91.createcontrolling.registry.CCContent;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
//? if neoforge {
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;
//?} else {
/*import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
*///?}

import java.util.Map;

/** Client-only wiring; never classloaded on dedicated servers. */
public final class CreateControllingClient {

    private CreateControllingClient() {
    }

    public static void init(IEventBus modEventBus) {
        CCClientHooks.OPEN_DESIGNER = pos -> Minecraft.getInstance().setScreen(new PanelDesignerScreen(pos));
        CCClientHooks.SEND_EDIT = CCNetwork::sendEditToServer;
        CCClientHooks.OPEN_BLUEPRINT = hand -> Minecraft.getInstance().setScreen(new PanelDesignerScreen(hand));
        CCClientHooks.SEND_BLUEPRINT_EDIT = CCNetwork::sendBlueprintEditToServer;
        CCClientHooks.CLIENT_PLAYER = () -> Minecraft.getInstance().player;
        CCClientHooks.SHIFT_DOWN = net.minecraft.client.gui.screens.Screen::hasShiftDown;
        dev.mccastle91.createcontrolling.compat.create.contraption.ConsoleMovingInteraction.CLIENT_CLICK = ContraptionConsoles::click;
        dev.mccastle91.createcontrolling.network.ContraptionSyncPayload.CLIENT_HANDLER = ContraptionConsoles::sync;
        modEventBus.addListener(CreateControllingClient::clientSetup);
        modEventBus.addListener(CreateControllingClient::registerRenderers);
        modEventBus.addListener(CreateControllingClient::swapConsoleModels);
        modEventBus.addListener(CreateControllingClient::registerBlockColors);
        modEventBus.addListener(CreateControllingClient::registerReloadListeners);
        //? if neoforge {
        NeoForge.EVENT_BUS.addListener(CreateControllingClient::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(AnalogDrag::onUseKey);
        NeoForge.EVENT_BUS.addListener(CreateControllingClient::onHighlight);
        NeoForge.EVENT_BUS.addListener(CreateControllingClient::onPlaySound);
        //?} else {
        /*MinecraftForge.EVENT_BUS.addListener(CreateControllingClient::onPlaySound);
        MinecraftForge.EVENT_BUS.addListener(CreateControllingClient::onMouseScroll);
        MinecraftForge.EVENT_BUS.addListener(AnalogDrag::onUseKey);
        MinecraftForge.EVENT_BUS.addListener(CreateControllingClient::onHighlight);
        *///?}
        PonderIndex.addPlugin(new CCPonderPlugin());
    }

    /** The blueprint's icon shows whether it holds a design: its item model switches on this property. */
    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
                CCContent.PANEL_BLUEPRINT.get(),
                McCompat.id(dev.mccastle91.createcontrolling.CreateControlling.MOD_ID, "filled"),
                (stack, level, entity, seed) -> PanelBlueprintItem.partCount(stack) > 0 ? 1f : 0f));
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CCContent.CONTROL_PANEL_ENTITY.get(),
                context -> new PanelBlockEntityRenderer<>());
    }

    /**
     * The console body is generated, not modelled: wrap the placeholder JSON
     * model of every console blockstate (and the item) in the generator.
     */
    private static void swapConsoleModels(ModelEvent.ModifyBakingResult event) {
        @SuppressWarnings({ "unchecked", "rawtypes" })
        Map<Object, BakedModel> models = (Map) event.getModels();
        swapModelsOf(models, CCContent.CONTROL_PANEL.get());
        swapModelsOf(models, CCContent.COPYCAT_CONTROL_PANEL.get());
    }

    private static void swapModelsOf(Map<Object, BakedModel> models, PanelBlock block) {
        ConsoleCasing casing = block.fixedCasing() == null ? ConsoleCasing.DEFAULT : block.fixedCasing();
        BakedModel original = null;
        ConsoleBakedModel console = null;
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            ModelResourceLocation location = BlockModelShaper.stateToModelLocation(state);
            BakedModel model = models.get(location);
            if (model == null)
                continue;
            if (model != original) {
                // all states share one JSON model, so one generator (and one cache)
                original = model;
                console = new ConsoleBakedModel(model, casing);
            }
            models.put(location, console);
        }
        ModelResourceLocation item = new ModelResourceLocation(BuiltInRegistries.ITEM.getKey(block.asItem()), "inventory");
        BakedModel itemModel = models.get(item);
        if (itemModel != null)
            models.put(item, new ConsoleBakedModel(itemModel, casing));
    }

    /** Colours and window textures a resource pack may have changed are read again on every reload. */
    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(ConsoleStyle.INSTANCE);
    }

    /** A copycat console is tinted as the block it copies is (grass, leaves, water). */
    private static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (level != null && pos != null && level.getBlockEntity(pos) instanceof PanelBlockEntity panel
                    && panel.material() != null)
                return Minecraft.getInstance().getBlockColors().getColor(panel.material(), level, pos, tintIndex);
            return -1;
        }, CCContent.COPYCAT_CONTROL_PANEL.get());
    }

    /** With part sounds switched off in the client config, the parts' sound events are dropped before they play. */
    private static void onPlaySound(PlaySoundEvent event) {
        if (dev.mccastle91.createcontrolling.config.CCConfig.partSounds || event.getSound() == null)
            return;
        net.minecraft.resources.ResourceLocation id = event.getSound().getLocation();
        if (id.getNamespace().equals(dev.mccastle91.createcontrolling.CreateControlling.MOD_ID) && id.getPath().startsWith("part."))
            event.setSound(null);
    }

    /** Consoles draw their own highlight: the aimed component, or the wedge. */
    private static void onHighlight(RenderHighlightEvent.Block event) {
        if (ConsoleOutline.render(event.getTarget(), event.getCamera().getPosition(),
                event.getPoseStack(), event.getMultiBufferSource()))
            event.setCanceled(true);
    }

    /** Sneak + scroll while aiming at a knob or slider nudges it, analog-lever style. */
    private static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !mc.player.isShiftKeyDown() || AnalogDrag.holdsCableTool(mc.player))
            return;
        AnalogDrag.Aim aim = AnalogDrag.aimedAnalog(mc);
        if (aim == null)
            return;
        //? if neoforge {
        int delta = (int) Math.signum(event.getScrollDeltaY());
        //?} else {
        /*int delta = (int) Math.signum(event.getScrollDelta());
        *///?}
        if (delta == 0)
            return;
        CompoundTag op = new CompoundTag();
        op.putString(PanelEditOps.KEY_OP, PanelEditOps.OP_ADJUST);
        op.putUUID("Id", aim.element().id());
        op.putInt("Delta", delta);
        CCNetwork.sendEditToServer(aim.pos(), op);
        event.setCanceled(true);
    }
}
