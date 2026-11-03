package dev.mccastle91.createcontrolling.panel.element;

import net.minecraft.world.item.DyeColor;

/**
 * Rotary knob (Control Engineering's variac): the pointer sweeps 270°, from
 * lower-left (0) over the top to lower-right (15). Clicking steps it one
 * forward, sneak-clicking one back.
 */
public class KnobElement extends AnalogElement {

    public static final DyeColor DEFAULT_COLOR = DyeColor.BLACK;
    /** Half the sweep, degrees; 0° points at the far (top) edge of the face. */
    public static final float HALF_SWEEP = 135f;

    public KnobElement(PanelElementType<? extends KnobElement> type) {
        super(type, DEFAULT_COLOR);
    }

    @Override
    protected DyeColor defaultColor() {
        return DEFAULT_COLOR;
    }

    /** Pointer angle for a value, degrees clockwise from the far edge. */
    public static float angleFor(int value) {
        return -HALF_SWEEP + value / (float) MAX_VALUE * 2 * HALF_SWEEP;
    }
}
