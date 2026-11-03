package dev.mccastle91.createcontrolling.panel.element;

import net.minecraft.world.item.DyeColor;

/**
 * Rotary knob (Control Engineering's variac): the pointer sweeps 270°, from
 * lower-left (0) over the top to lower-right (15). Clicking sets the value to
 * the angle of the point clicked; sneak-clicking steps one towards it.
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

    /** Value for a click at ({@code localX}, {@code localY}) inside the knob's footprint. */
    public int valueAt(float localX, float localY) {
        float dx = localX - width() / 2f;
        float dy = localY - depth() / 2f;
        // face y grows towards the viewer, so "up" (the far edge) is -y
        double degrees = Math.toDegrees(Math.atan2(dx, -dy));
        double clamped = Math.max(-HALF_SWEEP, Math.min(HALF_SWEEP, degrees));
        return (int) Math.round((clamped + HALF_SWEEP) / (2 * HALF_SWEEP) * MAX_VALUE);
    }

    @Override
    public boolean click(float localX, float localY, boolean sneaking) {
        int target = valueAt(localX, localY);
        return sneaking ? stepTowards(target) : setValue(target);
    }
}
