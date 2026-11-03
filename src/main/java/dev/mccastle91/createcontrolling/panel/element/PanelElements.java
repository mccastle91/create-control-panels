package dev.mccastle91.createcontrolling.panel.element;

/**
 * The component set. Sizes are footprint width × depth on the 16×16 px face,
 * plus how far the part stands off it. Class-loading this registers all types;
 * registration order is palette order.
 */
public final class PanelElements {

    // Outputs
    public static final PanelElementType<ButtonElement> BUTTON =
            PanelElementType.register("button", ButtonElement::new, 3, 3, 1.5f);
    public static final PanelElementType<LatchingButtonElement> LATCHING_BUTTON =
            PanelElementType.register("latching_button", LatchingButtonElement::new, 3, 3, 1.5f);
    public static final PanelElementType<ButtonElement> LARGE_BUTTON =
            PanelElementType.register("large_button", ButtonElement::new, 5, 5, 1.5f);
    public static final PanelElementType<SwitchElement> TOGGLE_SWITCH =
            PanelElementType.register("toggle_switch", SwitchElement::new, 2, 3, 2.25f);
    public static final PanelElementType<CoveredSwitchElement> COVERED_SWITCH =
            PanelElementType.register("covered_switch", CoveredSwitchElement::new, 2, 3, 2.4f);
    public static final PanelElementType<KnobElement> KNOB =
            PanelElementType.register("knob", KnobElement::new, 4, 4, 1.25f);
    public static final PanelElementType<SliderElement> SLIDER_H =
            PanelElementType.register("slider_h", type -> new SliderElement(type, false), 8, 1.5f, 1f);
    public static final PanelElementType<SliderElement> SLIDER_V =
            PanelElementType.register("slider_v", type -> new SliderElement(type, true), 1.5f, 8, 1f);

    // Inputs
    public static final PanelElementType<LampElement> LAMP =
            PanelElementType.register("lamp", LampElement::new, 2, 2, 0.5f);
    public static final PanelElementType<LampElement> LARGE_LAMP =
            PanelElementType.register("large_lamp", LampElement::new, 4, 4, 0.5f);
    public static final PanelElementType<NeedleMeterElement> NEEDLE_METER =
            PanelElementType.register("needle_meter", NeedleMeterElement::new, 6, 5, 0.5f);
    // default length; each bar meter sets its own (see BarMeterElement)
    public static final PanelElementType<BarMeterElement> BAR_METER =
            PanelElementType.register("bar_meter", BarMeterElement::new, 6, 2, 0.5f);
    public static final PanelElementType<NixieElement> NIXIE =
            PanelElementType.register("nixie", NixieElement::new, 6, 4, 3.5f);

    // Passive; width is per-label, depth follows its text scale
    public static final PanelElementType<LabelElement> LABEL =
            PanelElementType.register("label", LabelElement::new,
                    LabelElement.DEFAULT_WIDTH, LabelElement.DEPTH_PER_SCALE, 0f);

    private PanelElements() {
    }

    public static void init() {
        // constants above register on class load
    }
}
