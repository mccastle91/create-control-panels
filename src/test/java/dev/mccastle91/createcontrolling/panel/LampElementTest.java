package dev.mccastle91.createcontrolling.panel;

import dev.mccastle91.createcontrolling.panel.element.LampElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElements;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A lamp's inverted option: what it shows turns over, the signal it reports does not. */
class LampElementTest {

    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        PanelElements.init();
        registries = VanillaRegistries.createLookup();
    }

    @Test
    void invertedLampIsLitWithoutASignal() {
        for (LampElement lamp : new LampElement[] { PanelElements.LAMP.create(), PanelElements.LARGE_LAMP.create() }) {
            assertFalse(lamp.inverted(), "lamps start the usual way round");
            assertEquals(0, lamp.brightness());
            lamp.receiveStrength(15);
            assertEquals(15, lamp.brightness());

            lamp.setInverted(true);
            assertEquals(0, lamp.brightness(), "full signal: dark");
            assertEquals(15, lamp.displayStrength(), "the signal it reports is unchanged");
            lamp.receiveStrength(4);
            assertEquals(11, lamp.brightness(), "a weak signal dims it a little");
            lamp.receiveStrength(0);
            assertEquals(15, lamp.brightness(), "no signal: fully lit");
            assertEquals(0, lamp.displayStrength());
        }
    }

    @Test
    void designerOpSetsItAndItIsSaved() {
        PanelLayout layout = new PanelLayout();
        LampElement lamp = PanelElements.LAMP.create();
        layout.addElement(lamp);

        CompoundTag op = new CompoundTag();
        op.putString(PanelEditOps.KEY_OP, PanelEditOps.OP_SET_OPTION);
        op.putUUID("Id", lamp.id());
        op.putString("Key", "inverted");
        op.putBoolean("Value", true);
        assertTrue(PanelEditOps.applyToLayout(layout, op, registries, CostPayer.FREE));
        assertTrue(lamp.inverted());

        PanelLayout loaded = PanelLayout.load(layout.save(registries), registries);
        assertTrue(((LampElement) loaded.elements().get(0)).inverted(), "the option survives a save");

        op.putBoolean("Value", false);
        assertTrue(PanelEditOps.applyToLayout(layout, op, registries, CostPayer.FREE));
        loaded = PanelLayout.load(layout.save(registries), registries);
        assertFalse(((LampElement) loaded.elements().get(0)).inverted());
    }
}
