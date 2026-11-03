package dev.mccastle91.createcontrolling.panel;

import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Voxel shapes for a console. Collision is the sloped body as a few stacked
 * boxes; the selection shape adds each component's bounds, so the outline
 * hugs raised parts and rays reach them even where they stand off the body.
 */
public final class ConsoleShapes {

    /** Slices along the slope: enough for a natural feel without many boxes. */
    private static final int SLICES = 4;
    private static final double PX = 1 / 16.0;

    private ConsoleShapes() {
    }

    public static VoxelShape collision(BlockState state, ConsoleShape shape) {
        ConsoleOrientation orientation = ConsoleOrientation.of(state);
        VoxelShape result = Shapes.empty();
        for (AABB box : bodyBoxes(shape))
            result = Shapes.or(result, Shapes.create(orientation.apply(box)));
        return result.optimize();
    }

    public static VoxelShape selection(BlockState state, ConsoleShape shape, PanelLayout layout) {
        ConsoleOrientation orientation = ConsoleOrientation.of(state);
        PanelFaceGeometry face = PanelFaceGeometry.model(shape);
        VoxelShape result = Shapes.empty();
        for (AABB box : bodyBoxes(shape))
            result = Shapes.or(result, Shapes.create(orientation.apply(box)));
        for (PanelElement element : layout.elements()) {
            if (element.height() <= 0)
                continue;
            result = Shapes.or(result, Shapes.create(orientation.apply(elementBox(face, element))));
        }
        return result.optimize();
    }

    /** Model-space body: slices from the far edge to the near edge. */
    private static AABB[] bodyBoxes(ConsoleShape shape) {
        double far = shape.farHeight() * PX;
        double near = shape.nearHeight() * PX;
        if (shape.isFlat())
            return new AABB[] { new AABB(0, 0, 0, 1, near, 1) };
        AABB[] boxes = new AABB[SLICES];
        for (int i = 0; i < SLICES; i++) {
            double z0 = i / (double) SLICES;
            double z1 = (i + 1) / (double) SLICES;
            // height runs linearly from the far edge (z=0) to the near edge (z=1); a slice's top is its high end
            double top = Math.max(far + (near - far) * z0, far + (near - far) * z1);
            boxes[i] = new AABB(0, 0, z0, 1, Math.max(PX, top), z1);
        }
        return boxes;
    }

    /** Model-space bounds of a component: its footprint corners, at the face and lifted by its height. */
    private static AABB elementBox(PanelFaceGeometry face, PanelElement element) {
        double x0 = element.x();
        double y0 = element.y();
        double x1 = x0 + element.width();
        double y1 = y0 + element.depth();
        double h = element.height();
        Vec3 min = null;
        Vec3 max = null;
        for (double px : new double[] { x0, x1 })
            for (double py : new double[] { y0, y1 })
                for (double lift : new double[] { 0, h }) {
                    Vec3 p = face.point(px, py, lift);
                    min = min == null ? p : new Vec3(Math.min(min.x, p.x), Math.min(min.y, p.y), Math.min(min.z, p.z));
                    max = max == null ? p : new Vec3(Math.max(max.x, p.x), Math.max(max.y, p.y), Math.max(max.z, p.z));
                }
        return new AABB(min, max);
    }
}
