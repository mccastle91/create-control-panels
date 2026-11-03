package dev.mccastle91.createcontrolling.panel.element;

/** Analog gauge: a needle sweeping 0-15 across a dial, styled after Create's gauges. */
public class NeedleMeterElement extends ReceiverElement {

    /** Half the needle sweep, degrees either side of straight up. */
    public static final float HALF_SWEEP = 60f;

    public NeedleMeterElement(PanelElementType<? extends NeedleMeterElement> type) {
        super(type);
    }

    /** Needle angle for a strength, degrees clockwise from straight up. */
    public static float angleFor(int strength) {
        return -HALF_SWEEP + strength / 15f * 2 * HALF_SWEEP;
    }
}
