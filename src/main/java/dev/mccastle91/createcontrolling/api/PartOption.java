package dev.mccastle91.createcontrolling.api;

import net.minecraft.network.chat.Component;

/**
 * One setting of a component that the Console Designer shows when the
 * component is selected, without the designer knowing the component. A
 * component lists its options from {@code PanelElement.options()} and takes
 * the new value in {@code PanelElement.setOption(key, value)}, on the server.
 *
 * A toggle is one button that reads {@code label} and is green while on; a
 * stepper is {@code label} between a - and a + button. In both, the label is
 * the whole text shown, so it carries the value where that helps
 * ("Delay: 5 t"). The designer shows up to four options per component.
 *
 * @param key   what {@code setOption} is called with; unique within the component
 * @param value the current value; a toggle's is 0 or 1
 * @param min   lowest value a stepper sends
 * @param max   highest value a stepper sends
 * @param step  how far one click of - or + moves a stepper
 */
public record PartOption(String key, Kind kind, Component label, int value, int min, int max, int step) {

    public enum Kind { TOGGLE, STEPPER }

    public static PartOption toggle(String key, Component label, boolean on) {
        return new PartOption(key, Kind.TOGGLE, label, on ? 1 : 0, 0, 1, 1);
    }

    public static PartOption stepper(String key, Component label, int value, int min, int max, int step) {
        return new PartOption(key, Kind.STEPPER, label, value, min, max, Math.max(1, step));
    }

    /** The value a click sends: a toggle flips; a stepper moves one step, staying within its range. */
    public int next(boolean up) {
        if (kind == Kind.TOGGLE)
            return value == 0 ? 1 : 0;
        return Math.max(min, Math.min(max, value + (up ? step : -step)));
    }
}
