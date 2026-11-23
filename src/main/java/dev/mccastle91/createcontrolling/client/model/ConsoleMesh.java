package dev.mccastle91.createcontrolling.client.model;

import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import dev.mccastle91.createcontrolling.client.ClientCompat;
import dev.mccastle91.createcontrolling.panel.ConsoleOrientation;
import dev.mccastle91.createcontrolling.panel.ConsoleShape;
import dev.mccastle91.createcontrolling.panel.PanelBlock;
import dev.mccastle91.createcontrolling.panel.PanelFaceGeometry;
import net.minecraft.client.renderer.FaceInfo;
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
 * A copycat console swaps the casing texture for a {@link Skin}: the textures
 * of the block it copies, each wedge face taking those of the world side it
 * points to. The plate stays the plate.
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

    /**
     * One texture layer of a copied block's face (grass has two on its sides), with its tint index.
     *
     * @param map how the block's model lays that texture on the side (see {@link SkinMapping}),
     *            or null to spread the whole sprite over it
     */
    public record Layer(TextureAtlasSprite sprite, int tintIndex, @Nullable float[] map) {

        public Layer(TextureAtlasSprite sprite, int tintIndex) {
            this(sprite, tintIndex, null);
        }
    }

    /** Lightmap value baked into a quad that glows whatever the light around it (magma, a lit lamp). */
    private static final int FULL_BRIGHT = 0xF000F0;

    /** What a copied block shows on each side of the world, bottom layer first. */
    public interface Skin {
        List<Layer> layers(Direction worldSide);
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
    /** The copied block's textures, drawn wherever the casing's would be; null for a console in its casing. */
    @Nullable
    private Skin skin;
    /** Whether the console's own textures (plate, casing) are drawn; false leaves only the skin's. */
    private boolean ownTextures = true;
    /** Lightmap baked into the skin's quads: {@link #FULL_BRIGHT} for a copied block that glows, else 0. */
    private int skinLight;
    private TextureAtlasSprite plateSprite;
    /** The world side whose skin the quads being emitted take, or null for the one each faces. */
    @Nullable
    private Direction skinSide;

    private ConsoleMesh(ConsoleOrientation orientation, ConsoleShape shape) {
        this.orientation = orientation;
        this.face = PanelFaceGeometry.model(shape);
    }

    public static Mesh build(ConsoleOrientation orientation, ConsoleShape shape, Connections c,
                             TextureAtlasSprite casing, @Nullable CTSpriteShiftEntry casingCt,
                             TextureAtlasSprite plate) {
        return build(orientation, shape, c, casing, casingCt, plate, null, true, false);
    }

    /**
     * @param skin        the copied block's textures in place of the casing's, or null
     * @param ownTextures false to leave out everything but the skin: the pass for a
     *                    render layer only the copied block draws in
     * @param glowingSkin true if the copied block draws at full brightness whatever the light
     */
    public static Mesh build(ConsoleOrientation orientation, ConsoleShape shape, Connections c,
                             TextureAtlasSprite casing, @Nullable CTSpriteShiftEntry casingCt,
                             TextureAtlasSprite plate, @Nullable Skin skin, boolean ownTextures, boolean glowingSkin) {
        ConsoleMesh mesh = new ConsoleMesh(orientation, shape);
        mesh.skinLight = glowingSkin ? FULL_BRIGHT : 0;
        mesh.ct = skin == null ? casingCt : null;
        mesh.skin = skin;
        mesh.ownTextures = ownTextures;
        mesh.plateSprite = plate;
        mesh.body(shape, c, casing);
        // the sloped face is the block's top, however steep
        mesh.skinSide = orientation.rotate(Direction.UP);
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
        // a copycat console's body may be see-through (glass, the bare frame's
        // gaps): give the face an underside, or it vanishes seen through a wall
        if (skin != null)
            quad(plate, face.normal().scale(-1),
                    face.point(0, top, 0), 0, 0,
                    face.point(0, bottom, 0), 0, 16,
                    face.point(16, bottom, 0), 16, 16,
                    face.point(16, top, 0), 16, 0);

        // edges: frame unless an identical neighbour continues the surface. A
        // horizontal frame strip owns its corners, so the vertical ones stop
        // short of it rather than overlapping (coplanar quads z-fight)
        if (c.n()) facePlate(plate, 1, top, 15, top + 1); else faceFrameH(casing, 0, top, 16, top + 1);
        if (c.s()) facePlate(plate, 1, bottom - 1, 15, bottom); else faceFrameH(casing, 0, bottom - 1, 16, bottom);
        float vTop = c.n() ? top : top + 1, vBottom = c.s() ? bottom : bottom - 1;
        if (c.w()) facePlate(plate, 0, top + 1, 1, bottom - 1); else faceFrameV(casing, 0, vTop, 1, vBottom);
        if (c.e()) facePlate(plate, 15, top + 1, 16, bottom - 1); else faceFrameV(casing, 15, vTop, 16, vBottom);

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
        boolean skinned = skin != null && sprite != plateSprite;
        if (!skinned && !ownTextures)
            return;
        Vec3[] pos = { orientation.apply(a), orientation.apply(b), orientation.apply(c), orientation.apply(d) };
        float[] u = { ua, ub, uc, ud };
        float[] v = { va, vb, vc, vd };
        Vec3 normal = orientation.rotate(modelNormal).normalize();

        // counter-clockwise seen from outside; the diagonals' cross product
        // is robust even when two corners coincide (a knife-edged side)
        Vec3 winding = pos[2].subtract(pos[0]).cross(pos[3].subtract(pos[1]));
        if (winding.dot(normal) < 0) {
            swap(pos, u, v, 1, 3);
        }
        // start at the corner vanilla starts that face at (see vanillaStart)
        int start = vanillaStart(pos, Direction.getNearest(normal.x, normal.y, normal.z));
        if (start != 0) {
            Vec3[] p0 = pos.clone();
            float[] u0 = u.clone(), v0 = v.clone();
            for (int i = 0; i < 4; i++) {
                pos[i] = p0[(i + start) % 4];
                u[i] = u0[(i + start) % 4];
                v[i] = v0[(i + start) % 4];
            }
        }
        if (skinned) {
            Direction side = skinSide != null ? skinSide : Direction.getNearest(normal.x, normal.y, normal.z);
            // the copied block's texture goes where it is on that block: each corner takes the
            // texture px its place in the world has on that side, not the casing's layout,
            // so the skin lines up with real blocks and across joined consoles
            float[] du = new float[4], dv = new float[4], px = new float[2];
            for (int i = 0; i < 4; i++) {
                SkinMapping.defaultUv(side, pos[i].x, pos[i].y, pos[i].z, px);
                du[i] = px[0];
                dv[i] = px[1];
            }
            for (Layer layer : skin.layers(side)) {
                float[] lu = new float[4], lv = new float[4];
                for (int i = 0; i < 4; i++) {
                    if (layer.map() != null) {
                        lu[i] = SkinMapping.u(layer.map(), du[i], dv[i]);
                        lv[i] = SkinMapping.v(layer.map(), du[i], dv[i]);
                    } else {
                        lu[i] = ClientCompat.spriteU(layer.sprite(), du[i]);
                        lv[i] = ClientCompat.spriteV(layer.sprite(), dv[i]);
                    }
                }
                target.add(pack(pos, lu, lv, normal, layer.sprite(), layer.tintIndex(), skinLight));
            }
            return;
        }
        // texture px to atlas uv; connected walls are redirected into the casing's CT sheet
        boolean connected = ct != null && ctIndex >= 0 && sprite == ct.getOriginal();
        u = atlasU(sprite, u);
        v = atlasV(sprite, v);
        if (connected) {
            for (int i = 0; i < 4; i++) {
                u[i] = ct.getTargetU(u[i], ctIndex);
                v[i] = ct.getTargetV(v[i], ctIndex);
            }
            sprite = ct.getTarget();
        }
        target.add(pack(pos, u, v, normal, sprite, -1, 0));
    }

    private static float[] atlasU(TextureAtlasSprite sprite, float[] px) {
        float[] u = new float[4];
        for (int i = 0; i < 4; i++)
            u[i] = ClientCompat.spriteU(sprite, px[i]);
        return u;
    }

    private static float[] atlasV(TextureAtlasSprite sprite, float[] px) {
        float[] v = new float[4];
        for (int i = 0; i < 4; i++)
            v[i] = ClientCompat.spriteV(sprite, px[i]);
        return v;
    }

    /**
     * Which of a quad's corners (given counter-clockwise from outside) should
     * come first so they run in the order vanilla bakes that face in.
     *
     * Smooth lighting doesn't look at where a quad's corners are: for a quad
     * that doesn't cover its whole block face it shades the first corner as
     * the face's first corner, the second as its second, and so on, working
     * only from the quad's bounds. A console's corners start wherever its
     * rotation leaves them, so on most facings each corner took the shading
     * meant for its neighbour — a quarter turn out. On the 1 px strips that
     * replace the frame between joined consoles, that put the shadow of a
     * block standing behind the console along the whole length of the strip:
     * dark lines at the seams. Starting at vanilla's corner fixes the shading
     * of every quad, strips and wide ones alike.
     */
    static int vanillaStart(Vec3[] pos, Direction side) {
        // bounds indexed like Direction's 3D data value: down, up, north, south, west, east
        double[] bounds = { Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE,
                Double.MAX_VALUE, -Double.MAX_VALUE };
        for (Vec3 p : pos) {
            bounds[0] = Math.min(bounds[0], p.y);
            bounds[1] = Math.max(bounds[1], p.y);
            bounds[2] = Math.min(bounds[2], p.z);
            bounds[3] = Math.max(bounds[3], p.z);
            bounds[4] = Math.min(bounds[4], p.x);
            bounds[5] = Math.max(bounds[5], p.x);
        }
        FaceInfo face = FaceInfo.fromFacing(side);
        int best = 0;
        double bestCost = Double.MAX_VALUE;
        for (int start = 0; start < 4; start++) {
            double cost = 0;
            for (int i = 0; i < 4; i++) {
                FaceInfo.VertexInfo corner = face.getVertexInfo(i);
                Vec3 p = pos[(i + start) % 4];
                double dx = p.x - bounds[corner.xFace], dy = p.y - bounds[corner.yFace], dz = p.z - bounds[corner.zFace];
                cost += dx * dx + dy * dy + dz * dz;
            }
            if (cost < bestCost - 1e-9) {
                bestCost = cost;
                best = start;
            }
        }
        return best;
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
    private static BakedQuad pack(Vec3[] pos, float[] u, float[] v, Vec3 n, TextureAtlasSprite sprite, int tintIndex,
                                  int light) {
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
            data[base + 6] = light;
            data[base + 7] = packedNormal;
        }
        return new BakedQuad(data, tintIndex, Direction.getNearest(n.x, n.y, n.z), sprite, true);
    }
}
