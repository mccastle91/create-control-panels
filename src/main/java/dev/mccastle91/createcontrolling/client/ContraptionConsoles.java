package dev.mccastle91.createcontrolling.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ContraptionHandler;
import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import com.simibubi.create.content.contraptions.sync.ContraptionInteractionPacket;
import com.simibubi.create.foundation.utility.RaycastHelper;
import dev.mccastle91.createcontrolling.config.CCConfig;
import dev.mccastle91.createcontrolling.network.CCNetwork;
import dev.mccastle91.createcontrolling.network.ContraptionClickPayload;
import dev.mccastle91.createcontrolling.network.ContraptionSyncPayload;
import dev.mccastle91.createcontrolling.network.ContraptionValuePayload;
import dev.mccastle91.createcontrolling.panel.ConsolePicker;
import dev.mccastle91.createcontrolling.panel.PanelBlock;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.element.AnalogElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.util.McCompat;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Couple;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
//? if neoforge {
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
//?} else {
/*import net.minecraftforge.client.event.RenderLevelStageEvent;
*///?}

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The client's side of consoles that ride a contraption: which part the
 * crosshair is on, its highlight, what a click, a held click or a scroll on it
 * does, and the state the server sends for them. The console drawn there is a
 * block entity Create builds from the contraption's data, in a level of its
 * own; this reads and refreshes that one.
 *
 * <p>Aiming is worked out where the contraption is drawn this frame, between
 * its last tick and this one, so the highlight sits on the part the player
 * sees and a click lands on it, however fast the cabin moves.
 */
public final class ContraptionConsoles {

    /** How far around the ray contraptions are looked for; Create's own figure (their anchor may be far from their blocks). */
    private static final double SEARCH = 16;

    /** A part of a console on a contraption, under the crosshair. */
    public record Aim(AbstractContraptionEntity entity, BlockPos localPos, PanelBlockEntity panel, ConsolePicker.Pick pick) {
    }

    private ContraptionConsoles() {
    }

    // --- Aiming ----------------------------------------------------------------

    /** The part under the crosshair, if it is on a contraption and nothing nearer is in the way. */
    @Nullable
    public static Aim aim() {
        return aim(AnimationTickHolder.getPartialTicks());
    }

    @Nullable
    private static Aim aim(float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isSpectator())
            return null;
        Vec3 eye = player.getEyePosition(partialTicks);
        double reach = McCompat.blockReach(player);
        // whatever the game itself found under the crosshair is in front of anything further away
        if (mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS)
            reach = Math.min(reach, mc.hitResult.getLocation().distanceTo(eye));
        if (reach < 1e-3)
            return null;
        Vec3 sight = player.getViewVector(partialTicks).scale(reach);
        AABB around = new AABB(eye, eye.add(sight)).inflate(SEARCH);

        Met nearest = null;
        for (WeakReference<AbstractContraptionEntity> ref : ContraptionHandler.loadedContraptions.get(mc.level).values()) {
            AbstractContraptionEntity entity = ref.get();
            if (entity == null || entity.isRemoved() || entity.getContraption() == null
                    || !entity.getBoundingBox().intersects(around))
                continue;
            // the ray, from the eye, in the contraption's own space
            Matrix4f toLocal = drawn(entity, eye, partialTicks).invert();
            Vec3 from = moved(toLocal, Vec3.ZERO);
            Vec3 to = moved(toLocal, sight);
            Met met = trace(entity, from, to);
            if (met != null && (nearest == null || met.distance < nearest.distance))
                nearest = met;
        }
        return nearest == null ? null : nearest.aim;
    }

    /** What a ray met first on a contraption: a console's part, or (no aim) something that hides whatever is behind. */
    private record Met(double distance, @Nullable Aim aim) {
    }

    /**
     * Follows the ray through a contraption's blocks, both in its own space.
     * A console stops it at a part or at its body, and lets it through the
     * air above a low one; any other block stops it as it does Create's ray.
     */
    @Nullable
    private static Met trace(AbstractContraptionEntity entity, Vec3 from, Vec3 to) {
        Contraption contraption = entity.getContraption();
        Met[] met = new Met[1];
        RaycastHelper.rayTraceUntil(from, to, cell -> {
            StructureBlockInfo info = contraption.getBlocks().get(cell);
            if (info == null || contraption.isHiddenInPortal(cell))
                return false;
            if (info.state().getBlock() instanceof PanelBlock
                    && contraption.getBlockEntityClientSide(cell) instanceof PanelBlockEntity panel) {
                Optional<ConsolePicker.Pick> pick = pick(panel, cell, from, to);
                if (pick.isPresent()) {
                    // parts sit inside their console's block: where the ray enters it is near enough
                    double distance = new AABB(cell).clip(from, to).map(from::distanceTo).orElse(0.0);
                    met[0] = new Met(distance, new Aim(entity, cell.immutable(), panel, pick.get()));
                    return true;
                }
                BlockHitResult body = panel.selectionShape().clip(from, to, cell);
                if (body == null)
                    return false;
                met[0] = new Met(body.getLocation().distanceTo(from), null);
                return true;
            }
            VoxelShape shape = info.state().getShape(contraption.getContraptionWorld(), BlockPos.ZERO.below());
            BlockHitResult hit = shape.isEmpty() ? null : shape.clip(from, to, cell);
            if (hit == null)
                return false;
            met[0] = new Met(hit.getLocation().distanceTo(from), null);
            return true;
        });
        return met[0];
    }

    private static Optional<ConsolePicker.Pick> pick(PanelBlockEntity panel, BlockPos localPos, Vec3 from, Vec3 to) {
        Vec3 corner = Vec3.atLowerCornerOf(localPos);
        return ConsolePicker.pick(panel.getBlockState(), panel.shape(), panel.getLayout(), from.subtract(corner), to.subtract(from));
    }

    /**
     * Moves the pose from {@code origin} (a point of the world) into the
     * contraption's space as its renderer does: to where the entity is this
     * frame, then Create's own turn and nudge.
     */
    private static void place(PoseStack pose, AbstractContraptionEntity entity, Vec3 origin, float partialTicks) {
        pose.translate(Mth.lerp(partialTicks, entity.xOld, entity.getX()) - origin.x,
                Mth.lerp(partialTicks, entity.yOld, entity.getY()) - origin.y,
                Mth.lerp(partialTicks, entity.zOld, entity.getZ()) - origin.z);
        entity.applyLocalTransforms(pose, partialTicks);
    }

    /** Contraption space to the world as drawn, measured from {@code origin} so the numbers stay small. */
    private static Matrix4f drawn(AbstractContraptionEntity entity, Vec3 origin, float partialTicks) {
        PoseStack pose = new PoseStack();
        place(pose, entity, origin, partialTicks);
        return new Matrix4f(pose.last().pose());
    }

    private static Vec3 moved(Matrix4f matrix, Vec3 point) {
        Vector3f moved = matrix.transformPosition(new Vector3f((float) point.x, (float) point.y, (float) point.z));
        return new Vec3(moved.x(), moved.y(), moved.z());
    }

    // --- Highlight -------------------------------------------------------------

    /**
     * The outline of the aimed part, drawn where the world's own block
     * highlight is (the game has none for blocks of a contraption).
     */
    static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui)
            return;
        float partialTicks = AnimationTickHolder.getPartialTicks();
        Aim aim = aim(partialTicks);
        if (aim == null)
            return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        place(pose, aim.entity(), event.getCamera().getPosition(), partialTicks);
        pose.translate(aim.localPos().getX(), aim.localPos().getY(), aim.localPos().getZ());
        ConsoleOutline.part(aim.panel(), aim.pick(), pose, buffers);
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    // --- Clicks, drags and scrolls -----------------------------------------------

    /**
     * Create has found a console under the crosshair and asks whether the
     * click does anything. It does if a part is aimed at: a knob, slider or
     * analog lever opens the drag (a tap steps it up, as on a placed console),
     * anything else is clicked. Create took the whole block for the console
     * (see {@code WrenchablePanelBlock#getShape}), so with no part aimed at
     * the ray may only have crossed the air above a low console: the click is
     * then passed on to whatever the contraption has behind it.
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
        Aim aim = aim();
        if (aim == null && contraption.getBlockEntityClientSide(localPos) instanceof PanelBlockEntity panel) {
            // not where the console is drawn: then where Create's own ray, a tick ahead, found it
            Vec3 from = entity.toLocalVector(ray.getFirst(), 1);
            aim = pick(panel, localPos, from, entity.toLocalVector(ray.getSecond(), 1))
                    .map(pick -> new Aim(entity, localPos, panel, pick)).orElse(null);
        }
        if (aim == null) {
            passOn(entity, localPos, hand, player, ray);
            return false;
        }
        PanelElement element = aim.pick().element();
        boolean sneaking = player.isShiftKeyDown();
        // sneak-click opens the designer on a placed console; there is none on a contraption
        if (sneaking && !element.takesSneakClicks())
            return false;
        if (!sneaking && element instanceof AnalogElement analog) {
            mc.setScreen(new AnalogDragScreen(dragTarget(aim.entity(), aim.localPos()), analog, CCConfig.analogPopup));
            return true;
        }
        CCNetwork.sendToServer(new ContraptionClickPayload(aim.entity().getId(), aim.localPos(), element.id(),
                aim.pick().zone(), aim.pick().localX(), aim.pick().localY(), sneaking));
        return true;
    }

    /**
     * A scroll over a part: steps the aimed floor selector to the next floor
     * (as on Create's Contraption Controls, no sneaking needed), or, sneaking,
     * nudges the aimed knob, slider or analog lever. True if the scroll was
     * for a console part.
     */
    static boolean scroll(int delta, boolean sneaking) {
        Aim aim = aim();
        if (aim == null)
            return false;
        PanelElement element = aim.pick().element();
        boolean selector = element instanceof dev.mccastle91.createcontrolling.panel.element.ActorSwitchElement actors
                && !actors.floorName().isEmpty();
        if (!selector && !(sneaking && element instanceof AnalogElement))
            return false;
        CCNetwork.sendToServer(new ContraptionValuePayload(aim.entity().getId(), aim.localPos(), element.id(), delta, true));
        return true;
    }

    /** What a drag works on when its console rides a contraption. */
    private static AnalogDragScreen.Target dragTarget(AbstractContraptionEntity entity, BlockPos localPos) {
        return new AnalogDragScreen.Target() {
            @Override
            public PanelBlockEntity console() {
                Contraption contraption = entity.isRemoved() ? null : entity.getContraption();
                return contraption != null && contraption.getBlockEntityClientSide(localPos) instanceof PanelBlockEntity panel
                        ? panel : null;
            }

            @Override
            public void send(UUID elementId, int value) {
                CCNetwork.sendToServer(new ContraptionValuePayload(entity.getId(), localPos, elementId, value, false));
            }
        };
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

    // --- State from the server ---------------------------------------------------

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
