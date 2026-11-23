package dev.mccastle91.createcontrolling.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mccastle91.createcontrolling.panel.ConsoleOrientation;
import dev.mccastle91.createcontrolling.panel.ConsolePicker;
import dev.mccastle91.createcontrolling.panel.ConsoleShape;
import dev.mccastle91.createcontrolling.panel.PanelBlock;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.PanelFaceGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * The block highlight for consoles. The selection voxel shape is a stack of
 * boxes (so rays reach raised parts), which outlines as a stepped mess; this
 * draws instead the component under the crosshair, tilted with the face (for
 * a part with several click zones, the one aimed at: a guarded switch's lever
 * or its raised lid), or — when none is aimed at — the console's true wedge.
 */
public final class ConsoleOutline {

    // drawn in StyleColor.HIGHLIGHT: vanilla's highlight colour, unless a resource pack changes it

    private ConsoleOutline() {
    }

    /** Draws the highlight for a console hit; false if the target isn't a console (leave it to vanilla). */
    public static boolean render(BlockHitResult hit, Vec3 camera, PoseStack poseStack, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos pos = hit.getBlockPos();
        if (mc.level == null || mc.player == null)
            return false;
        BlockState state = mc.level.getBlockState(pos);
        if (!(state.getBlock() instanceof PanelBlock) || !(mc.level.getBlockEntity(pos) instanceof PanelBlockEntity panel))
            return false;

        Vec3 origin = Vec3.atLowerCornerOf(pos);
        Vec3 eye = mc.player.getEyePosition().subtract(origin);
        Vec3 direction = hit.getLocation().subtract(origin).subtract(eye);
        Optional<ConsolePicker.Pick> pick = ConsolePicker.pick(state, panel.shape(), panel.getLayout(), eye, direction);

        poseStack.pushPose();
        poseStack.translate(origin.x - camera.x, origin.y - camera.y, origin.z - camera.z);
        Lines lines = new Lines(buffers.getBuffer(RenderType.lines()), poseStack.last());
        if (pick.isPresent())
            box(lines, PanelFaceGeometry.of(state, panel.shape()), pick.get().box());
        else
            body(lines, ConsoleOrientation.of(state), panel.shape());
        poseStack.popPose();
        return true;
    }

    /** The box the click ray hit ({x0, y0, z0, x1, y1, z1} in face px), standing on the face. */
    private static void box(Lines lines, PanelFaceGeometry face, float[] box) {
        double x0 = box[0], y0 = box[1], z0 = box[2], x1 = box[3], y1 = box[4], z1 = box[5];
        Vec3[] lo = { face.point(x0, y0, z0), face.point(x1, y0, z0), face.point(x1, y1, z0), face.point(x0, y1, z0) };
        Vec3[] hi = { face.point(x0, y0, z1), face.point(x1, y0, z1), face.point(x1, y1, z1), face.point(x0, y1, z1) };
        for (int i = 0; i < 4; i++) {
            lines.line(lo[i], lo[(i + 1) % 4]);
            lines.line(hi[i], hi[(i + 1) % 4]);
            lines.line(lo[i], hi[i]);
        }
    }

    /** The wedge: base, sloped top and its vertical edges (the low ones vanish on a knife edge). */
    private static void body(Lines lines, ConsoleOrientation orientation, ConsoleShape shape) {
        double far = shape.farHeight() / 16.0;
        double near = shape.nearHeight() / 16.0;
        Vec3[] lo = { new Vec3(0, 0, 0), new Vec3(1, 0, 0), new Vec3(1, 0, 1), new Vec3(0, 0, 1) };
        Vec3[] hi = { new Vec3(0, far, 0), new Vec3(1, far, 0), new Vec3(1, near, 1), new Vec3(0, near, 1) };
        for (int i = 0; i < 4; i++) {
            lo[i] = orientation.apply(lo[i]);
            hi[i] = orientation.apply(hi[i]);
        }
        for (int i = 0; i < 4; i++) {
            lines.line(lo[i], lo[(i + 1) % 4]);
            lines.line(hi[i], hi[(i + 1) % 4]);
            lines.line(lo[i], hi[i]);
        }
    }

    private record Lines(VertexConsumer consumer, PoseStack.Pose pose) {

        void line(Vec3 a, Vec3 b) {
            Vec3 d = b.subtract(a);
            double length = d.length();
            if (length < 1e-4)
                return;
            d = d.scale(1 / length);
            ClientCompat.lineVertex(consumer, pose, (float) a.x, (float) a.y, (float) a.z, StyleColor.HIGHLIGHT.argb(),
                    (float) d.x, (float) d.y, (float) d.z);
            ClientCompat.lineVertex(consumer, pose, (float) b.x, (float) b.y, (float) b.z, StyleColor.HIGHLIGHT.argb(),
                    (float) d.x, (float) d.y, (float) d.z);
        }
    }
}
