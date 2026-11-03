package dev.mccastle91.createcontrolling.client;

import dev.mccastle91.createcontrolling.client.model.ConsoleBakedModel;
import dev.mccastle91.createcontrolling.compat.ponder.CCPonderPlugin;
import dev.mccastle91.createcontrolling.network.CCNetwork;
import dev.mccastle91.createcontrolling.panel.ConsolePicker;
import dev.mccastle91.createcontrolling.panel.PanelBlock;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.PanelEditOps;
import dev.mccastle91.createcontrolling.panel.element.AnalogElement;
import dev.mccastle91.createcontrolling.registry.CCContent;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
//? if neoforge {
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.common.NeoForge;
//?} else {
/*import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
*///?}

import java.util.Map;

/** Client-only wiring; never classloaded on dedicated servers. */
public final class CreateControllingClient {

    private CreateControllingClient() {
    }

    public static void init(IEventBus modEventBus) {
        CCClientHooks.OPEN_DESIGNER = pos -> Minecraft.getInstance().setScreen(new PanelDesignerScreen(pos));
        CCClientHooks.SEND_EDIT = CCNetwork::sendEditToServer;
        modEventBus.addListener(CreateControllingClient::registerRenderers);
        modEventBus.addListener(CreateControllingClient::swapConsoleModels);
        //? if neoforge {
        NeoForge.EVENT_BUS.addListener(CreateControllingClient::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(CreateControllingClient::onHighlight);
        //?} else {
        /*MinecraftForge.EVENT_BUS.addListener(CreateControllingClient::onMouseScroll);
        MinecraftForge.EVENT_BUS.addListener(CreateControllingClient::onHighlight);
        *///?}
        PonderIndex.addPlugin(new CCPonderPlugin());
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
        Block block = CCContent.CONTROL_PANEL.get();
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
                console = new ConsoleBakedModel(model);
            }
            models.put(location, console);
        }
        ModelResourceLocation item = new ModelResourceLocation(BuiltInRegistries.ITEM.getKey(block.asItem()), "inventory");
        BakedModel itemModel = models.get(item);
        if (itemModel != null)
            models.put(item, new ConsoleBakedModel(itemModel));
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
        if (mc.level == null || mc.player == null || mc.screen != null || !mc.player.isShiftKeyDown())
            return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK)
            return;
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (!(state.getBlock() instanceof PanelBlock) || !(mc.level.getBlockEntity(pos) instanceof PanelBlockEntity panel))
            return;
        Vec3 origin = Vec3.atLowerCornerOf(pos);
        Vec3 eye = mc.player.getEyePosition().subtract(origin);
        Vec3 direction = hit.getLocation().subtract(origin).subtract(eye);
        var pick = ConsolePicker.pick(state, panel.shape(), panel.getLayout(), eye, direction);
        if (pick.isEmpty() || !(pick.get().element() instanceof AnalogElement analog))
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
        op.putUUID("Id", analog.id());
        op.putInt("Delta", delta);
        CCNetwork.sendEditToServer(pos, op);
        event.setCanceled(true);
    }
}
