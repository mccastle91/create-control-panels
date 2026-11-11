package dev.mccastle91.createcontrolling.compat.computercraft;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.mccastle91.createcontrolling.panel.FrequencyPair;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A console as a CC: Tweaked peripheral ({@code control_console}). Computers
 * see the console's linked components, numbered from 1 in the order they were
 * mounted, and work them through Create's link network: reading a component
 * gives what it sends or receives, writing operates a control or sends on a
 * lamp's or meter's frequency from the console.
 *
 * <p>Every method runs on the server thread. Each change of a component's
 * signal queues {@code console_signal(name, index, value)} on the attached
 * computers.
 */
public final class ConsolePeripheral implements IPeripheral, PanelBlockEntity.SignalListener {

    public static final String TYPE = "control_console";
    public static final String EVENT = "console_signal";

    private final PanelBlockEntity console;
    private final Set<IComputerAccess> computers = ConcurrentHashMap.newKeySet();

    ConsolePeripheral(PanelBlockEntity console) {
        this.console = console;
        console.addSignalListener(this);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void attach(IComputerAccess computer) {
        computers.add(computer);
    }

    /** What computers were sending ends when the last one lets go, like their redstone output. */
    @Override
    public void detach(IComputerAccess computer) {
        computers.remove(computer);
        MinecraftServer server = console.getLevel() == null ? null : console.getLevel().getServer();
        if (server != null)
            server.execute(() -> {
                if (computers.isEmpty() && !console.isRemoved())
                    console.clearDrivenSignals();
            });
    }

    @Override
    public Object getTarget() {
        return console;
    }

    @Override
    public boolean equals(IPeripheral other) {
        return other instanceof ConsolePeripheral peripheral && peripheral.console == console;
    }

    @Override
    public void signalChanged(PanelElement element, int strength) {
        if (computers.isEmpty())
            return;
        int index = linkedComponents().indexOf(element) + 1;
        if (index == 0)
            return;
        for (IComputerAccess computer : computers)
            computer.queueEvent(EVENT, computer.getAttachmentName(), index, strength);
    }

    /**
     * Every linkable component: {@code index}, {@code type}, {@code direction}
     * ("transmit" for controls, "receive" for lamps and meters), {@code value}
     * (0-15), {@code frequency} ({@code first}/{@code second} item ids, absent
     * where the slot is empty) and its {@code x}/{@code y} on the face in px.
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getComponents() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<PanelElement> components = linkedComponents();
        for (int i = 0; i < components.size(); i++) {
            PanelElement element = components.get(i);
            Map<String, Object> entry = new HashMap<>();
            entry.put("index", i + 1);
            entry.put("type", element.type().id());
            entry.put("direction", element.isTransmitter() ? "transmit" : "receive");
            entry.put("value", PanelBlockEntity.signalOf(element));
            entry.put("frequency", frequencyTable(element.frequency()));
            entry.put("x", element.x());
            entry.put("y", element.y());
            result.add(entry);
        }
        return result;
    }

    /** The strength (0-15) a control is sending, or a lamp or meter is receiving. */
    @LuaFunction(mainThread = true)
    public final int getValue(int index) throws LuaException {
        return PanelBlockEntity.signalOf(component(index));
    }

    /**
     * Operates a control so that it sends {@code value}: on/off controls take
     * anything above 0 as on, a momentary button gives one pulse. On a lamp or
     * meter the console sends {@code value} on the component's frequency until
     * it is set back to 0 or the computer detaches.
     */
    @LuaFunction(mainThread = true)
    public final void setValue(int index, int value) throws LuaException {
        if (value < 0 || value > 15)
            throw new LuaException("Expected value in range 0-15");
        PanelElement element = component(index);
        if (!console.setSignal(element.id(), value))
            throw new LuaException("Component " + index + " has no frequency");
    }

    private PanelElement component(int index) throws LuaException {
        List<PanelElement> components = linkedComponents();
        if (index < 1 || index > components.size())
            throw new LuaException("No component " + index + " (console has " + components.size() + ")");
        return components.get(index - 1);
    }

    private List<PanelElement> linkedComponents() {
        List<PanelElement> components = new ArrayList<>();
        for (PanelElement element : console.getLayout().elements())
            if (element.isTransmitter() || element.isReceiver())
                components.add(element);
        return components;
    }

    private static Map<String, Object> frequencyTable(FrequencyPair frequency) {
        Map<String, Object> table = new HashMap<>();
        putItem(table, "first", frequency.first());
        putItem(table, "second", frequency.second());
        return table;
    }

    private static void putItem(Map<String, Object> table, String key, ItemStack stack) {
        if (!stack.isEmpty())
            table.put(key, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
}
