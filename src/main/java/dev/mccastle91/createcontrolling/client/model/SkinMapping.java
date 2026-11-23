package dev.mccastle91.createcontrolling.client.model;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * How a copied block's texture lies on one of its sides, read off the quad
 * its model draws there, so the console can lay it the same way.
 *
 * A model says where its texture goes per quad corner. Between a point on a
 * block's side and the texture there is, for any flat quad, one linear map;
 * this finds it, in terms of the texture px vanilla gives that point by
 * default ({@link #defaultUv}). Applying it to the console's own corners
 * then carries over whatever the model does: a log lying on its side (its
 * bark turned a quarter), a texture that is mirrored or only part of its
 * sprite, and Create's connected textures, which work by moving a quad's
 * corners onto another tile of a larger sheet.
 *
 * The map is six numbers: atlas u = m0 + m1·du + m2·dv, atlas v = m3 + m4·du + m5·dv.
 */
public final class SkinMapping {

    /** A quad narrower than this (texture px) is a detail, not the side's texture: it isn't stretched over the console. */
    private static final float MIN_EXTENT = 12f;

    private SkinMapping() {
    }

    /**
     * The texture px (0-16) vanilla maps to a point of a block's side when a
     * model doesn't say otherwise: u runs to the viewer's right and v down on
     * the four walls; the top reads as seen from above with north up.
     *
     * @param x,y,z the point within the block, 0-1
     * @param out   receives u, v
     */
    public static void defaultUv(Direction side, double x, double y, double z, float[] out) {
        double u, v;
        switch (side) {
            case UP -> { u = x; v = z; }
            case DOWN -> { u = x; v = 1 - z; }
            case NORTH -> { u = 1 - x; v = 1 - y; }
            case SOUTH -> { u = x; v = 1 - y; }
            case WEST -> { u = z; v = 1 - y; }
            default -> { u = 1 - z; v = 1 - y; }
        }
        out[0] = (float) (u * 16);
        out[1] = (float) (v * 16);
    }

    /**
     * The map of a baked quad drawn on {@code side}, or null if it has none
     * worth using: a quad that covers only part of the side, or a degenerate one.
     *
     * @param vertices the quad's packed vertices, block format: x, y, z, colour, u, v, ...
     */
    @Nullable
    public static float[] of(int[] vertices, Direction side) {
        int stride = vertices.length / 4;
        if (stride < 6)
            return null;
        float[] du = new float[4], dv = new float[4], u = new float[4], v = new float[4];
        float[] out = new float[2];
        for (int i = 0; i < 4; i++) {
            int at = i * stride;
            defaultUv(side, Float.intBitsToFloat(vertices[at]), Float.intBitsToFloat(vertices[at + 1]),
                    Float.intBitsToFloat(vertices[at + 2]), out);
            du[i] = out[0];
            dv[i] = out[1];
            u[i] = Float.intBitsToFloat(vertices[at + 4]);
            v[i] = Float.intBitsToFloat(vertices[at + 5]);
        }
        return of(du, dv, u, v);
    }

    /** As {@link #of(int[], Direction)}, from the four corners' default px and atlas coordinates. */
    @Nullable
    public static float[] of(float[] du, float[] dv, float[] u, float[] v) {
        if (extent(du) < MIN_EXTENT || extent(dv) < MIN_EXTENT)
            return null;
        // any three corners that aren't in a line fix the map
        int[][] triples = { { 0, 1, 2 }, { 0, 1, 3 }, { 0, 2, 3 }, { 1, 2, 3 } };
        for (int[] t : triples) {
            int a = t[0], b = t[1], c = t[2];
            float bx = du[b] - du[a], by = dv[b] - dv[a], cx = du[c] - du[a], cy = dv[c] - dv[a];
            float det = bx * cy - cx * by;
            if (Math.abs(det) < 1e-3f)
                continue;
            float[] map = new float[6];
            solve(map, 0, u, a, b, c, bx, by, cx, cy, det, du[a], dv[a]);
            solve(map, 3, v, a, b, c, bx, by, cx, cy, det, du[a], dv[a]);
            return map;
        }
        return null;
    }

    private static void solve(float[] map, int at, float[] value, int a, int b, int c,
                              float bx, float by, float cx, float cy, float det, float ax, float ay) {
        float vb = value[b] - value[a], vc = value[c] - value[a];
        float perU = (vb * cy - vc * by) / det;
        float perV = (bx * vc - cx * vb) / det;
        map[at] = value[a] - perU * ax - perV * ay;
        map[at + 1] = perU;
        map[at + 2] = perV;
    }

    private static float extent(float[] values) {
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (float value : values) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return max - min;
    }

    public static float u(float[] map, float du, float dv) {
        return map[0] + map[1] * du + map[2] * dv;
    }

    public static float v(float[] map, float du, float dv) {
        return map[3] + map[4] * du + map[5] * dv;
    }
}
