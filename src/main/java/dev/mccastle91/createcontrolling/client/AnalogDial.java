package dev.mccastle91.createcontrolling.client;

import com.mojang.math.Axis;
import dev.mccastle91.createcontrolling.panel.ConsoleCasing;
import dev.mccastle91.createcontrolling.panel.element.AnalogElement;
import dev.mccastle91.createcontrolling.panel.element.AnalogLeverElement;
import dev.mccastle91.createcontrolling.panel.element.KnobElement;
import dev.mccastle91.createcontrolling.panel.element.SliderElement;
import dev.mccastle91.createcontrolling.util.McCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * A knob or slider drawn flat for the screen, as seen from straight above:
 * the same plates, ticks, body and pointer as the part on the console (see
 * PanelBlockEntityRenderer), from the same textures and in the part's own
 * colour — so the dial window shows the control you are holding, not a
 * different widget.
 */
public final class AnalogDial {

    private static final ResourceLocation COMPONENT = McCompat.id("create_controlling", "textures/block/component.png");
    private static final ResourceLocation IRON = McCompat.id("create", "textures/block/industrial_iron_block.png");

    private static final int WHITE = 0xFFFFFFFF;
    /** The stop marks of a limited control, as on the console. */

    /** Drawing units per face pixel: fine enough for the parts' quarter- and tenth-pixel details. */
    private static final int UNITS = 20;

    private AnalogDial() {
    }

    /**
     * Draws {@code element} showing {@code value}, its top-left corner at
     * ({@code x}, {@code y}), {@code scale} screen pixels per face pixel.
     */
    public static void draw(GuiGraphics graphics, AnalogElement element, ConsoleCasing casing, int value,
                            float x, float y, float scale) {
        ResourceLocation trim = textureFile(casing.trimTexture());
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale / UNITS, scale / UNITS, 1f);
        int color = McCompat.dyeRgb(element.color());
        if (element instanceof KnobElement)
            knob(graphics, trim, element.width(), element.depth(), value, color, element.stops());
        else if (element instanceof SliderElement slider)
            slider(graphics, slider, value, color);
        else if (element instanceof AnalogLeverElement)
            lever(graphics, trim, element.width(), element.depth(), value, color);
        graphics.pose().popPose();
    }

    private static void knob(GuiGraphics graphics, ResourceLocation trim, float w, float d, int value, int color,
                             int[] stops) {
        rect(graphics, trim, 0, 0, w, d, WHITE);
        float r = w / 2;
        for (int tick : new int[] { 0, AnalogElement.MAX_VALUE / 2, AnalogElement.MAX_VALUE }) {
            turned(graphics, r, d / 2, KnobElement.angleFor(tick));
            rect(graphics, COMPONENT, -0.15f, -r + 0.05f, 0.15f, -r + 0.45f, StyleColor.MARKING.argb());
            graphics.pose().popPose();
        }
        for (int stop : stops) {
            turned(graphics, r, d / 2, KnobElement.angleFor(stop));
            rect(graphics, COMPONENT, -0.15f, -r + 0.05f, 0.15f, -r + 0.45f, StyleColor.NEEDLE.argb());
            graphics.pose().popPose();
        }
        turned(graphics, r, d / 2, KnobElement.angleFor(value));
        float body = r - 0.5f, cap = body - 0.4f;
        // the body's shoulder sits lower than its cap: a shade darker reads as that step
        rect(graphics, COMPONENT, -body, -body, body, body, shade(color, 0.8f));
        rect(graphics, COMPONENT, -cap, -cap, cap, cap, color);
        rect(graphics, COMPONENT, -0.25f, -body, 0.25f, -0.3f, StyleColor.POINTER.argb());
        graphics.pose().popPose();
    }

    private static void slider(GuiGraphics graphics, SliderElement slider, int value, int color) {
        float w = slider.width(), d = slider.depth();
        boolean vertical = slider.isVertical();
        rect(graphics, IRON, 0, 0, w, d, WHITE);
        for (float at : slider.stopLines()) {
            float half = SliderElement.STOP_LINE / 2;
            if (vertical)
                rect(graphics, COMPONENT, 0, at - half, w, at + half, StyleColor.NEEDLE.argb());
            else
                rect(graphics, COMPONENT, at - half, 0, at + half, d, StyleColor.NEEDLE.argb());
        }
        float f = SliderElement.fractionFor(value);
        float m = SliderElement.END_MARGIN;
        if (vertical) {
            rect(graphics, COMPONENT, w / 2 - 0.2f, m / 2, w / 2 + 0.2f, d - m / 2, StyleColor.MARKING.argb());
            float cy = d - m - f * (d - 2 * m);
            rect(graphics, COMPONENT, 0.15f, cy - m, w - 0.15f, cy + m, color);
            rect(graphics, COMPONENT, 0.15f, cy - 0.12f, w - 0.15f, cy + 0.12f, StyleColor.MARKING.argb());
        } else {
            rect(graphics, COMPONENT, m / 2, d / 2 - 0.2f, w - m / 2, d / 2 + 0.2f, StyleColor.MARKING.argb());
            float cx = m + f * (w - 2 * m);
            rect(graphics, COMPONENT, cx - m, 0.15f, cx + m, d - 0.15f, color);
            rect(graphics, COMPONENT, cx - 0.12f, 0.15f, cx + 0.12f, d - 0.15f, StyleColor.MARKING.argb());
        }
    }

    /** The lever from above: its handle's tip travels up the plate as it is pushed forward. */
    private static void lever(GuiGraphics graphics, ResourceLocation trim, float w, float d, int value, int color) {
        rect(graphics, IRON, 0, 0, w, d, WHITE);
        rect(graphics, trim, w / 2 - 0.8f, d / 2 - 0.8f, w / 2 + 0.8f, d / 2 + 0.8f, WHITE);
        float glow = value > 0 ? 0.45f + 0.55f * value / AnalogElement.MAX_VALUE : 0.3f;
        rect(graphics, COMPONENT, 0.3f, 0.3f, 1.0f, 1.0f, shade(StyleColor.LEVER_LAMP.argb(), glow));
        double lean = Math.toRadians(AnalogLeverElement.angleFor(value));
        float r = AnalogLeverElement.HANDLE_HALF_WIDTH;
        float tipY = d / 2 - (float) Math.sin(lean) * (AnalogLeverElement.HANDLE_LENGTH - r);
        rect(graphics, IRON, w / 2 - r, Math.min(d / 2, tipY), w / 2 + r, Math.max(d / 2, tipY), WHITE);
        rect(graphics, COMPONENT, w / 2 - r, tipY - r, w / 2 + r, tipY + r, color);
    }

    /** The texture file behind a block-atlas texture name ("create:block/brass_block"). */
    private static ResourceLocation textureFile(String texture) {
        int colon = texture.indexOf(':');
        return McCompat.id(texture.substring(0, colon), "textures/" + texture.substring(colon + 1) + ".png");
    }

    /** Pushes a pose turned {@code degrees} clockwise about face px ({@code cx}, {@code cy}); the caller pops it. */
    private static void turned(GuiGraphics graphics, float cx, float cy, float degrees) {
        graphics.pose().pushPose();
        graphics.pose().translate(cx * UNITS, cy * UNITS, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(degrees));
    }

    /** A textured, tinted rectangle in face px, showing as much of the texture as the part on the console does. */
    private static void rect(GuiGraphics graphics, ResourceLocation texture, float x0, float y0, float x1, float y1, int argb) {
        int left = Math.round(x0 * UNITS), top = Math.round(y0 * UNITS);
        int width = Math.round(x1 * UNITS) - left, height = Math.round(y1 * UNITS) - top;
        if (width <= 0 || height <= 0)
            return;
        int texW = Math.max(1, Math.min(16, Math.round(x1 - x0))), texH = Math.max(1, Math.min(16, Math.round(y1 - y0)));
        graphics.setColor(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, 1f);
        graphics.blit(texture, left, top, width, height, 0f, 0f, texW, texH, 16, 16);
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    private static int shade(int argb, float factor) {
        int r = (int) (((argb >> 16) & 0xFF) * factor);
        int g = (int) (((argb >> 8) & 0xFF) * factor);
        int b = (int) ((argb & 0xFF) * factor);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
