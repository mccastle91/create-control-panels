package dev.mccastle91.createcontrolling.client.model;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Reading a copied block's texture mapping off its quads. */
class SkinMappingTest {

    private static final float EPS = 1e-4f;

    /** A quad in the block vertex format: x, y, z, colour, u, v, light, normal per corner. */
    private static int[] quad(float[][] corners) {
        int[] data = new int[32];
        for (int i = 0; i < 4; i++) {
            data[i * 8] = Float.floatToRawIntBits(corners[i][0]);
            data[i * 8 + 1] = Float.floatToRawIntBits(corners[i][1]);
            data[i * 8 + 2] = Float.floatToRawIntBits(corners[i][2]);
            data[i * 8 + 3] = -1;
            data[i * 8 + 4] = Float.floatToRawIntBits(corners[i][3]);
            data[i * 8 + 5] = Float.floatToRawIntBits(corners[i][4]);
        }
        return data;
    }

    private static void assertUv(Direction side, double x, double y, double z, float u, float v) {
        float[] out = new float[2];
        SkinMapping.defaultUv(side, x, y, z, out);
        assertEquals(u, out[0], EPS, side + " u");
        assertEquals(v, out[1], EPS, side + " v");
    }

    /** Every corner of the quad must come out at its own texture coordinates. */
    private static void assertReproduces(float[] map, float[][] corners, Direction side) {
        float[] px = new float[2];
        for (float[] corner : corners) {
            SkinMapping.defaultUv(side, corner[0], corner[1], corner[2], px);
            assertEquals(corner[3], SkinMapping.u(map, px[0], px[1]), EPS);
            assertEquals(corner[4], SkinMapping.v(map, px[0], px[1]), EPS);
        }
    }

    @Test
    void defaultUvStartsAtEachSidesTopLeft() {
        // the corner a viewer facing the side sees at its top left is texture px (0, 0)
        assertUv(Direction.NORTH, 1, 1, 0, 0, 0);
        assertUv(Direction.SOUTH, 0, 1, 1, 0, 0);
        assertUv(Direction.WEST, 0, 1, 0, 0, 0);
        assertUv(Direction.EAST, 1, 1, 1, 0, 0);
        assertUv(Direction.UP, 0, 1, 0, 0, 0);
        assertUv(Direction.DOWN, 0, 0, 1, 0, 0);
        // and the opposite corner is (16, 16)
        assertUv(Direction.NORTH, 0, 0, 0, 16, 16);
        assertUv(Direction.UP, 1, 1, 1, 16, 16);
        // half-way up a wall is half-way down the texture
        assertUv(Direction.SOUTH, 0.25, 0.5, 1, 4, 8);
    }

    @Test
    void plainQuadMapsTheSpriteStraight() {
        // the top of a block, its sprite at u 0.25-0.5, v 0.5-0.75 of the atlas
        float[][] corners = {
                { 0, 1, 0, 0.25f, 0.5f }, { 0, 1, 1, 0.25f, 0.75f }, { 1, 1, 1, 0.5f, 0.75f }, { 1, 1, 0, 0.5f, 0.5f } };
        float[] map = SkinMapping.of(quad(corners), Direction.UP);
        assertNotNull(map);
        assertReproduces(map, corners, Direction.UP);
        assertEquals(0.375f, SkinMapping.u(map, 8, 8), EPS, "the middle of the face is the middle of the sprite");
        assertEquals(0.625f, SkinMapping.v(map, 8, 8), EPS);
    }

    @Test
    void turnedQuadKeepsItsTurn() {
        // a log lying down: the same side, its texture a quarter turn round
        float[][] corners = {
                { 0, 1, 1, 0.5f, 0.5f }, { 0, 0, 1, 0.25f, 0.5f }, { 1, 0, 1, 0.25f, 0.75f }, { 1, 1, 1, 0.5f, 0.75f } };
        float[] map = SkinMapping.of(quad(corners), Direction.SOUTH);
        assertNotNull(map);
        assertReproduces(map, corners, Direction.SOUTH);
        // going right along the wall now moves down the sprite, not across it
        assertEquals(0f, map[1], EPS);
        assertEquals(0f, map[5], EPS);
    }

    @Test
    void mirroredAndConnectedQuadsAreFollowed() {
        // a connected-texture tile: the quad points at one cell of a larger sheet, mirrored
        float[][] corners = {
                { 1, 1, 0, 0.9f, 0.1f }, { 1, 0, 0, 0.9f, 0.2f }, { 0, 0, 0, 0.8f, 0.2f }, { 0, 1, 0, 0.8f, 0.1f } };
        float[] map = SkinMapping.of(quad(corners), Direction.NORTH);
        assertNotNull(map);
        assertReproduces(map, corners, Direction.NORTH);
    }

    @Test
    void partOfASideIsNotStretchedOverIt() {
        // a detail covering a quarter of the side
        float[][] corners = {
                { 0, 1, 0, 0.25f, 0.5f }, { 0, 1, 0.5f, 0.25f, 0.6f }, { 0.5f, 1, 0.5f, 0.35f, 0.6f }, { 0.5f, 1, 0, 0.35f, 0.5f } };
        assertNull(SkinMapping.of(quad(corners), Direction.UP));
        // and a quad squashed to a line has no mapping at all
        float[][] line = {
                { 0, 1, 0, 0, 0 }, { 0, 1, 0, 0, 1 }, { 1, 1, 0, 1, 1 }, { 1, 1, 0, 1, 0 } };
        assertNull(SkinMapping.of(quad(line), Direction.UP));
    }
}
