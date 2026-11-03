package dev.mccastle91.createcontrolling.panel.element;

import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;

/**
 * Linear fader along its long axis. Horizontal sliders run 0 (left) to 15
 * (right); vertical ones 0 (near edge) to 15 (far edge). Clicking sets the
 * value at the clicked point; sneak-clicking steps one towards it.
 */
public class SliderElement extends AnalogElement {

    public static final DyeColor DEFAULT_COLOR = DyeColor.WHITE;
    /** Track inset at each end, px: the knob's half-length. */
    public static final float END_MARGIN = 0.75f;

    private final boolean vertical;

    public SliderElement(PanelElementType<? extends SliderElement> type, boolean vertical) {
        super(type, DEFAULT_COLOR);
        this.vertical = vertical;
    }

    @Override
    protected DyeColor defaultColor() {
        return DEFAULT_COLOR;
    }

    public boolean isVertical() {
        return vertical;
    }

    /** Knob position along the track as a 0-1 fraction for a value. */
    public static float fractionFor(int value) {
        return value / (float) MAX_VALUE;
    }

    public int valueAt(float localX, float localY) {
        float length = (vertical ? depth() : width()) - 2 * END_MARGIN;
        float along = vertical ? depth() - END_MARGIN - localY : localX - END_MARGIN;
        float fraction = Mth.clamp(along / length, 0f, 1f);
        return Math.round(fraction * MAX_VALUE);
    }

    @Override
    public boolean click(float localX, float localY, boolean sneaking) {
        int target = valueAt(localX, localY);
        return sneaking ? stepTowards(target) : setValue(target);
    }
}
