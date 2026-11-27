package dev.mccastle91.createcontrolling.client;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import com.simibubi.create.content.contraptions.sync.ContraptionInteractionPacket;
import dev.mccastle91.createcontrolling.network.CCNetwork;
import dev.mccastle91.createcontrolling.network.ContraptionClickPayload;
import dev.mccastle91.createcontrolling.network.ContraptionSyncPayload;
import dev.mccastle91.createcontrolling.panel.ConsolePicker;
import dev.mccastle91.createcontrolling.panel.PanelBlock;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Couple;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * The client's side of consoles that ride a contraption: which part a click
 * lands on, and the state the server sends for them. The console drawn there
 * is a block entity Create builds from the contraption's data, in a level of
 * its own; this reads and refreshes that one.
 */
public final class ContraptionConsoles {

    private ContraptionConsoles() {
    }

    /**
     * Create has found a console under the crosshair and asks whether the
     * click does anything. The ray goes into the contraption's own space,
     * where the console sits still, and is tried against its parts as a click
     * on a placed console is. Create took the whole block for the console
     * (see {@code WrenchablePanelBlock#getShape}), so a ray that finds no part
     * may only have crossed the air above a low console: the click is then
     * passed on to whatever the contraption has behind it.
     */
    public static boolean click(AbstractContraptionEntity entity, BlockPos localPos, InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Contraption contraption = entity.getContraption();
        if (player == null || contraption == null)
            return false;
        // the wrench is Create's on a contraption (it relocates trains)
        if (BuiltInRegistries.ITEM.getKey(player.getItemInHand(hand).getItem()).toString().equals("create:wrench"))
            return false;
        Couple<Vec3> ray = ContraptionHandlerClient.getRayInputs(player);
        if (contraption.getBlockEntityClientSide(localPos) instanceof PanelBlockEntity panel) {
            // first where the cabin is drawn this frame, then where Create's own ray found it
            for (float partialTicks : new float[] { AnimationTickHolder.getPartialTicks(), 1 }) {
                Optional<ConsolePicker.Pick> pick = pick(entity, localPos, panel, ray, partialTicks);
                if (pick.isEmpty())
                    continue;
                ConsolePicker.Pick hit = pick.get();
                boolean sneaking = player.isShiftKeyDown();
                // sneak-click opens the designer on a placed console; there is none on a contraption
                if (sneaking && !hit.element().takesSneakClicks())
                    return false;
                CCNetwork.sendToServer(new ContraptionClickPayload(entity.getId(), localPos, hit.element().id(),
                        hit.zone(), hit.localX(), hit.localY(), sneaking));
                return true;
            }
        }
        passOn(entity, localPos, hand, player, ray);
        return false;
    }

    private static Optional<ConsolePicker.Pick> pick(AbstractContraptionEntity entity, BlockPos localPos,
                                                     PanelBlockEntity panel, Couple<Vec3> ray, float partialTicks) {
        Vec3 corner = Vec3.atLowerCornerOf(localPos);
        Vec3 eye = entity.toLocalVector(ray.getFirst(), partialTicks).subtract(corner);
        Vec3 target = entity.toLocalVector(ray.getSecond(), partialTicks).subtract(corner);
        return ConsolePicker.pick(panel.getBlockState(), panel.shape(), panel.getLayout(), eye, target.subtract(eye));
    }

    /** Carries the click on along the ray, from where it leaves the console's block. */
    private static void passOn(AbstractContraptionEntity entity, BlockPos localPos, InteractionHand hand,
                               LocalPlayer player, Couple<Vec3> ray) {
        Vec3 from = entity.toLocalVector(ray.getFirst(), 1);
        Vec3 direction = entity.toLocalVector(ray.getSecond(), 1).subtract(from);
        double exit = exitOf(localPos, from, direction);
        if (exit < 0 || exit >= 1)
            return;
        // the same fraction of the way along the ray in the world as in the contraption's space
        Vec3 restart = ray.getFirst().add(ray.getSecond().subtract(ray.getFirst()).scale(Math.min(1, exit + 1e-3)));
        BlockHitResult next = ContraptionHandlerClient.rayTraceContraption(restart, ray.getSecond(), entity);
        if (next == null || next.getBlockPos().equals(localPos))
            return;
        if (!entity.handlePlayerInteraction(player, next.getBlockPos(), next.getDirection(), hand))
            return;
        ContraptionInteractionPacket packet = new ContraptionInteractionPacket(entity, hand, next.getBlockPos(), next.getDirection());
        //? if neoforge {
        net.createmod.catnip.platform.CatnipServices.NETWORK.sendToServer(packet);
        //?} else {
        /*com.simibubi.create.AllPackets.getChannel().sendToServer(packet);
        *///?}
    }

    /** How far along the ray (0 at its start, 1 at its end) it leaves the block at {@code pos}; -1 if it never enters. */
    private static double exitOf(BlockPos pos, Vec3 from, Vec3 direction) {
        double enter = 0;
        double exit = Double.MAX_VALUE;
        double[] origin = { from.x, from.y, from.z };
        double[] dir = { direction.x, direction.y, direction.z };
        double[] low = { pos.getX(), pos.getY(), pos.getZ() };
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(dir[axis]) < 1e-9) {
                if (origin[axis] < low[axis] || origin[axis] > low[axis] + 1)
                    return -1;
                continue;
            }
            double a = (low[axis] - origin[axis]) / dir[axis];
            double b = (low[axis] + 1 - origin[axis]) / dir[axis];
            enter = Math.max(enter, Math.min(a, b));
            exit = Math.min(exit, Math.max(a, b));
        }
        return enter > exit ? -1 : exit;
    }

    /**
     * The server's word on a console of a contraption in view. It goes into
     * the contraption's data, which Create rebuilds its client block entities
     * from now and then, and into the block entity being drawn.
     */
    public static void sync(ContraptionSyncPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(payload.entityId()) instanceof AbstractContraptionEntity entity))
            return;
        Contraption contraption = entity.getContraption();
        if (contraption == null)
            return;
        BlockPos localPos = payload.localPos();
        StructureBlockInfo info = contraption.getBlocks().get(localPos);
        if (info == null || !(info.state().getBlock() instanceof PanelBlock))
            return;
        CompoundTag data = info.nbt();
        if (data == null) {
            contraption.getBlocks().put(localPos, new StructureBlockInfo(info.pos(), info.state(), payload.tag().copy()));
        } else {
            for (String key : List.copyOf(data.getAllKeys()))
                data.remove(key);
            data.merge(payload.tag());
        }
        if (contraption.getBlockEntityClientSide(localPos) instanceof PanelBlockEntity panel) {
            //? if >=1.21 {
            panel.handleUpdateTag(payload.tag(), mc.level.registryAccess());
            //?} else {
            /*panel.handleUpdateTag(payload.tag());
            *///?}
        }
    }
}
