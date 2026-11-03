package dev.mccastle91.createcontrolling.client;

import dev.mccastle91.createcontrolling.panel.PanelFaceGeometry;
import dev.mccastle91.createcontrolling.util.McCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Draws component geometry on a console face. Coordinates are face pixels:
 * x right, y down the slope towards the player, z out of the face. A small
 * transform stack places rotated parts (knob bodies, lever sticks, hinged
 * covers). Boxes are block-atlas textured, shaded like block faces by their
 * real normal, so components sit in the world like part of the block.
 */
public final class ComponentPainter {

    public static final int FULL_BRIGHT = 0xF000F0;

    public static final ResourceLocation COMPONENT = McCompat.id("create_controlling", "block/component");
    public static final ResourceLocation BRASS = McCompat.id("create", "block/brass_block");
    public static final ResourceLocation IRON = McCompat.id("create", "block/industrial_iron_block");

    private static final float PX = 1 / 16f;

    private final PanelFaceGeometry face;
    private final Matrix4f pose;
    private final MultiBufferSource buffers;
    private final Deque<Matrix4f> stack = new ArrayDeque<>();
    private Matrix4f local = new Matrix4f();
    private RenderType renderType = RenderType.solid();

    public ComponentPainter(PanelFaceGeometry face, Matrix4f pose, MultiBufferSource buffers) {
        this.face = face;
        this.pose = pose;
        this.buffers = buffers;
    }

    public static TextureAtlasSprite sprite(ResourceLocation texture) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
    }

    // --- Transform stack -------------------------------------------------------

    public void push() {
        stack.push(new Matrix4f(local));
    }

    public void pop() {
        local = stack.pop();
    }

    public void translate(float x, float y, float z) {
        local.translate(x, y, z);
    }

    /** Clockwise as seen from above the face, 0° pointing at the far edge. */
    public void rotateZ(float degrees) {
        local.rotateZ((float) Math.toRadians(degrees));
    }

    /** Positive tips +z towards the far edge (-y). */
    public void rotateX(float degrees) {
        local.rotateX((float) Math.toRadians(degrees));
    }

    /**
     * Draws following boxes as see-through glass (alpha from the colour), or
     * back to solid. Glass writes no depth, so text inside it still shows.
     */
    public void translucent(boolean on) {
        renderType = on ? CCRenderTypes.GLASS : RenderType.solid();
    }

    // --- Geometry ------------------------------------------------------------

    public void box(float x0, float y0, float z0, float x1, float y1, float z1,
                    TextureAtlasSprite sprite, int argb, int light) {
        float w = x1 - x0, d = y1 - y0, h = z1 - z0;
        Vector3f ex = new Vector3f(w, 0, 0), ey = new Vector3f(0, d, 0), ez = new Vector3f(0, 0, h);
        // top, bottom
        face(new Vector3f(x0, y0, z1), ex, ey, new Vector3f(0, 0, 1), sprite, argb, light);
        face(new Vector3f(x0, y0, z0), ex, ey, new Vector3f(0, 0, -1), sprite, argb, light);
        // far (-y), near (+y)
        face(new Vector3f(x0, y0, z0), ex, ez, new Vector3f(0, -1, 0), sprite, argb, light);
        face(new Vector3f(x0, y1, z0), ex, ez, new Vector3f(0, 1, 0), sprite, argb, light);
        // left (-x), right (+x)
        face(new Vector3f(x0, y0, z0), ey, ez, new Vector3f(-1, 0, 0), sprite, argb, light);
        face(new Vector3f(x1, y0, z0), ey, ez, new Vector3f(1, 0, 0), sprite, argb, light);
    }

    /** A flat rectangle at height {@code z}, facing out of the face. */
    public void plate(float x0, float y0, float x1, float y1, float z, TextureAtlasSprite sprite, int argb, int light) {
        face(new Vector3f(x0, y0, z), new Vector3f(x1 - x0, 0, 0), new Vector3f(0, y1 - y0, 0),
                new Vector3f(0, 0, 1), sprite, argb, light);
    }

    private void face(Vector3f corner, Vector3f e1, Vector3f e2, Vector3f normal,
                      TextureAtlasSprite sprite, int argb, int light) {
        Vec3[] p = {
                toBlock(new Vector3f(corner)),
                toBlock(new Vector3f(corner).add(e1)),
                toBlock(new Vector3f(corner).add(e1).add(e2)),
                toBlock(new Vector3f(corner).add(e2)) };
        Vector3f fn = local.transformDirection(new Vector3f(normal)).normalize();
        Vec3 n = face.u().scale(fn.x).add(face.v().scale(fn.y)).add(face.normal().scale(fn.z)).normalize();

        float uSpan = Math.min(e1.length(), 16f), vSpan = Math.min(e2.length(), 16f);
        float[] us = { 0, uSpan, uSpan, 0 };
        float[] vs = { 0, 0, vSpan, vSpan };
        int[] order = { 0, 1, 2, 3 };
        Vec3 winding = p[1].subtract(p[0]).cross(p[3].subtract(p[0]));
        if (winding.dot(n) < 0)
            order = new int[] { 0, 3, 2, 1 };

        int shaded = shade(argb, n);
        var consumer = buffers.getBuffer(renderType);
        for (int i : order)
            ClientCompat.blockVertex(consumer, pose, (float) p[i].x, (float) p[i].y, (float) p[i].z, shaded,
                    ClientCompat.spriteU(sprite, us[i]), ClientCompat.spriteV(sprite, vs[i]), light,
                    (float) n.x, (float) n.y, (float) n.z);
    }

    private Vec3 toBlock(Vector3f facePx) {
        local.transformPosition(facePx);
        return face.point(facePx.x, facePx.y, facePx.z);
    }

    /** Block-face style directional shading: top full, sides 0.8/0.6, bottom 0.5. */
    private static int shade(int argb, Vec3 n) {
        double k = Math.min(1.0, n.x * n.x * 0.6 + n.y * n.y * ((3 + n.y) / 4) + n.z * n.z * 0.8);
        int r = (int) (((argb >> 16) & 0xFF) * k);
        int g = (int) (((argb >> 8) & 0xFF) * k);
        int b = (int) ((argb & 0xFF) * k);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    // --- Text ----------------------------------------------------------------

    /**
     * Text lying on the face, its top-left at face px ({@code x}, {@code y}),
     * glyphs {@code lineHeight} px per font line.
     */
    public void faceText(Font font, String text, float x, float y, float lift, float lineHeight, int argb, int light) {
        Vec3 origin = face.point(x, y, lift);
        Vec3 into = face.u().cross(face.v());
        text(font, text, origin, face.u(), face.v(), into, lineHeight, argb, light);
    }

    /**
     * Text standing up from the face (a nixie digit), its glyphs' visible
     * height centred on {@code mid} px off the face, at depth {@code y}; it
     * shows from both sides, mirrored from behind like a real filament.
     *
     * Standing on a floor or wall it faces down the design (+y, towards the
     * player) and reads upright with its top away from the face. Hanging from
     * a ceiling the player sits up the design (-y) and "up" is towards the
     * face, so it is turned half a turn about x: facing -y, top at the face.
     */
    public void standingText(Font font, String text, float x, float y, float mid, float lineHeight,
                             int argb, int light, boolean hanging) {
        float half = lineHeight * GLYPH_HEIGHT / 2;
        Vec3 out = face.normal();
        Vec3 origin = face.point(x, y, hanging ? mid - half : mid + half);
        Vec3 down = hanging ? out : out.scale(-1);
        Vec3 into = hanging ? face.v() : face.v().scale(-1);
        text(font, text, origin, face.u(), down, into, lineHeight, argb, light, new DoubleSidedBuffers(buffers));
    }

    /**
     * Text floating parallel to the face, {@code lift} px off it and facing
     * out (a nixie digit read end-on, as on a wall console): its glyphs'
     * visible height centred on design depth {@code midY}, shown from both
     * sides like {@link #standingText}.
     */
    public void floatingText(Font font, String text, float x, float midY, float lift, float lineHeight,
                             int argb, int light) {
        Vec3 origin = face.point(x, midY - lineHeight * GLYPH_HEIGHT / 2, lift);
        text(font, text, origin, face.u(), face.v(), face.u().cross(face.v()), lineHeight, argb, light,
                new DoubleSidedBuffers(buffers));
    }

    /** Visible glyph height as a fraction of a font line (a line includes spacing below). */
    private static final float GLYPH_HEIGHT = 0.8f;

    private void text(Font font, String text, Vec3 origin, Vec3 xAxis, Vec3 yAxis, Vec3 zAxis,
                      float lineHeight, int argb, int light) {
        text(font, text, origin, xAxis, yAxis, zAxis, lineHeight, argb, light, buffers);
    }

    private void text(Font font, String text, Vec3 origin, Vec3 xAxis, Vec3 yAxis, Vec3 zAxis,
                      float lineHeight, int argb, int light, MultiBufferSource target) {
        float k = lineHeight / font.lineHeight * PX;
        Matrix4f m = new Matrix4f(pose)
                .translate((float) origin.x, (float) origin.y, (float) origin.z)
                .mul(new Matrix4f(
                        (float) xAxis.x, (float) xAxis.y, (float) xAxis.z, 0,
                        (float) yAxis.x, (float) yAxis.y, (float) yAxis.z, 0,
                        (float) zAxis.x, (float) zAxis.y, (float) zAxis.z, 0,
                        0, 0, 0, 1))
                .scale(k, k, k);
        font.drawInBatch(text, 0, 0, argb, false, m, target, Font.DisplayMode.NORMAL, 0, light);
    }
}
