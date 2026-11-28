package dev.mccastle91.createcontrolling.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mccastle91.createcontrolling.api.client.ComponentRenderer;
import dev.mccastle91.createcontrolling.api.client.ConsoleClientApi;
import dev.mccastle91.createcontrolling.api.client.PartRenderContext;
import dev.mccastle91.createcontrolling.panel.PanelBlock;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.PanelFaceGeometry;
import dev.mccastle91.createcontrolling.panel.element.AnalogElement;
import dev.mccastle91.createcontrolling.panel.element.AnalogLeverElement;
import dev.mccastle91.createcontrolling.panel.element.BarMeterElement;
import dev.mccastle91.createcontrolling.panel.element.ButtonElement;
import dev.mccastle91.createcontrolling.panel.element.ColorableElement;
import dev.mccastle91.createcontrolling.panel.element.CoveredSwitchElement;
import dev.mccastle91.createcontrolling.panel.element.DisplayBoardElement;
import dev.mccastle91.createcontrolling.panel.element.KnobElement;
import dev.mccastle91.createcontrolling.panel.element.LabelElement;
import dev.mccastle91.createcontrolling.panel.element.LampElement;
import dev.mccastle91.createcontrolling.panel.element.LatchingButtonElement;
import dev.mccastle91.createcontrolling.panel.element.NeedleMeterElement;
import dev.mccastle91.createcontrolling.panel.element.ActorSwitchElement;
import dev.mccastle91.createcontrolling.panel.element.FloorButtonElement;
import dev.mccastle91.createcontrolling.panel.element.FloorReadoutElement;
import dev.mccastle91.createcontrolling.panel.element.NixieElement;
import dev.mccastle91.createcontrolling.panel.element.NixieStripElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.panel.element.ReadoutElement;
import dev.mccastle91.createcontrolling.panel.element.SliderElement;
import dev.mccastle91.createcontrolling.panel.element.SwitchElement;
import dev.mccastle91.createcontrolling.util.McCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

/**
 * Draws a console's components as small 3D parts — metal bezels (in the casing's own metal), industrial
 * iron bodies, dye-coloured caps — standing on the sloped face. Everything is
 * placed through the same {@link PanelFaceGeometry} the click ray uses, so
 * what you see is exactly what you hit.
 */
public class PanelBlockEntityRenderer<T extends PanelBlockEntity> implements BlockEntityRenderer<T> {


    /** Board text font line height, px, and how far long text may shrink to fit before it's cut off. */
    private static final float BOARD_LINE = 2.6f;
    private static final float BOARD_LINE_MIN = 1.6f;
    private static final float BOARD_MARGIN = 0.5f;

    /** Lever rod length (px) shared by the toggle and guarded switches; the tip adds {@link #LEVER_TIP}. */
    private static final float LEVER_LENGTH = 1.1f;
    private static final float LEVER_TIP = 0.8f;
    /**
     * Height of a decal over the surface it lies on, px: enough for the depth
     * buffer to keep them apart at range, too little to see.
     */
    private static final float LIFT = 0.05f;

    /** {@code brass} is the parts' metal trim: brass by default, but it follows the console's casing. */
    private TextureAtlasSprite component, brass, iron, boardFront, boardSide, boardEdge;
    private int light;
    /** Ceiling consoles: parts hang, so standing text turns to read from below. */
    private boolean hanging;
    /** Wall consoles: the player faces the face, so nixie digits face out of it. */
    private boolean onWall;

    @Override
    public void render(T panel, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockState state = panel.getBlockState();
        if (!(state.getBlock() instanceof PanelBlock) || panel.getLayout().isEmpty())
            return;

        component = ComponentPainter.sprite(ComponentPainter.COMPONENT);
        brass = ComponentPainter.sprite(panel.casing().trimTexture());
        iron = ComponentPainter.sprite(ComponentPainter.IRON);
        boardFront = ComponentPainter.sprite(ComponentPainter.BOARD_FRONT);
        boardSide = ComponentPainter.sprite(ComponentPainter.BOARD_SIDE);
        boardEdge = ComponentPainter.sprite(ComponentPainter.BOARD_EDGE);
        light = packedLight;
        AttachFace attach = state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE);
        hanging = attach == AttachFace.CEILING;
        onWall = attach == AttachFace.WALL;

        PanelFaceGeometry face = PanelFaceGeometry.of(state, panel.shape());
        ComponentPainter p = new ComponentPainter(face, poseStack.last().pose(), buffers);
        Font font = Minecraft.getInstance().font;

        PartRenderContext context = null;
        for (PanelElement element : panel.getLayout().elements()) {
            p.push();
            p.translate(element.x(), element.y(), 0);
            float w = element.width(), d = element.depth(), h = element.height();
            // a renderer registered through the API draws the part, its own or one of ours
            ComponentRenderer<PanelElement> custom = ConsoleClientApi.rendererFor(element.type());
            if (custom != null) {
                if (context == null)
                    context = new PartRenderContext(p, panel, font, light, brass, iron, component, hanging, onWall, partialTick);
                try {
                    custom.render(element, context);
                } catch (RuntimeException e) {
                    complain(element, e);
                }
                // whatever the renderer left pushed, the next part starts clean
                p.reset();
                continue;
            }
            if (element instanceof LabelElement label)
                label(p, font, label);
            else if (element instanceof CoveredSwitchElement covered)
                coveredSwitch(p, w, d, covered.lidTop(), covered.isGuardOpen(), covered.isOn(), color(covered));
            else if (element instanceof SwitchElement toggle)
                toggle(p, w, d, toggle.isOn(), color(toggle));
            else if (element instanceof LatchingButtonElement latching)
                button(p, w, d, h, latching.isOn(), color(latching));
            else if (element instanceof ButtonElement button)
                button(p, w, d, h, button.isPressed(), color(button));
            else if (element instanceof KnobElement knob)
                knob(p, w, d, h, knob, color(knob));
            else if (element instanceof SliderElement slider)
                slider(p, w, d, h, slider, color(slider));
            else if (element instanceof AnalogLeverElement lever)
                analogLever(p, w, d, lever.value(), color(lever));
            else if (element instanceof LampElement lamp)
                lamp(p, w, d, h, lamp.brightness(), color(lamp));
            else if (element instanceof NeedleMeterElement meter)
                needleMeter(p, w, d, meter.displayStrength());
            else if (element instanceof BarMeterElement meter)
                barMeter(p, w, d, meter.isVertical(), meter.displayStrength(), color(meter));
            else if (element instanceof NixieElement nixie)
                nixie(p, font, element, nixie.digits());
            else if (element instanceof NixieStripElement strip)
                nixieStrip(p, font, panel, strip);
            else if (element instanceof DisplayBoardElement board)
                displayBoard(p, font, panel, board);
            else if (element instanceof ActorSwitchElement actors)
                actorSwitch(p, font, actors, w, d, h);
            else if (element instanceof FloorButtonElement floor)
                floorButton(p, font, floor, w, d, h, color(floor));
            else if (element instanceof FloorReadoutElement readout)
                tubes(p, font, readout, readout.glyphs());
            else if (element.type().id().indexOf(':') >= 0)
                // another mod's part with no renderer registered: a plain plate, so it can be seen and clicked
                p.box(0, 0, 0, w, d, Math.max(0.25f, h), iron, 0xFFFFFFFF, light);
            p.pop();
        }
    }

    private static final java.util.Set<String> COMPLAINED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** A renderer that throws is reported once per component type, not once per frame. */
    private static void complain(PanelElement element, RuntimeException e) {
        if (COMPLAINED.add(element.type().id()))
            dev.mccastle91.createcontrolling.CreateControlling.LOGGER.error(
                    "The renderer for console component '{}' failed; it is skipped", element.type().id(), e);
    }

    private static int color(ColorableElement element) {
        return 0xFF000000 | McCompat.dyeRgb(element.color());
    }

    private static int scale(int argb, float factor) {
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((argb >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((argb & 0xFF) * factor));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    // --- Components ------------------------------------------------------------

    /** Small and large buttons share one profile: a 0.5 px bezel and a cap standing as high. */
    private void button(ComponentPainter p, float w, float d, float h, boolean down, int color) {
        float bezel = 0.5f;
        p.box(0, 0, 0, w, d, bezel, brass, 0xFFFFFFFF, light);
        float top = down ? h - bezel * 0.8f : h;
        p.box(bezel, bezel, bezel, w - bezel, d - bezel, top, component, down ? color : scale(color, 0.85f), light);
    }

    private void toggle(ComponentPainter p, float w, float d, boolean on, int color) {
        toggleBase(p, w, d);
        lever(p, w / 2, d / 2, 0.5f, on, LEVER_LENGTH, color);
    }

    /** The toggle switch's iron plate and brass pivot block; the lever pivots 0.5 px off the face. */
    private void toggleBase(ComponentPainter p, float w, float d) {
        p.box(0, 0, 0, w, d, 0.25f, iron, 0xFFFFFFFF, light);
        p.box(w / 2 - 0.65f, d / 2 - 0.65f, 0.25f, w / 2 + 0.65f, d / 2 + 0.65f, 0.6f, brass, 0xFFFFFFFF, light);
    }

    /** A straight lever pivoting about x, its tip coloured; on throws it towards the far edge. */
    private void lever(ComponentPainter p, float x, float y, float z, boolean on, float length, int color) {
        float r = 0.4f;
        p.push();
        p.translate(x, y, z);
        p.rotateX(on ? 30 : -30);
        p.box(-r, -r, 0, r, r, length, iron, 0xFFFFFFFF, light);
        p.box(-r, -r, length, r, r, length + LEVER_TIP, component, color, light);
        p.pop();
    }

    /**
     * Literally a toggle switch with a cover on it: the same footprint, base
     * and lever, under a hollow lid hinged along its top far edge. Opened, the
     * lid flips up to stand upright over the far half of the base, so it never
     * reaches past the switch's own footprint. Two brass hinge posts with a pin
     * between them carry the hinge: hidden inside the closed lid, holding up
     * the open one.
     */
    private void coveredSwitch(ComponentPainter p, float w, float d, float top, boolean open, boolean on, int color) {
        toggleBase(p, w, d);
        lever(p, w / 2, d / 2, 0.5f, on, LEVER_LENGTH, StyleColor.GUARDED_LEVER.argb());
        float t = CoveredSwitchElement.LID_THICKNESS, post = 0.25f, base = 0.25f;
        // posts just inside the lid's side walls, clear of the lever and its
        // pivot block; they stop under the lid's top so no faces coincide
        p.box(t, 0.05f, base, t + post, 0.45f, top - t, brass, 0xFFFFFFFF, light);
        p.box(w - t - post, 0.05f, base, w - t, 0.45f, top - t, brass, 0xFFFFFFFF, light);
        p.box(t + post, 0.05f, top - t - 0.25f, w - t - post, 0.25f, top - t, brass, 0xFFFFFFFF, light);
        p.push();
        p.translate(0, 0, top);
        if (open)
            p.rotateX(90);
        // the walls stand on the base plate, not beside its edges
        p.box(0, 0, -t, w, d, 0, component, color, light);
        p.box(0, 0, base - top, t, d, -t, component, color, light);
        p.box(w - t, 0, base - top, w, d, -t, component, color, light);
        p.box(t, d - t, base - top, w - t, d, -t, component, color, light);
        p.pop();
    }

    /** A limited knob gets a red tick where it stops, at either end it stops short of. */
    private void knob(ComponentPainter p, float w, float d, float h, KnobElement knob, int color) {
        int value = knob.value();
        p.box(0, 0, 0, w, d, 0.25f, brass, 0xFFFFFFFF, light);
        float r = w / 2;
        for (int stop : stops(knob)) {
            p.push();
            p.translate(r, d / 2, 0);
            p.rotateZ(KnobElement.angleFor(stop));
            p.box(-0.15f, -r + 0.05f, 0.25f, 0.15f, -r + 0.45f, 0.3f + LIFT, component, StyleColor.NEEDLE.argb(), light);
            p.pop();
        }
        for (int tick : new int[] { 0, 15 / 2, 15 }) {
            p.push();
            p.translate(r, d / 2, 0);
            p.rotateZ(KnobElement.angleFor(tick));
            p.box(-0.15f, -r + 0.05f, 0.25f, 0.15f, -r + 0.45f, 0.3f, component, StyleColor.MARKING.argb(), light);
            p.pop();
        }
        p.push();
        p.translate(r, d / 2, 0);
        p.rotateZ(KnobElement.angleFor(value));
        float body = r - 0.5f, cap = body - 0.4f;
        p.box(-body, -body, 0.25f, body, body, h - 0.4f, component, color, light);
        p.box(-cap, -cap, h - 0.4f, cap, cap, h, component, color, light);
        p.box(-0.25f, -body - LIFT, h - 0.6f, 0.25f, -0.3f, h + LIFT, component, StyleColor.POINTER.argb(), light);
        p.pop();
    }

    /** A limited slider gets a red line across it where the handle stops, at either end it stops short of. */
    private void slider(ComponentPainter p, float w, float d, float h, SliderElement slider, int color) {
        boolean vertical = slider.isVertical();
        p.box(0, 0, 0, w, d, 0.25f, iron, 0xFFFFFFFF, light);
        float f = SliderElement.fractionFor(slider.value());
        float m = SliderElement.END_MARGIN;
        for (float at : slider.stopLines()) {
            float half = SliderElement.STOP_LINE / 2;
            if (vertical)
                p.plate(0, at - half, w, at + half, 0.25f + 2 * LIFT, component, StyleColor.NEEDLE.argb(), light);
            else
                p.plate(at - half, 0, at + half, d, 0.25f + 2 * LIFT, component, StyleColor.NEEDLE.argb(), light);
        }
        if (vertical) {
            p.plate(w / 2 - 0.2f, m / 2, w / 2 + 0.2f, d - m / 2, 0.25f + LIFT, component, StyleColor.MARKING.argb(), light);
            float cy = d - m - f * (d - 2 * m);
            p.box(0.15f, cy - m, 0.25f, w - 0.15f, cy + m, h, component, color, light);
            p.plate(0.15f, cy - 0.12f, w - 0.15f, cy + 0.12f, h + LIFT, component, StyleColor.MARKING.argb(), light);
        } else {
            p.plate(m / 2, d / 2 - 0.2f, w - m / 2, d / 2 + 0.2f, 0.25f + LIFT, component, StyleColor.MARKING.argb(), light);
            float cx = m + f * (w - 2 * m);
            p.box(cx - m, 0.15f, 0.25f, cx + m, d - 0.15f, h, component, color, light);
            p.plate(cx - 0.12f, 0.15f, cx + 0.12f, d - 0.15f, h + LIFT, component, StyleColor.MARKING.argb(), light);
        }
    }

    /**
     * Create's analog lever in miniature: an iron base, a brass pivot, a
     * handle that leans from pulled back (0) to pushed forward (15), and a
     * small lamp in the corner that glows brighter with the value.
     */
    private void analogLever(ComponentPainter p, float w, float d, int value, int color) {
        p.box(0, 0, 0, w, d, 0.25f, iron, 0xFFFFFFFF, light);
        p.box(w / 2 - 0.8f, d / 2 - 0.8f, 0.25f, w / 2 + 0.8f, d / 2 + 0.8f, 0.75f, brass, 0xFFFFFFFF, light);
        boolean lit = value > 0;
        int lamp = scale(StyleColor.LEVER_LAMP.argb(), lit ? 0.45f + 0.55f * value / AnalogElement.MAX_VALUE : 0.3f);
        p.box(0.3f, 0.3f, 0.25f, 1.0f, 1.0f, 0.45f, component, lamp, lit ? ComponentPainter.FULL_BRIGHT : light);
        // one thickness all the way up; only the colour changes at the grip
        float r = AnalogLeverElement.HANDLE_HALF_WIDTH;
        float rod = AnalogLeverElement.HANDLE_LENGTH - AnalogLeverElement.HANDLE_GRIP;
        p.push();
        p.translate(w / 2, d / 2, AnalogLeverElement.PIVOT_HEIGHT);
        p.rotateX(AnalogLeverElement.angleFor(value));
        p.box(-r, -r, 0, r, r, rod, iron, 0xFFFFFFFF, light);
        p.box(-r, -r, rod, r, r, AnalogLeverElement.HANDLE_LENGTH, component, color, light);
        p.pop();
    }

    /**
     * The top plate of Create's Contraption Controls at half size, drawn from
     * Create's own textures (in sprite px, where a Create texel is half a px):
     * the dark plate, the square button standing out of its middle with the
     * filter's item on it, and the arrows either side, dark while the actors
     * run and lit red while they are stopped, as on Create's block.
     *
     * As an elevator's floor selector it shows the picked floor's name on
     * the button, the way Create does: white while that floor is where the
     * cabin is called to, orange while it is not, over a dark copy set off
     * behind. The arrows rest dark and sweep while the cabin travels.
     */
    private void actorSwitch(ComponentPainter p, Font font, ActorSwitchElement part, float w, float d, float h) {
        TextureAtlasSprite plate = ComponentPainter.sprite(ComponentPainter.CONTROLS_PLATE);
        TextureAtlasSprite arrows = ComponentPainter.sprite(ComponentPainter.CONTROLS_ARROWS);
        int white = 0xFFFFFFFF;
        float base = 0.5f;
        p.box(0, 0, 0, w, d, base, iron, white, light);
        p.textured(0, 0, w, d, base + LIFT, plate, 1, 9.5f, 7, 13.5f, white, light);
        // Create's sheet holds eight frames of the arrows' sweep, four to a column; the two it rests on
        // are the first of each column. A selector runs through them while the cabin travels.
        boolean selector = !part.floorName().isEmpty();
        int frame = selector ? 0 : part.running() ? 0 : 4;
        if (selector && part.cabinTravel() != 0 && Minecraft.getInstance().level != null) {
            int step = (int) (Minecraft.getInstance().level.getGameTime() / 2 % 8);
            frame = part.cabinTravel() > 0 ? step : 7 - step;
        }
        float u = frame < 4 ? 1 : 9, v = 0.5f + (frame % 4) * 4;
        int glow = frame == 0 ? light : ComponentPainter.FULL_BRIGHT;
        p.textured(0, 0.5f, 1.5f, d - 0.5f, base + 2 * LIFT, arrows, u, v, u + 1.5f, v + 3, white, glow);
        p.textured(w - 1.5f, 0.5f, w, d - 0.5f, base + 2 * LIFT, arrows, u + 4.5f, v, u + 6, v + 3, white, glow);
        float half = 1.525f, cx = w / 2, cy = d / 2;
        float top = part.isPressed() ? base + (h - base) * 0.4f : h;
        p.box(cx - half, cy - half, base, cx + half, cy + half, top, iron, white, light);
        p.textured(cx - half, cy - half, cx + half, cy + half, top + LIFT, plate, 2.5f, 10, 5.5f, 13, white, light);
        if (selector) {
            String name = part.floorName();
            float room = 2 * half - 0.5f;
            float lineHeight = Math.min(1.7f, room * font.lineHeight / Math.max(1, font.width(name)));
            float x = part.x() + cx - font.width(name) * lineHeight / font.lineHeight / 2;
            float y = part.y() + cy - lineHeight * ComponentPainter.GLYPH_HEIGHT / 2;
            int ink = (part.floorIsTarget() ? StyleColor.FLOOR_NUMBER_LIGHT : StyleColor.FLOOR_PICKED).argb();
            float off = 0.6f * lineHeight / font.lineHeight;
            p.faceText(font, name, x + off, y + off, top + 2 * LIFT, lineHeight, scale(ink, 0.25f), light);
            p.faceText(font, name, x, y, top + 3 * LIFT, lineHeight, ink, ComponentPainter.FULL_BRIGHT);
        } else if (!part.filter().isEmpty()) {
            p.item(part.filter(), cx, cy, top + 2 * LIFT, 1.9f, light);
        }
    }

    /**
     * A button with its floor's number on the cap, which glows while that
     * floor is where the cabin is going. The number is drawn as Create draws
     * a floor's name on its Contraption Controls: full-bright over a dark
     * copy set off behind it; or, for a pale cap, plainly in a dark ink.
     */
    private void floorButton(ComponentPainter p, Font font, FloorButtonElement button, float w, float d, float h, int color) {
        float bezel = 0.5f;
        boolean lit = button.isLit();
        int glow = lit ? ComponentPainter.FULL_BRIGHT : light;
        p.box(0, 0, 0, w, d, bezel, brass, 0xFFFFFFFF, light);
        float top = button.isPressed() ? h - bezel * 0.8f : h;
        p.box(bezel, bezel, bezel, w - bezel, d - bezel, top, component, lit ? color : scale(color, 0.55f), glow);
        String number = Integer.toString(button.floor());
        float lineHeight = number.length() > 1 ? 1.2f : 1.6f;
        float textWidth = font.width(number) * lineHeight / font.lineHeight;
        float x = button.x() + (w - textWidth) / 2, y = button.y() + (d - lineHeight * ComponentPainter.GLYPH_HEIGHT) / 2;
        if (button.lightNumber()) {
            int ink = StyleColor.FLOOR_NUMBER_LIGHT.argb();
            float off = 0.6f * lineHeight / font.lineHeight;
            p.faceText(font, number, x + off, y + off, top + LIFT, lineHeight, scale(ink, 0.25f), light);
            p.faceText(font, number, x, y, top + 2 * LIFT, lineHeight, ink, ComponentPainter.FULL_BRIGHT);
        } else {
            p.faceText(font, number, x, y, top + LIFT, lineHeight, StyleColor.FLOOR_NUMBER_DARK.argb(), glow);
        }
    }

    /** The limits a knob marks: its lowest and highest value, where they aren't the scale's own ends. */
    private static int[] stops(AnalogElement element) {
        return element.stops();
    }

    /** A lens set flush into a brass bezel: nothing stands proud of the rim, so it can't pass for a button. */
    private void lamp(ComponentPainter p, float w, float d, float h, int strength, int color) {
        float bezel = w >= 4 ? 0.5f : 0.25f;
        p.box(0, 0, 0, w, d, h, brass, 0xFFFFFFFF, light);
        boolean lit = strength > 0;
        int lens = lit ? scale(color, 0.55f + 0.6f * strength / 15f) : scale(color, 0.35f);
        p.plate(bezel, bezel, w - bezel, d - bezel, h + LIFT, component, lens, lit ? ComponentPainter.FULL_BRIGHT : light);
    }

    private void needleMeter(ComponentPainter p, float w, float d, int strength) {
        p.box(0, 0, 0, w, d, 0.5f, brass, 0xFFFFFFFF, light);
        p.plate(0.5f, 0.5f, w - 0.5f, d - 0.5f, 0.5f + LIFT, component, StyleColor.DIAL_FACE.argb(), light);
        float px = w / 2, py = d - 1;
        for (int tick = 0; tick <= 4; tick++) {
            p.push();
            p.translate(px, py, 0);
            p.rotateZ(-NeedleMeterElement.HALF_SWEEP + tick * NeedleMeterElement.HALF_SWEEP / 2);
            p.plate(-0.1f, -2.5f, 0.1f, -2.0f, 0.5f + 2 * LIFT, component, StyleColor.MARKING.argb(), light);
            p.pop();
        }
        p.push();
        p.translate(px, py, 0);
        p.rotateZ(NeedleMeterElement.angleFor(strength));
        p.box(-0.12f, -2.6f, 0.5f + 3 * LIFT, 0.12f, 0.3f, 0.75f, component, StyleColor.NEEDLE.argb(), light);
        p.pop();
        p.box(px - 0.4f, py - 0.4f, 0.5f, px + 0.4f, py + 0.4f, 0.85f, brass, 0xFFFFFFFF, light);
    }

    /** Fills left to right, or from the near edge up when vertical (like a vertical slider's 0). */
    private void barMeter(ComponentPainter p, float w, float d, boolean vertical, int strength, int color) {
        p.box(0, 0, 0, w, d, 0.5f, iron, 0xFFFFFFFF, light);
        p.plate(0.5f, 0.5f, w - 0.5f, d - 0.5f, 0.5f + LIFT, component, StyleColor.MARKING.argb(), light);
        if (strength <= 0)
            return;
        float z = 0.5f + 2 * LIFT;
        if (vertical)
            p.plate(0.5f, d - 0.5f - (d - 1) * strength / 15f, w - 0.5f, d - 0.5f, z, component, color,
                    ComponentPainter.FULL_BRIGHT);
        else
            p.plate(0.5f, 0.5f, 0.5f + (w - 1) * strength / 15f, d - 0.5f, z, component, color,
                    ComponentPainter.FULL_BRIGHT);
    }

    private void nixie(ComponentPainter p, Font font, PanelElement element, int[] digits) {
        String[] glyphs = new String[digits.length];
        for (int i = 0; i < digits.length; i++)
            glyphs[i] = digits[i] < 0 ? " " : Integer.toString(digits[i]);
        tubes(p, font, element, glyphs);
    }

    private void nixieStrip(ComponentPainter p, Font font, T panel, NixieStripElement strip) {
        String text = strip.glyphs(panel.displayLine(strip.line(0)).getString());
        String[] glyphs = new String[text.length()];
        for (int i = 0; i < glyphs.length; i++)
            glyphs[i] = text.substring(i, i + 1);
        tubes(p, font, strip, glyphs);
    }

    /** A row of tubes like Create's nixie: square brass sockets, all-glass bulbs, characters floating in the middle. */
    private void tubes(ComponentPainter p, Font font, PanelElement element, String[] glyphs) {
        float w = element.width(), d = element.depth(), h = element.height();
        p.box(0, 0, 0, w, d, 0.5f, iron, 0xFFFFFFFF, light);
        int count = glyphs.length;
        float tube = (w - 0.5f * (count + 1)) / count;
        float y0 = (d - tube) / 2, y1 = y0 + tube;
        float socket = 0.9f;
        for (int i = 0; i < count; i++) {
            float x0 = 0.5f + i * (tube + 0.5f), x1 = x0 + tube;
            p.box(x0, y0, 0.5f, x1, y1, socket, brass, 0xFFFFFFFF, light);
        }
        // characters float in the middle of each tube, readable from either side: standing
        // and facing the player on floors and ceilings, read end-on through the glass
        // (like a top-view tube) on walls, where the player looks straight at the face
        float lineHeight = 2.4f;
        for (int i = 0; i < count; i++) {
            if (glyphs[i].isBlank())
                continue;
            String glyph = glyphs[i];
            float x0 = 0.5f + i * (tube + 0.5f);
            float textWidth = font.width(glyph) * lineHeight / font.lineHeight;
            float textX = element.x() + x0 + (tube - textWidth) / 2, midY = element.y() + (y0 + y1) / 2;
            if (onWall)
                p.floatingText(font, glyph, textX, midY, (socket + h) / 2, lineHeight, StyleColor.NIXIE_GLOW.argb(),
                        ComponentPainter.FULL_BRIGHT);
            else
                p.standingText(font, glyph, textX, midY, (socket + h) / 2, lineHeight, StyleColor.NIXIE_GLOW.argb(),
                        ComponentPainter.FULL_BRIGHT, hanging);
        }
        // the glass last and see-through, from the socket to the top
        p.translucent(true);
        for (int i = 0; i < count; i++) {
            float x0 = 0.5f + i * (tube + 0.5f), x1 = x0 + tube;
            p.box(x0 + 0.1f, y0 + 0.1f, socket, x1 - 0.1f, y1 - 0.1f, h, component, StyleColor.NIXIE_GLASS.argb(), light);
        }
        p.translucent(false);
    }

    /** No bezel: the board's own stripes run down its sides, as on Create's block. One line of text per flap row. */
    private void displayBoard(ComponentPainter p, Font font, T panel, DisplayBoardElement board) {
        float w = board.width(), d = board.depth(), h = board.height();
        p.displayBoard(w, d, h, boardFront, boardSide, boardEdge, light);
        float rowDepth = d / board.rows(), room = w - 2 * BOARD_MARGIN;
        for (int row = 0; row < board.rows(); row++) {
            if (board.line(row) == ReadoutElement.NO_LINE)
                continue;
            String text = panel.displayLine(board.line(row)).getString().trim();
            if (text.isEmpty())
                continue;
            float lineHeight = Math.max(BOARD_LINE_MIN,
                    Math.min(BOARD_LINE, room * font.lineHeight / font.width(text)));
            int columns = (int) (room * font.lineHeight / lineHeight);
            if (font.width(text) > columns)
                text = font.plainSubstrByWidth(text, columns - font.width("…")) + "…";
            float y = board.y() + row * rowDepth + (rowDepth - lineHeight * ComponentPainter.GLYPH_HEIGHT) / 2;
            p.faceText(font, text, board.x() + BOARD_MARGIN, y, h + LIFT, lineHeight, StyleColor.BOARD_TEXT.argb(), light);
        }
    }

    /** One font line per text line of the footprint; nothing is drawn past the label's width. */
    private void label(ComponentPainter p, Font font, LabelElement label) {
        float lineHeight = label.lineHeight();
        int row = 0;
        for (String line : LabelText.lines(font, label)) {
            float textWidth = font.width(line) * lineHeight / font.lineHeight;
            float x = switch (label.alignment()) {
                case LEFT -> label.x() + LabelElement.TEXT_MARGIN;
                case RIGHT -> label.x() + label.width() - textWidth - LabelElement.TEXT_MARGIN;
                case CENTER -> label.x() + (label.width() - textWidth) / 2f;
            };
            // a font line includes descender space: nudge glyphs to sit centred
            p.faceText(font, line, x, label.y() + (row + 0.12f) * lineHeight, LIFT, lineHeight,
                    0xFF000000 | label.color(), light);
            row++;
        }
    }
}
