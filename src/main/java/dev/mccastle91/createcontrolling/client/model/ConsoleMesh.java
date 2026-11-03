package dev.mccastle91.createcontrolling.client.model;

import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import dev.mccastle91.createcontrolling.client.ClientCompat;
import dev.mccastle91.createcontrolling.panel.ConsoleOrientation;
import dev.mccastle91.createcontrolling.panel.ConsoleShape;
import dev.mccastle91.createcontrolling.panel.PanelBlock;
import dev.mccastle91.createcontrolling.panel.PanelFaceGeometry;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a console body as baked quads: the sloped wedge in model space
 * (see {@link ConsoleOrientation}), rotated into its placement. The sides,
 * back and bottom wear the casing texture; the face is a control plate inside
 * a 1 px frame sampled from the casing's own border. Frame edges that meet an
 * identical neighbour are replaced by plate, so a group of consoles reads as
 * one framed surface (the inner corner of an L keeps its frame pixel). Where
 * the casing has Create connected textures, the outer walls of joined
 * consoles use them too, so a row's back reads as one casing.
 *
 * Quads are packed directly in the vanilla BLOCK vertex layout, which is the
 * same on 1.20.1 and 1.21.1, and lit by the normal chunk pipeline.
 */
public final class ConsoleMesh {

    /** Band of the casing texture used as frame colour: its second row/column. */
    private static final float FRAME_BAND_MIN = 1f;
    private static final float FRAME_BAND_MAX = 2f;

    public record Connections(boolean n, boolean s, boolean w, boolean e,
                              boolean ne, boolean nw, boolean se, boolean sw) {

        public static final Connections NONE = new Connections(false, false, false, false, false, false, false, false);

        public static Connections of(@Nullable BlockState state) {
            if (state == null || !(state.getBlock() instanceof PanelBlock))
                return NONE;
            return new Connections(state.getValue(PanelBlock.CONNECT_N), state.getValue(PanelBlock.CONNECT_S),
                    state.getValue(PanelBlock.CONNECT_W), state.getValue(PanelBlock.CONNECT_E),
                    state.getValue(PanelBlock.DIAG_NE), state.getValue(PanelBlock.DIAG_NW),
                    state.getValue(PanelBlock.DIAG_SE), state.getValue(PanelBlock.DIAG_SW));
        }
    }

    /**
     * The quads of one console. The bottom covers its whole block face, so it
     * is culled against the supporting block like any full face.
     */
    public record Mesh(List<BakedQuad> quads, Direction bottomSide, List<BakedQuad> bottom) {
    }

    private final ConsoleOrientation orientation;
    private final PanelFaceGeometry face;
    private final List<BakedQuad> quads = new ArrayList<>();
    private final List<BakedQuad> bottom = new ArrayList<>();
    private List<BakedQuad> target = quads;
    /** Connected-texture sheet index for the quad being emitted, or -1 for the plain sprite. */
    private int ctIndex = -1;
    @Nullable
    private CTSpriteShiftEntry ct;
    /** The face's extent down the slope, design px; texture v along it is spread over 0–16. */
    private float plateTop = 0, plateBottom = 16;

    private ConsoleMesh(ConsoleOrientation orientation, ConsoleShape shape) {
        this.orientation = orientation;
        this.face = PanelFaceGeometry.model(shape);
    }

    public static Mesh build(ConsoleOrientation orientation, ConsoleShape shape, Connections c,
                             TextureAtlasSprite casing, @Nullable CTSpriteShiftEntry casingCt,
                             TextureAtlasSprite plate) {
        ConsoleMesh mesh = new ConsoleMesh(orientation, shape);
        mesh.ct = casingCt;
        mesh.body(shape, c, casing);
        mesh.face(shape, c, casing, plate);
        return new Mesh(List.copyOf(mesh.quads), orientation.rotate(Direction.DOWN), List.copyOf(mesh.bottom));
    }

    // --- Body ----------------------------------------------------------------

    private void body(ConsoleShape shape, Connections c, TextureAtlasSprite casing) {
        // model-space edge heights: far edge at z = 0, near edge at z = 1
        double b = shape.farHeight() / 16.0;
        double f = shape.nearHeight() / 16.0;
        target = bottom;
        quad(casing, new Vec3(0, -1, 0),
                p(0, 0, 0), 0, 0, p(1, 0, 0), 16, 0, p(1, 0, 1), 16, 16, p(0, 0, 1), 0, 16);
        target = quads;
        // walls: u = 0 is each quad's texture-left, so that side's neighbour is "left"
        // back (far edge); either end is absent where the console has a knife edge
        connect(c.e(), c.w());
        if (!c.n() && b > 1e-4)
            quad(casing, new Vec3(0, 0, -1),
                    p(1, 0, 0), 0, 16, p(0, 0, 0), 16, 16, p(0, b, 0), 16, side(b), p(1, b, 0), 0, side(b));
        // front (near edge)
        connect(c.w(), c.e());
        if (!c.s() && f > 1e-4)
            quad(casing, new Vec3(0, 0, 1),
                    p(0, 0, 1), 0, 16, p(1, 0, 1), 16, 16, p(1, f, 1), 16, side(f), p(0, f, 1), 0, side(f));
        // sides: trapezoids following the slope (they only join on flat consoles)
        connect(c.n(), c.s());
        if (!c.w())
            quad(casing, new Vec3(-1, 0, 0),
                    p(0, 0, 0), 0, 16, p(0, 0, 1), 16, 16, p(0, f, 1), 16, side(f), p(0, b, 0), 0, side(b));
        connect(c.s(), c.n());
        if (!c.e())
            quad(casing, new Vec3(1, 0, 0),
                    p(1, 0, 1), 0, 16, p(1, 0, 0), 16, 16, p(1, b, 0), 16, side(b), p(1, f, 1), 0, side(f));
        ctIndex = -1;
    }

    /** Picks the connected-texture tile for the next wall: joined left/right, never up/down (one block tall). */
    private void connect(boolean left, boolean right) {
        if (ct == null) {
            ctIndex = -1;
            return;
        }
        ConnectedTextureBehaviour.CTContext context = new ConnectedTextureBehaviour.CTContext();
        context.left = left;
        context.right = right;
        ctIndex = ct.getType().getTextureIndex(context);
    }

    /** Texture v for a height on a side face: the texture's bottom sits at the console's base. */
    private static float side(double height) {
        return (float) (16 - height * 16);
    }

    // --- Face ----------------------------------------------------------------

    private void face(ConsoleShape shape, Connections c, TextureAtlasSprite casing, TextureAtlasSprite plate) {
        // the whole face in design px: a steep face runs past the 16 px design
        // area by the same margin at both ends, and the frame stays 1 px
        float top = -shape.designMargin();
        float bottom = 16 + shape.designMargin();
        plateTop = top;
        plateBottom = bottom;

        // centre
        facePlate(plate, 1, top + 1, 15, bottom - 1);

        // edges: frame unless an identical neighbour continues the surface
        if (c.n()) facePlate(plate, 1, top, 15, top + 1); else faceFrameH(casing, 0, top, 16, top + 1);
        if (c.s()) facePlate(plate, 1, bottom - 1, 15, bottom); else faceFrameH(casing, 0, bottom - 1, 16, bottom);
        if (c.w()) facePlate(plate, 0, top + 1, 1, bottom - 1); else faceFrameV(casing, 0, top, 1, bottom);
        if (c.e()) facePlate(plate, 15, top + 1, 16, bottom - 1); else faceFrameV(casing, 15, top, 16, bottom);

        // corners where both edges connect: plate if the diagonal is there too,
        // else one frame pixel — the inner corner of an L
        corner(c.n() && c.w(), c.nw(), casing, plate, 0, top);
        corner(c.n() && c.e(), c.ne(), casing, plate, 15, top);
        corner(c.s() && c.w(), c.sw(), casing, plate, 0, bottom - 1);
        corner(c.s() && c.e(), c.se(), casing, plate, 15, bottom - 1);
    }

    private void corner(boolean bothConnected, boolean diagonal, TextureAtlasSprite casing,
                        TextureAtlasSprite plate, float x, float y) {
        if (!bothConnected)
            return; // covered by the full-length frame strips
        if (diagonal)
            facePlate(plate, x, y, x + 1, y + 1);
        else
            faceRect(casing, x, y, x + 1, y + 1, FRAME_BAND_MIN, FRAME_BAND_MIN, FRAME_BAND_MAX, FRAME_BAND_MAX);
    }

    /** Plate texture spread over the whole face, however long the slope. */
    private void facePlate(TextureAtlasSprite plate, float x0, float y0, float x1, float y1) {
        float k = 16f / (plateBottom - plateTop);
        faceRect(plate, x0, y0, x1, y1, x0, (y0 - plateTop) * k, x1, (y1 - plateTop) * k);
    }

    /** Horizontal frame strip: casing texture along its length, one band thick. */
    private void faceFrameH(TextureAtlasSprite casing, float x0, float y0, float x1, float y1) {
        faceRect(casing, x0, y0, x1, y1, x0, FRAME_BAND_MIN, x1, FRAME_BAND_MAX);
    }

    /** Vertical frame strip, its texture spread along the whole face. */
    private void faceFrameV(TextureAtlasSprite casing, float x0, float y0, float x1, float y1) {
        float k = 16f / (plateBottom - plateTop);
        faceRect(casing, x0, y0, x1, y1, FRAME_BAND_MIN, (y0 - plateTop) * k, FRAME_BAND_MAX, (y1 - plateTop) * k);
    }

    /** A rectangle of the face (design px) with the given texture region. */
    private void faceRect(TextureAtlasSprite sprite, float x0, float y0, float x1, float y1,
                          float u0, float v0, float u1, float v1) {
        quad(sprite, face.normal(),
                face.point(x0, y0, 0), u0, v0,
                face.point(x0, y1, 0), u0, v1,
                face.point(x1, y1, 0), u1, v1,
                face.point(x1, y0, 0), u1, v0);
    }

    // --- Packing -------------------------------------------------------------

    private static Vec3 p(double x, double y, double z) {
        return new Vec3(x, y, z);
    }

    /** Emits one quad given its model-space corners in order around the edge, with texture px per corner. */
    private void quad(TextureAtlasSprite sprite, Vec3 modelNormal,
                      Vec3 a, float ua, float va, Vec3 b, float ub, float vb,
                      Vec3 c, float uc, float vc, Vec3 d, float ud, float vd) {
        Vec3[] pos = { orientation.apply(a), orientation.apply(b), orientation.apply(c), orientation.apply(d) };
        float[] u = { ua, ub, uc, ud };
        float[] v = { va, vb, vc, vd };
        // texture px to atlas uv; connected walls are redirected into the casing's CT sheet
        boolean connected = ct != null && ctIndex >= 0 && sprite == ct.getOriginal();
        for (int i = 0; i < 4; i++) {
            u[i] = ClientCompat.spriteU(sprite, u[i]);
            v[i] = ClientCompat.spriteV(sprite, v[i]);
            if (connected) {
                u[i] = ct.getTargetU(u[i], ctIndex);
                v[i] = ct.getTargetV(v[i], ctIndex);
            }
        }
        if (connected)
            sprite = ct.getTarget();
        Vec3 normal = orientation.rotate(modelNormal).normalize();

        // counter-clockwise seen from outside; the diagonals' cross product
        // is robust even when two corners coincide (a knife-edged side)
        Vec3 winding = pos[2].subtract(pos[0]).cross(pos[3].subtract(pos[1]));
        if (winding.dot(normal) < 0) {
            swap(pos, u, v, 1, 3);
        }
        target.add(pack(pos, u, v, normal, sprite));
    }

    private static void swap(Vec3[] pos, float[] u, float[] v, int i, int j) {
        Vec3 tp = pos[i];
        pos[i] = pos[j];
        pos[j] = tp;
        float tu = u[i];
        u[i] = u[j];
        u[j] = tu;
        float tv = v[i];
        v[i] = v[j];
        v[j] = tv;
    }

    /** BLOCK layout per vertex: position xyz, colour, atlas uv, lightmap, normal — 8 ints. */
    private static BakedQuad pack(Vec3[] pos, float[] u, float[] v, Vec3 n, TextureAtlasSprite sprite) {
        int[] data = new int[32];
        int packedNormal = ((int) Math.round(n.x * 127) & 0xFF)
                | (((int) Math.round(n.y * 127) & 0xFF) << 8)
                | (((int) Math.round(n.z * 127) & 0xFF) << 16);
        for (int i = 0; i < 4; i++) {
            int base = i * 8;
            data[base] = Float.floatToRawIntBits((float) pos[i].x);
            data[base + 1] = Float.floatToRawIntBits((float) pos[i].y);
            data[base + 2] = Float.floatToRawIntBits((float) pos[i].z);
            data[base + 3] = -1;
            data[base + 4] = Float.floatToRawIntBits(u[i]);
            data[base + 5] = Float.floatToRawIntBits(v[i]);
            data[base + 6] = 0;
            data[base + 7] = packedNormal;
        }
        return new BakedQuad(data, -1, Direction.getNearest(n.x, n.y, n.z), sprite, true);
    }
}
