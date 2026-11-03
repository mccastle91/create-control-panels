package dev.mccastle91.createcontrolling.panel;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A console's face as a 2D surface in block-local space: {@code origin} is
 * design point (0, 0) — the far-left corner of the 16×16 px design area —
 * {@code u} runs right, {@code v} runs down the design (towards the player
 * on a floor console, away from them on a ceiling one, so it reads upright
 * from below), and {@code normal} points out of the face. All in blocks.
 *
 * In model space (see {@link ConsoleOrientation}) the face runs from the far
 * edge (z = 0) to the near edge (z = 1) at the heights the shape gives them
 * (see {@link ConsoleShape#farHeight()}), and the design area is 16 px of
 * slope centred on the face, its top at the far edge; a steep
 * face's extra length is bare plate split evenly above and below it (see
 * {@link ConsoleShape#designMargin()}).
 */
public record PanelFaceGeometry(Vec3 origin, Vec3 u, Vec3 v, Vec3 normal) {

    private static final double PX = 1 / 16.0;

    /** The frame in model space (floor console facing north). */
    public static PanelFaceGeometry model(ConsoleShape shape) {
        double far = shape.farHeight() * PX;
        double near = shape.nearHeight() * PX;
        double length = Math.sqrt((near - far) * (near - far) + 1);
        // down the face: from the far edge (z = 0) to the near edge (z = 1)
        Vec3 v = new Vec3(0, (near - far) / length, 1 / length);
        Vec3 u = new Vec3(1, 0, 0);
        // past the upper margin to the design area's top edge
        Vec3 origin = new Vec3(0, far, 0).add(v.scale(shape.designMargin() * PX));
        return new PanelFaceGeometry(origin, u, v, v.cross(u));
    }

    public static PanelFaceGeometry of(BlockState state, ConsoleShape shape) {
        PanelFaceGeometry model = model(shape);
        ConsoleOrientation orientation = ConsoleOrientation.of(state);
        return new PanelFaceGeometry(orientation.apply(model.origin), orientation.rotate(model.u),
                orientation.rotate(model.v), orientation.rotate(model.normal));
    }

    /** Point at design position ({@code px}, {@code py}) px, lifted {@code lift} px off the face. */
    public Vec3 point(double px, double py, double lift) {
        return origin.add(u.scale(px * PX)).add(v.scale(py * PX)).add(normal.scale(lift * PX));
    }

    /** A point in this frame's space expressed as (design x, design y, height off face), all px. */
    public Vec3 toFace(Vec3 p) {
        Vec3 d = p.subtract(origin);
        return new Vec3(d.dot(u) / PX, d.dot(v) / PX, d.dot(normal) / PX);
    }

    /** A direction expressed in face space, px per block. */
    public Vec3 toFaceDirection(Vec3 d) {
        return new Vec3(d.dot(u) / PX, d.dot(v) / PX, d.dot(normal) / PX);
    }
}
