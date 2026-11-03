package dev.mccastle91.createcontrolling.panel.element;

import net.minecraft.world.item.DyeColor;

/**
 * Linear fader along its long axis. Horizontal sliders run 0 (left) to 15
 * (right); vertical ones 0 (near edge) to 15 (far edge). Clicking steps it
 * one forward, sneak-clicking one back.
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
}
