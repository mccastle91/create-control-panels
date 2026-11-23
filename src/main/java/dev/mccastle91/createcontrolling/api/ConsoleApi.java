package dev.mccastle91.createcontrolling.api;

import dev.mccastle91.createcontrolling.panel.ComponentCosts;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElementType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The entry point for other mods: add-ons that put their own components on
 * consoles, and mods that read or work the consoles in a world. See
 * {@code docs/api.md} for a worked example.
 *
 * <b>What is stable.</b> This package and {@code api.client}; and of the
 * classes they hand out, the public and protected members of
 * {@link PanelElement} (and its {@code ReceiverElement}, {@code AnalogElement}
 * and {@code ColorableElement} relatives), {@link PanelElementType},
 * {@link ComponentCosts.Cost}, {@code PartSound}, and on {@link PanelBlockEntity}
 * the methods this class forwards to plus {@code getLayout()}, {@code casing()},
 * {@code shape()} and {@code name()}. Everything else may change between
 * versions without notice. {@link #VERSION} goes up when something here
 * changes in a way an add-on could notice.
 *
 * <b>When to call.</b> Register components during mod construction or common
 * setup, on both sides, before any world loads: a saved console drops parts
 * whose type it doesn't know. Renderers are registered on the client only,
 * through {@code api.client.ConsoleClientApi}.
 */
public final class ConsoleApi {

    /** 1: first release (mod 0.2.8). */
    public static final int VERSION = 1;

    private ConsoleApi() {
    }

    // --- Components ------------------------------------------------------------

    /**
     * Registers a kind of component. It appears in the designer's palette
     * after the mod's own, in registration order.
     *
     * @param id      {@code "yourmodid:name"}; saved with every such part, so never change it.
     *                The palette button reads the translation
     *                {@code gui.create_controlling.add.yourmodid.name} (keep it under ~10 letters)
     *                and the part is called {@code gui.create_controlling.element.yourmodid.name}
     * @param factory builds a blank part of this type; called on both sides
     * @param width   footprint across the face, in px of the 16 px face (parts sit inside its 1 px frame)
     * @param depth   footprint down the face, px
     * @param height  how far it stands off the face, px: what the crosshair can hit
     * @throws IllegalArgumentException if the id has no namespace
     * @throws IllegalStateException    if the id is taken
     */
    public static <T extends PanelElement> PanelElementType<T> registerComponent(
            String id, Function<PanelElementType<T>, T> factory, float width, float depth, float height) {
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1)
            throw new IllegalArgumentException("Component id must be \"modid:name\", got \"" + id + "\"");
        return PanelElementType.register(id, factory, width, depth, height);
    }

    /**
     * What a component costs to mount: pairs of an item id ({@code "minecraft:lever"})
     * or an item tag with a leading # ({@code "#minecraft:wooden_buttons"}) and a count.
     * A component with no cost set is free. A tag line is named in tooltips by the
     * translation {@code gui.create_controlling.cost_tag.<namespace>.<path>}.
     */
    public static void setCost(PanelElementType<?> type, Object... keyCountPairs) {
        if (keyCountPairs.length % 2 != 0)
            throw new IllegalArgumentException("Expected pairs of item (or #tag) and count");
        List<ComponentCosts.Cost> lines = new ArrayList<>();
        for (int i = 0; i < keyCountPairs.length; i += 2)
            lines.add(new ComponentCosts.Cost((String) keyCountPairs[i], (Integer) keyCountPairs[i + 1]));
        ComponentCosts.setCost(type, lines);
    }

    // --- Consoles in the world ---------------------------------------------------

    /** The console at a position, if there is one. */
    public static Optional<PanelBlockEntity> console(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PanelBlockEntity console ? Optional.of(console) : Optional.empty();
    }

    /** A console's components, in the order they were added. Read-only: change them through the methods here. */
    public static List<PanelElement> components(PanelBlockEntity console) {
        return console.getLayout().elements();
    }

    /** The strength (0-15) a control is sending, or the one a lamp, meter or readout shows. */
    public static int signal(PanelElement component) {
        return PanelBlockEntity.signalOf(component);
    }

    /**
     * Server only. Works a control as a hand would so that it sends
     * {@code strength} (on/off parts take anything above 0 as on; a momentary
     * button gives one pulse). For a lamp or meter with a frequency the
     * console sends {@code strength} on it, and one without shows it directly;
     * either lasts until set back to 0 (see {@link #release}).
     * Returns false if the component isn't on this console or has nothing to set.
     */
    public static boolean setSignal(PanelBlockEntity console, PanelElement component, int strength) {
        return console.setSignal(component.id(), strength);
    }

    /** Server only. Ends everything {@link #setSignal} made this console's lamps and meters show or send. */
    public static void release(PanelBlockEntity console) {
        console.clearDrivenSignals();
    }

    /**
     * Server only: told whenever the signal of a component on any console
     * changes. Keep the listener to remove it again.
     */
    public static void addSignalListener(PanelBlockEntity.SignalListener listener) {
        PanelBlockEntity.addSignalListener(listener);
    }

    public static void removeSignalListener(PanelBlockEntity.SignalListener listener) {
        PanelBlockEntity.removeSignalListener(listener);
    }

    /** Server only: told when a console's components are connected, on load and after every change to its design. */
    public static void addLayoutListener(PanelBlockEntity.LayoutListener listener) {
        PanelBlockEntity.addLayoutListener(listener);
    }
}
