package dev.mccastle91.createcontrolling.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mccastle91.createcontrolling.panel.PanelBlock;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.PanelFaceGeometry;
import dev.mccastle91.createcontrolling.panel.element.BarMeterElement;
import dev.mccastle91.createcontrolling.panel.element.ButtonElement;
import dev.mccastle91.createcontrolling.panel.element.ColorableElement;
import dev.mccastle91.createcontrolling.panel.element.CoveredSwitchElement;
import dev.mccastle91.createcontrolling.panel.element.KnobElement;
import dev.mccastle91.createcontrolling.panel.element.LabelElement;
import dev.mccastle91.createcontrolling.panel.element.LampElement;
import dev.mccastle91.createcontrolling.panel.element.LatchingButtonElement;
import dev.mccastle91.createcontrolling.panel.element.NeedleMeterElement;
import dev.mccastle91.createcontrolling.panel.element.NixieElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
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
 * Draws a console's components as small 3D parts — brass bezels, industrial
 * iron bodies, dye-coloured caps — standing on the sloped face. Everything is
 * placed through the same {@link PanelFaceGeometry} the click ray uses, so
 * what you see is exactly what you hit.
 */
public class PanelBlockEntityRenderer<T extends PanelBlockEntity> implements BlockEntityRenderer<T> {

    private static final int DIAL_FACE = 0xFFEDE4D4;
    private static final int NEEDLE = 0xFFB3261E;
    private static final int DARK = 0xFF1C1C1F;
    private static final int GLASS = 0x40F2C9A0;
    private static final int NIXIE_GLOW = 0xFFFF9A2E;

    /** Lever rod length (px) shared by the toggle and guarded switches; the tip adds {@link #LEVER_TIP}. */
    private static final float LEVER_LENGTH = 1.1f;
    private static final float LEVER_TIP = 0.8f;
    /**
     * Height of a decal over the surface it lies on, px: enough for the depth
     * buffer to keep them apart at range, too little to see.
     */
    private static final float LIFT = 0.05f;

    private TextureAtlasSprite component, brass, iron;
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
        brass = ComponentPainter.sprite(ComponentPainter.BRASS);
        iron = ComponentPainter.sprite(ComponentPainter.IRON);
        light = packedLight;
        AttachFace attach = state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE);
        hanging = attach == AttachFace.CEILING;
        onWall = attach == AttachFace.WALL;

        PanelFaceGeometry face = PanelFaceGeometry.of(state, panel.shape());
        ComponentPainter p = new ComponentPainter(face, poseStack.last().pose(), buffers);
        Font font = Minecraft.getInstance().font;

        for (PanelElement element : panel.getLayout().elements()) {
            p.push();
            p.translate(element.x(), element.y(), 0);
            float w = element.width(), d = element.depth(), h = element.height();
            if (element instanceof LabelElement label)
                label(p, font, label);
            else if (element instanceof CoveredSwitchElement covered)
                coveredSwitch(p, w, d, covered.state(), color(covered));
            else if (element instanceof SwitchElement toggle)
                toggle(p, w, d, toggle.isOn(), color(toggle));
            else if (element instanceof LatchingButtonElement latching)
                button(p, w, d, h, latching.isOn(), color(latching));
            else if (element instanceof ButtonElement button)
                button(p, w, d, h, button.isPressed(), color(button));
            else if (element instanceof KnobElement knob)
                knob(p, w, d, h, knob.value(), color(knob));
            else if (element instanceof SliderElement slider)
                slider(p, w, d, h, slider.isVertical(), slider.value(), color(slider));
            else if (element instanceof LampElement lamp)
                lamp(p, w, d, h, lamp.displayStrength(), color(lamp));
            else if (element instanceof NeedleMeterElement meter)
                needleMeter(p, w, d, meter.displayStrength());
            else if (element instanceof BarMeterElement meter)
                barMeter(p, w, d, meter.displayStrength(), color(meter));
            else if (element instanceof NixieElement nixie)
                nixie(p, font, element, nixie.digits());
            p.pop();
        }
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
    private void coveredSwitch(ComponentPainter p, float w, float d, CoveredSwitchElement.State state, int color) {
        toggleBase(p, w, d);
        lever(p, w / 2, d / 2, 0.5f, state == CoveredSwitchElement.State.ARMED, LEVER_LENGTH, 0xFFDDDDDD);
        float top = 2.4f, t = 0.2f, post = 0.25f, base = 0.25f;
        // posts just inside the lid's side walls, clear of the lever and its
        // pivot block; they stop under the lid's top so no faces coincide
        p.box(t, 0.05f, base, t + post, 0.45f, top - t, brass, 0xFFFFFFFF, light);
        p.box(w - t - post, 0.05f, base, w - t, 0.45f, top - t, brass, 0xFFFFFFFF, light);
        p.box(t + post, 0.05f, top - t - 0.25f, w - t - post, 0.25f, top - t, brass, 0xFFFFFFFF, light);
        p.push();
        p.translate(0, 0, top);
        if (state != CoveredSwitchElement.State.CLOSED)
            p.rotateX(90);
        // the walls stand on the base plate, not beside its edges
        p.box(0, 0, -t, w, d, 0, component, color, light);
        p.box(0, 0, base - top, t, d, -t, component, color, light);
        p.box(w - t, 0, base - top, w, d, -t, component, color, light);
        p.box(t, d - t, base - top, w - t, d, -t, component, color, light);
        p.pop();
    }

    private void knob(ComponentPainter p, float w, float d, float h, int value, int color) {
        p.box(0, 0, 0, w, d, 0.25f, brass, 0xFFFFFFFF, light);
        float r = w / 2;
        for (int tick : new int[] { 0, 15 / 2, 15 }) {
            p.push();
            p.translate(r, d / 2, 0);
            p.rotateZ(KnobElement.angleFor(tick));
            p.box(-0.15f, -r + 0.05f, 0.25f, 0.15f, -r + 0.45f, 0.3f, component, DARK, light);
            p.pop();
        }
        p.push();
        p.translate(r, d / 2, 0);
        p.rotateZ(KnobElement.angleFor(value));
        float body = r - 0.5f, cap = body - 0.4f;
        p.box(-body, -body, 0.25f, body, body, h - 0.4f, component, color, light);
        p.box(-cap, -cap, h - 0.4f, cap, cap, h, component, color, light);
        p.box(-0.25f, -body - LIFT, h - 0.6f, 0.25f, -0.3f, h + LIFT, component, 0xFFF0F0F0, light);
        p.pop();
    }

    private void slider(ComponentPainter p, float w, float d, float h, boolean vertical, int value, int color) {
        p.box(0, 0, 0, w, d, 0.25f, iron, 0xFFFFFFFF, light);
        float f = SliderElement.fractionFor(value);
        float m = SliderElement.END_MARGIN;
        if (vertical) {
            p.plate(w / 2 - 0.2f, m / 2, w / 2 + 0.2f, d - m / 2, 0.25f + LIFT, component, DARK, light);
            float cy = d - m - f * (d - 2 * m);
            p.box(0.15f, cy - m, 0.25f, w - 0.15f, cy + m, h, component, color, light);
            p.plate(0.15f, cy - 0.12f, w - 0.15f, cy + 0.12f, h + LIFT, component, DARK, light);
        } else {
            p.plate(m / 2, d / 2 - 0.2f, w - m / 2, d / 2 + 0.2f, 0.25f + LIFT, component, DARK, light);
            float cx = m + f * (w - 2 * m);
            p.box(cx - m, 0.15f, 0.25f, cx + m, d - 0.15f, h, component, color, light);
            p.plate(cx - 0.12f, 0.15f, cx + 0.12f, d - 0.15f, h + LIFT, component, DARK, light);
        }
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
        p.plate(0.5f, 0.5f, w - 0.5f, d - 0.5f, 0.5f + LIFT, component, DIAL_FACE, light);
        float px = w / 2, py = d - 1;
        for (int tick = 0; tick <= 4; tick++) {
            p.push();
            p.translate(px, py, 0);
            p.rotateZ(-NeedleMeterElement.HALF_SWEEP + tick * NeedleMeterElement.HALF_SWEEP / 2);
            p.plate(-0.1f, -2.5f, 0.1f, -2.0f, 0.5f + 2 * LIFT, component, DARK, light);
            p.pop();
        }
        p.push();
        p.translate(px, py, 0);
        p.rotateZ(NeedleMeterElement.angleFor(strength));
        p.box(-0.12f, -2.6f, 0.5f + 3 * LIFT, 0.12f, 0.3f, 0.75f, component, NEEDLE, light);
        p.pop();
        p.box(px - 0.4f, py - 0.4f, 0.5f, px + 0.4f, py + 0.4f, 0.85f, brass, 0xFFFFFFFF, light);
    }

    private void barMeter(ComponentPainter p, float w, float d, int strength, int color) {
        p.box(0, 0, 0, w, d, 0.5f, iron, 0xFFFFFFFF, light);
        p.plate(0.5f, 0.5f, w - 0.5f, d - 0.5f, 0.5f + LIFT, component, DARK, light);
        if (strength > 0)
            p.plate(0.5f, 0.5f, 0.5f + (w - 1) * strength / 15f, d - 0.5f, 0.5f + 2 * LIFT, component, color,
                    ComponentPainter.FULL_BRIGHT);
    }

    /** Two tubes like Create's nixie: square brass sockets, all-glass bulbs, digits floating in the middle. */
    private void nixie(ComponentPainter p, Font font, PanelElement element, int[] digits) {
        float w = element.width(), d = element.depth(), h = element.height();
        p.box(0, 0, 0, w, d, 0.5f, iron, 0xFFFFFFFF, light);
        float tube = (w - 1.5f) / 2;
        float y0 = (d - tube) / 2, y1 = y0 + tube;
        float socket = 0.9f;
        for (int i = 0; i < 2; i++) {
            float x0 = 0.5f + i * (tube + 0.5f), x1 = x0 + tube;
            p.box(x0, y0, 0.5f, x1, y1, socket, brass, 0xFFFFFFFF, light);
        }
        // digits float in the middle of each tube, readable from either side: standing
        // and facing the player on floors and ceilings, read end-on through the glass
        // (like a top-view tube) on walls, where the player looks straight at the face
        float lineHeight = 2.4f;
        for (int i = 0; i < 2; i++) {
            if (digits[i] < 0)
                continue;
            String digit = Integer.toString(digits[i]);
            float x0 = 0.5f + i * (tube + 0.5f);
            float textWidth = font.width(digit) * lineHeight / font.lineHeight;
            float textX = element.x() + x0 + (tube - textWidth) / 2, midY = element.y() + (y0 + y1) / 2;
            if (onWall)
                p.floatingText(font, digit, textX, midY, (socket + h) / 2, lineHeight, NIXIE_GLOW,
                        ComponentPainter.FULL_BRIGHT);
            else
                p.standingText(font, digit, textX, midY, (socket + h) / 2, lineHeight, NIXIE_GLOW,
                        ComponentPainter.FULL_BRIGHT, hanging);
        }
        // the glass last and see-through, from the socket to the top
        p.translucent(true);
        for (int i = 0; i < 2; i++) {
            float x0 = 0.5f + i * (tube + 0.5f), x1 = x0 + tube;
            p.box(x0 + 0.1f, y0 + 0.1f, socket, x1 - 0.1f, y1 - 0.1f, h, component, GLASS, light);
        }
        p.translucent(false);
    }

    private void label(ComponentPainter p, Font font, LabelElement label) {
        String text = label.text();
        if (text.isEmpty())
            return;
        float lineHeight = label.depth();
        float textWidth = font.width(text) * lineHeight / font.lineHeight;
        float x = switch (label.alignment()) {
            case LEFT -> label.x() + 0.1f;
            case RIGHT -> label.x() + label.width() - textWidth - 0.1f;
            case CENTER -> label.x() + (label.width() - textWidth) / 2f;
        };
        // a font line includes descender space: nudge glyphs to sit centred
        p.faceText(font, text, x, label.y() + lineHeight * 0.12f, LIFT, lineHeight, 0xFF000000 | label.color(), light);
    }
}
