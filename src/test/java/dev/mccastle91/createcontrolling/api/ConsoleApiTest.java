package dev.mccastle91.createcontrolling.api;

import dev.mccastle91.createcontrolling.config.CCConfig;
import dev.mccastle91.createcontrolling.panel.ComponentCosts;
import dev.mccastle91.createcontrolling.panel.CostPayer;
import dev.mccastle91.createcontrolling.panel.PanelEditOps;
import dev.mccastle91.createcontrolling.panel.PanelLayout;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElementType;
import dev.mccastle91.createcontrolling.panel.element.PanelElements;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A component from "another mod", taken through everything the add-on API promises on the server side. */
class ConsoleApiTest {

    /** A stand-in add-on part: a transmitter with a level it steps and a mode it toggles. */
    static class Dimmer extends PanelElement {
        int level = 2;
        boolean fast;

        Dimmer(PanelElementType<Dimmer> type) {
            super(type);
        }

        @Override
        public boolean isTransmitter() {
            return true;
        }

        @Override
        public boolean isReceiver() {
            return false;
        }

        @Override
        public int transmittedStrength() {
            return level * 5;
        }

        @Override
        public List<PartOption> options() {
            return List.of(PartOption.stepper("level", Component.literal("Level: " + level), level, 0, 3, 1),
                    PartOption.toggle("fast", Component.literal("Fast"), fast));
        }

        @Override
        public boolean setOption(String key, int value) {
            if (key.equals("level"))
                level = Mth.clamp(value, 0, 3);
            else if (key.equals("fast"))
                fast = value != 0;
            else
                return false;
            return true;
        }

        @Override
        public void describe(Map<String, Object> out) {
            out.put("level", level);
        }

        @Override
        protected void saveOptions(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("Level", level);
            tag.putBoolean("Fast", fast);
        }

        @Override
        protected void loadOptions(CompoundTag tag, HolderLookup.Provider registries) {
            level = tag.getInt("Level");
            fast = tag.getBoolean("Fast");
        }
    }

    private static HolderLookup.Provider registries;
    private static PanelElementType<Dimmer> dimmer;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        PanelElements.init();
        registries = VanillaRegistries.createLookup();
        dimmer = ConsoleApi.registerComponent("apitest:dimmer", Dimmer::new, 3, 2, 1);
        ConsoleApi.setCost(dimmer, "minecraft:lever", 2);
    }

    private static CompoundTag op(String kind) {
        CompoundTag tag = new CompoundTag();
        tag.putString(PanelEditOps.KEY_OP, kind);
        return tag;
    }

    @Test
    void idsNeedANamespaceAndStayUnique() {
        assertThrows(IllegalArgumentException.class, () -> ConsoleApi.registerComponent("dimmer", Dimmer::new, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> ConsoleApi.registerComponent(":dimmer", Dimmer::new, 1, 1, 1));
        assertThrows(IllegalStateException.class, () -> ConsoleApi.registerComponent("apitest:dimmer", Dimmer::new, 1, 1, 1));
        assertEquals("apitest.dimmer", dimmer.langId(), "translation keys take a dot for the colon");
        assertEquals("knob", PanelElements.KNOB.langId());
        assertTrue(PanelElementType.all().contains(dimmer), "it joins the palette");
    }

    @Test
    void costIsChargedLikeAnyOtherPart() {
        List<ComponentCosts.Cost> cost = ComponentCosts.costOf(dimmer);
        assertEquals(1, cost.size());
        assertEquals("minecraft:lever", cost.get(0).key());
        assertEquals(2, cost.get(0).count());

        CCConfig.componentsCostMaterials = false;
        try {
            assertTrue(ComponentCosts.costOf(dimmer).isEmpty(), "costs switched off in the config: free");
            assertTrue(ComponentCosts.costOf(PanelElements.KNOB).isEmpty());
        } finally {
            CCConfig.componentsCostMaterials = true;
        }
    }

    @Test
    void designerEditsReachThePartAndSurviveSaving() {
        PanelLayout layout = new PanelLayout();
        CompoundTag add = op(PanelEditOps.OP_ADD);
        add.putString("Type", dimmer.id());
        add.putFloat("X", 4);
        add.putFloat("Y", 4);
        assertTrue(PanelEditOps.applyToLayout(layout, add, registries, CostPayer.FREE));
        Dimmer part = (Dimmer) layout.elements().get(0);
        assertEquals(10, ConsoleApi.signal(part));

        // the designer steps the first option up, then flips the second
        PartOption level = part.options().get(0);
        assertEquals(3, level.next(true));
        assertEquals(1, level.next(false));
        CompoundTag set = op(PanelEditOps.OP_SET_OPTION);
        set.putUUID("Id", part.id());
        set.putString("Key", level.key());
        set.putInt("Value", level.next(true));
        assertTrue(PanelEditOps.applyToLayout(layout, set, registries, CostPayer.FREE));
        assertEquals(3, part.level);
        assertEquals(3, part.options().get(0).next(true), "a stepper stops at its end");

        PartOption fast = part.options().get(1);
        set.putString("Key", fast.key());
        set.putInt("Value", fast.next(true));
        assertTrue(PanelEditOps.applyToLayout(layout, set, registries, CostPayer.FREE));
        assertTrue(part.fast);

        // a value from the wire is clamped by the part, not trusted
        set.putString("Key", "level");
        set.putInt("Value", 99);
        PanelEditOps.applyToLayout(layout, set, registries, CostPayer.FREE);
        assertEquals(3, part.level);

        PanelLayout loaded = PanelLayout.load(layout.save(registries), registries);
        Dimmer again = (Dimmer) loaded.elements().get(0);
        assertEquals(3, again.level);
        assertTrue(again.fast);
        assertEquals(part.id(), again.id());

        Map<String, Object> described = new HashMap<>();
        again.describe(described);
        assertEquals(3, described.get("level"));
    }

    @Test
    void ownPartsClaimNoGenericOptions() {
        // the mod's own parts keep their built-in designer controls and edit ops
        PanelElement lamp = PanelElements.LAMP.create();
        assertTrue(lamp.options().isEmpty());
        assertFalse(lamp.setOption("inverted", 1));
    }
}
