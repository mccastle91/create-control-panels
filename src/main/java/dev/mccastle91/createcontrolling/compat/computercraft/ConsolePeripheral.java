package dev.mccastle91.createcontrolling.compat.computercraft;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.element.AnalogElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A console as a CC: Tweaked peripheral ({@code control_console}). Through
 * the console it touches, a computer reaches every console joined to it (see
 * {@link ConsoleGroup}); each method takes an optional last argument naming
 * which, and works on the touched console without one. Components are
 * numbered from 1 in the order they were mounted. Reading gives what a
 * component sends or shows; writing operates a control, or for a lamp or
 * meter sends on its frequency from the console, or sets it directly when it
 * has no frequency.
 *
 * <p>Every method runs on the server thread. Each change of a component's
 * signal anywhere in the group queues
 * {@code console_signal(name, index, value, console)} on the attached computers.
 */
public final class ConsolePeripheral implements IPeripheral {

    public static final String TYPE = "control_console";
    public static final String EVENT = "console_signal";

    /** Peripherals with a computer attached: who a change anywhere may concern. */
    private static final Set<ConsolePeripheral> ACTIVE = ConcurrentHashMap.newKeySet();

    private final PanelBlockEntity console;
    private final Set<IComputerAccess> computers = ConcurrentHashMap.newKeySet();

    ConsolePeripheral(PanelBlockEntity console) {
        this.console = console;
    }

    /** Registered once as the consoles' signal listener (see {@link ComputerCraftCompat}). */
    static void signalChanged(PanelBlockEntity changed, PanelElement element, int strength) {
        if (ACTIVE.isEmpty())
            return;
        int index = ConsoleGroup.components(changed).indexOf(element) + 1;
        if (index == 0)
            return;
        List<PanelBlockEntity> group = null;
        Map<String, Object> source = null;
        for (ConsolePeripheral peripheral : ACTIVE) {
            if (peripheral.console.getLevel() != changed.getLevel())
                continue;
            if (group == null) {
                group = ConsoleGroup.of(changed);
                source = ConsoleGroup.describe(changed);
            }
            if (!group.contains(peripheral.console))
                continue;
            for (IComputerAccess computer : peripheral.computers)
                computer.queueEvent(EVENT, computer.getAttachmentName(), index, strength, source);
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public synchronized void attach(IComputerAccess computer) {
        computers.add(computer);
        ACTIVE.add(this);
    }

    /**
     * What computers were sending ends when the last one anywhere on the
     * group lets go, like their redstone output: links fall silent and
     * unlinked lamps and meters go dark.
     */
    @Override
    public void detach(IComputerAccess computer) {
        computers.remove(computer);
        MinecraftServer server = console.getLevel() == null ? null : console.getLevel().getServer();
        if (server == null) {
            releaseIfUnused();
            return;
        }
        server.execute(() -> {
            if (!releaseIfUnused() || console.isRemoved())
                return;
            List<PanelBlockEntity> group = ConsoleGroup.of(console);
            for (ConsolePeripheral other : ACTIVE)
                if (group.contains(other.console))
                    return;
            group.forEach(PanelBlockEntity::clearDrivenSignals);
        });
    }

    /** Leaves {@link #ACTIVE} once no computer is attached; true if it did. */
    private synchronized boolean releaseIfUnused() {
        if (!computers.isEmpty())
            return false;
        ACTIVE.remove(this);
        return true;
    }

    @Override
    public Object getTarget() {
        return console;
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof ConsolePeripheral peripheral && peripheral.console == console;
    }

    /**
     * The touched console and every console joined to it, the touched one
     * first: {@code name} (absent if it has none), {@code x}/{@code y}/{@code z},
     * {@code attached} (true for the one this peripheral is) and
     * {@code components} (how many it has). Any entry can be passed back as
     * the console argument of the other methods.
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getConsoles() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (PanelBlockEntity member : ConsoleGroup.of(console)) {
            Map<String, Object> entry = ConsoleGroup.describe(member);
            entry.put("attached", member == console);
            entry.put("components", ConsoleGroup.components(member).size());
            result.add(entry);
        }
        return result;
    }

    /**
     * {@code getComponents([console])}: every linkable component: {@code index},
     * {@code type}, {@code direction} ("transmit" for controls, "receive" for
     * lamps and meters), {@code linked} (whether it has a frequency),
     * {@code value} (0-15), for knobs, sliders and analog levers the
     * {@code min} and {@code max} they are limited to, {@code frequency} ({@code first}/{@code second}
     * item ids, absent where the slot is empty) and its {@code x}/{@code y}
     * on the face in px.
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getComponents(IArguments args) throws LuaException {
        PanelBlockEntity target = target(args, 0);
        List<Map<String, Object>> result = new ArrayList<>();
        List<PanelElement> components = ConsoleGroup.components(target);
        for (int i = 0; i < components.size(); i++) {
            PanelElement element = components.get(i);
            Map<String, Object> entry = new HashMap<>();
            entry.put("index", i + 1);
            entry.put("type", element.type().id());
            entry.put("direction", element.isTransmitter() ? "transmit" : "receive");
            entry.put("linked", !element.frequency().isEmpty());
            entry.put("value", PanelBlockEntity.signalOf(element));
            if (element instanceof AnalogElement analog) {
                entry.put("min", analog.min());
                entry.put("max", analog.max());
            }
            if (element instanceof dev.mccastle91.createcontrolling.panel.element.LampElement lamp)
                entry.put("inverted", lamp.inverted());
            element.describe(entry);
            entry.put("frequency", ConsoleGroup.frequencyTable(element.frequency()));
            entry.put("x", element.x());
            entry.put("y", element.y());
            result.add(entry);
        }
        return result;
    }

    /** {@code getValue(index[, console])}: the strength (0-15) a control is sending, or a lamp or meter shows. */
    @LuaFunction(mainThread = true)
    public final int getValue(IArguments args) throws LuaException {
        int index = args.getInt(0);
        return PanelBlockEntity.signalOf(component(target(args, 1), index));
    }

    /**
     * {@code setValue(index, value[, console])}: operates a control so that it
     * sends {@code value}: on/off controls take anything above 0 as on, a
     * momentary button gives one pulse. On a linked lamp or meter the console
     * sends {@code value} on the component's frequency; on an unlinked one it
     * shows {@code value} directly. Either lasts until set back to 0 or the
     * last computer on the group detaches.
     */
    @LuaFunction(mainThread = true)
    public final void setValue(IArguments args) throws LuaException {
        int index = args.getInt(0);
        int value = args.getInt(1);
        if (value < 0 || value > 15)
            throw new LuaException("Expected value in range 0-15");
        PanelBlockEntity target = target(args, 2);
        PanelElement element = component(target, index);
        if (!target.setSignal(element.id(), value))
            throw new LuaException("Component " + index + " can't be set");
    }

    /**
     * {@code setFrequency(index, first, second[, console])}: links a component
     * to the frequency of two item ids, as the designer does (nothing is
     * used up). Either may be nil for an empty slot; both nil unlinks it.
     */
    @LuaFunction(mainThread = true)
    public final void setFrequency(IArguments args) throws LuaException {
        int index = args.getInt(0);
        PanelBlockEntity target = target(args, 3);
        PanelElement element = component(target, index);
        try {
            element.setFrequency(ConsoleGroup.frequency(args.get(1), args.get(2)));
        } catch (IllegalArgumentException e) {
            throw new LuaException(e.getMessage());
        }
        target.markLayoutChanged();
    }

    private PanelBlockEntity target(IArguments args, int position) throws LuaException {
        try {
            return ConsoleGroup.select(console, args.get(position));
        } catch (IllegalArgumentException e) {
            throw new LuaException(e.getMessage());
        }
    }

    private static PanelElement component(PanelBlockEntity console, int index) throws LuaException {
        try {
            return ConsoleGroup.component(console, index);
        } catch (IllegalArgumentException e) {
            throw new LuaException(e.getMessage());
        }
    }
}
