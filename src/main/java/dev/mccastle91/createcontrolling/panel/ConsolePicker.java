package dev.mccastle91.createcontrolling.panel;

import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Finds which component a player is pointing at: the eye ray, moved into
 * face space, against each clickable component's 3D box (footprint × its
 * height off the face). The nearest hit wins, so you press the raised cap
 * you see rather than a cell of the face behind it.
 */
public final class ConsolePicker {

    /** Thin parts still need something to hit. */
    public static final double MIN_HEIGHT = 0.25;

    public record Pick(PanelElement element, float localX, float localY) {
    }

    private ConsolePicker() {
    }

    /**
     * @param eye       ray start, block-local (relative to the console's position)
     * @param direction ray direction (any length), block-local
     */
    public static Optional<Pick> pick(BlockState state, ConsoleShape shape, PanelLayout layout, Vec3 eye, Vec3 direction) {
        PanelFaceGeometry face = PanelFaceGeometry.of(state, shape);
        Vec3 o = face.toFace(eye);
        Vec3 d = face.toFaceDirection(direction);

        PanelElement best = null;
        double bestT = Double.MAX_VALUE;
        for (PanelElement element : layout.elements()) {
            if (!element.takesClicks())
                continue;
            double t = intersect(o, d, element.x(), element.y(), 0,
                    element.x() + element.width(), element.y() + element.depth(),
                    Math.max(MIN_HEIGHT, element.height()));
            if (t >= 0 && t < bestT) {
                bestT = t;
                best = element;
            }
        }
        if (best == null)
            return Optional.empty();
        Vec3 hit = o.add(d.scale(bestT));
        float localX = (float) Math.max(0, Math.min(best.width(), hit.x - best.x()));
        float localY = (float) Math.max(0, Math.min(best.depth(), hit.y - best.y()));
        return Optional.of(new Pick(best, localX, localY));
    }

    /** Slab test: ray parameter of the first hit on the box, or -1. */
    static double intersect(Vec3 o, Vec3 d, double minX, double minY, double minZ,
                            double maxX, double maxY, double maxZ) {
        double tMin = 0;
        double tMax = Double.MAX_VALUE;
        double[] origin = { o.x, o.y, o.z };
        double[] dir = { d.x, d.y, d.z };
        double[] lo = { minX, minY, minZ };
        double[] hi = { maxX, maxY, maxZ };
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(dir[axis]) < 1e-9) {
                if (origin[axis] < lo[axis] || origin[axis] > hi[axis])
                    return -1;
                continue;
            }
            double t1 = (lo[axis] - origin[axis]) / dir[axis];
            double t2 = (hi[axis] - origin[axis]) / dir[axis];
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
            if (tMin > tMax)
                return -1;
        }
        return tMin;
    }
}
