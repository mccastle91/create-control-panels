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
    public static final PanelElementType<AnalogLeverElement> ANALOG_LEVER =
            PanelElementType.register("analog_lever", AnalogLeverElement::new, 3, 4, 3.2f);

    // Inputs
    public static final PanelElementType<LampElement> LAMP =
            PanelElementType.register("lamp", LampElement::new, 2, 2, 0.5f);
    public static final PanelElementType<LampElement> LARGE_LAMP =
            PanelElementType.register("large_lamp", LampElement::new, 4, 4, 0.5f);
    public static final PanelElementType<NeedleMeterElement> NEEDLE_METER =
            PanelElementType.register("needle_meter", NeedleMeterElement::new, 6, 5, 0.5f);
    // default lengths; each bar meter sets its own (see BarMeterElement). The horizontal one keeps
    // the id the single bar meter had before it was split in two, so older consoles load as they were
    public static final PanelElementType<BarMeterElement> BAR_METER =
            PanelElementType.register("bar_meter", type -> new BarMeterElement(type, false), 6, 2, 0.5f);
    public static final PanelElementType<BarMeterElement> BAR_METER_V =
            PanelElementType.register("bar_meter_v", type -> new BarMeterElement(type, true), 2, 6, 0.5f);
    public static final PanelElementType<NixieElement> NIXIE =
            PanelElementType.register("nixie", NixieElement::new, 6, 4, 3.5f);

    // Readouts show text from a Display Link; each sets its own width (see ReadoutElement)
    public static final PanelElementType<NixieStripElement> NIXIE_STRIP =
            PanelElementType.register("nixie_strip", NixieStripElement::new, 10.5f, 4, 3.5f);
    public static final PanelElementType<DisplayBoardElement> DISPLAY_BOARD =
            PanelElementType.register("display_board", DisplayBoardElement::new, 12, 6, 1.125f);

    // Passive; width is per-label, depth follows its text scale
    public static final PanelElementType<LabelElement> LABEL =
            PanelElementType.register("label", LabelElement::new,
                    LabelElement.DEFAULT_WIDTH, LabelElement.DEPTH_PER_SCALE, 0f);

    // Parts that act on the contraption the console rides (see compat/create/contraption):
    // Create's Contraption Controls, taken apart into a switch for actors and an elevator's panel.
    // Registered last, so they share the palette's second page. The actor switch is Create's
    // top plate at half size: 12 x 8 texels of its texture on 6 x 4 px.
    public static final PanelElementType<ActorSwitchElement> ACTOR_SWITCH =
            PanelElementType.register("actor_switch", ActorSwitchElement::new, 6, 4, 1.1f);
    public static final PanelElementType<FloorButtonElement> FLOOR_BUTTON =
            PanelElementType.register("floor_button", FloorButtonElement::new, 3, 3, 1.5f);
    public static final PanelElementType<FloorReadoutElement> FLOOR_READOUT =
            PanelElementType.register("floor_readout", FloorReadoutElement::new, 8.5f, 4, 3.5f);

    private PanelElements() {
    }

    public static void init() {
        // constants above register on class load
    }
}
