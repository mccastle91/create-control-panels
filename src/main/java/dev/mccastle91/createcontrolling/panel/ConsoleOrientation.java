package dev.mccastle91.createcontrolling.panel;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Maps console "model space" — a floor console facing north, face up, the
 * player standing south (+z) — onto each of the 12 face/facing placements,
 * as a rotation about the block centre. Body mesh, component renderer, click
 * rays, selection shapes and border connections all go through this one
 * mapping, so they agree by construction.
 *
 * Each placement is given by where model +x (the face's right) and model +z
 * (down the face, towards the player) end up; the outward normal (model +y)
 * follows as {@code v × u}. Floor and wall axes are v1's in-game-verified face
 * tables; the ceiling's are turned 180° from v1's so the face reads the right
 * way up from below.
 */
public final class ConsoleOrientation {

    private static final Vec3 CENTER = new Vec3(0.5, 0.5, 0.5);
    private static final ConsoleOrientation[] TABLE = new ConsoleOrientation[3 * 4];

    static {
        // floor
        put(AttachFace.FLOOR, Direction.NORTH, new Vec3(1, 0, 0), new Vec3(0, 0, 1));
        put(AttachFace.FLOOR, Direction.SOUTH, new Vec3(-1, 0, 0), new Vec3(0, 0, -1));
        put(AttachFace.FLOOR, Direction.EAST, new Vec3(0, 0, 1), new Vec3(-1, 0, 0));
        put(AttachFace.FLOOR, Direction.WEST, new Vec3(0, 0, -1), new Vec3(1, 0, 0));
        // ceiling (face down; "down the face" points away from the player, so
        // the design reads upright to someone below looking up at it — which
        // puts the player at model z = 0, hence ceiling shapes are reversed)
        put(AttachFace.CEILING, Direction.NORTH, new Vec3(1, 0, 0), new Vec3(0, 0, -1));
        put(AttachFace.CEILING, Direction.SOUTH, new Vec3(-1, 0, 0), new Vec3(0, 0, 1));
        put(AttachFace.CEILING, Direction.EAST, new Vec3(0, 0, 1), new Vec3(1, 0, 0));
        put(AttachFace.CEILING, Direction.WEST, new Vec3(0, 0, -1), new Vec3(-1, 0, 0));
        // wall (face points along FACING; "down the face" is world down)
        put(AttachFace.WALL, Direction.NORTH, new Vec3(-1, 0, 0), new Vec3(0, -1, 0));
        put(AttachFace.WALL, Direction.SOUTH, new Vec3(1, 0, 0), new Vec3(0, -1, 0));
        put(AttachFace.WALL, Direction.EAST, new Vec3(0, 0, -1), new Vec3(0, -1, 0));
        put(AttachFace.WALL, Direction.WEST, new Vec3(0, 0, 1), new Vec3(0, -1, 0));
    }

    private final Vec3 u;
    private final Vec3 v;
    private final Vec3 n;

    private ConsoleOrientation(Vec3 u, Vec3 v) {
        this.u = u;
        this.v = v;
        this.n = v.cross(u);
    }

    private static void put(AttachFace face, Direction facing, Vec3 u, Vec3 v) {
        TABLE[index(face, facing)] = new ConsoleOrientation(u, v);
    }

    private static int index(AttachFace face, Direction facing) {
        return face.ordinal() * 4 + facing.get2DDataValue();
    }

    public static ConsoleOrientation of(AttachFace face, Direction facing) {
        return TABLE[index(face, facing)];
    }

    public static ConsoleOrientation of(BlockState state) {
        return of(state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE),
                state.getValue(HorizontalDirectionalBlock.FACING));
    }

    /** Model-space direction → block-local direction. */
    public Vec3 rotate(Vec3 d) {
        return u.scale(d.x).add(n.scale(d.y)).add(v.scale(d.z));
    }

    /** Block-local direction → model-space direction (the transpose). */
    public Vec3 unrotate(Vec3 d) {
        return new Vec3(d.dot(u), d.dot(n), d.dot(v));
    }

    /** Model-space point → block-local point (rotation about the block centre). */
    public Vec3 apply(Vec3 p) {
        return rotate(p.subtract(CENTER)).add(CENTER);
    }

    /** Block-local point → model-space point. */
    public Vec3 unapply(Vec3 p) {
        return unrotate(p.subtract(CENTER)).add(CENTER);
    }

    /** A model-space box in block-local space (exact: rotations are quarter turns). */
    public AABB apply(AABB box) {
        Vec3 a = apply(new Vec3(box.minX, box.minY, box.minZ));
        Vec3 b = apply(new Vec3(box.maxX, box.maxY, box.maxZ));
        return new AABB(a, b);
    }

    /** Where a model-space horizontal edge direction points in the world. */
    public Direction rotate(Direction modelDirection) {
        Vec3 d = rotate(Vec3.atLowerCornerOf(modelDirection.getNormal()));
        return Direction.getNearest(d.x, d.y, d.z);
    }

    /** Outward face normal (model +y) in block-local space. */
    public Vec3 normal() {
        return n;
    }

    /** Determinant of the rotation — 1 for every valid placement (checked by tests). */
    public double determinant() {
        return u.dot(n.cross(v));
    }
}
