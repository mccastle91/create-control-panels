package dev.mccastle91.createcontrolling.compat.computercraft;

import dan200.computercraft.api.peripheral.IPeripheral;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
//? if neoforge {
import dan200.computercraft.api.peripheral.PeripheralCapability;
import dev.mccastle91.createcontrolling.registry.CCContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
//?} else {
/*import dan200.computercraft.api.ForgeComputerCraftAPI;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.eventbus.api.IEventBus;
*///?}

/**
 * Optional CC: Tweaked integration: a computer or wired modem next to a
 * console sees it as a peripheral. Nothing outside this package refers to
 * CC: Tweaked's classes, and this class is only loaded when the mod is present.
 */
public final class ComputerCraftCompat {

    private ComputerCraftCompat() {
    }

    public static void init(IEventBus modEventBus) {
        //? if neoforge {
        modEventBus.addListener(RegisterCapabilitiesEvent.class, event -> event.registerBlockEntity(
                PeripheralCapability.get(), CCContent.CONTROL_PANEL_ENTITY.get(),
                (console, side) -> peripheral(console)));
        //?} else {
        /*ForgeComputerCraftAPI.registerPeripheralProvider((level, pos, side) ->
                level.getBlockEntity(pos) instanceof PanelBlockEntity console
                        ? LazyOptional.of(() -> peripheral(console))
                        : LazyOptional.empty());
        *///?}
    }

    /** One peripheral per console, so computers on several sides share its state. */
    private static IPeripheral peripheral(PanelBlockEntity console) {
        return console.integrationData(ConsolePeripheral::new);
    }
}
