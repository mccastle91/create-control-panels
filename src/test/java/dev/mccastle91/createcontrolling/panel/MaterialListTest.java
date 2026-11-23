package dev.mccastle91.createcontrolling.panel;

import dev.mccastle91.createcontrolling.item.PanelBlueprintItem;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElements;
import dev.mccastle91.createcontrolling.registry.CCContent;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a design costs as a whole, how a cost line is coloured, and drawing on a blueprint. */
class MaterialListTest {

    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        PanelElements.init();
        registries = VanillaRegistries.createLookup();
    }

    private static CompoundTag add(String type) {
        CompoundTag tag = new CompoundTag();
        tag.putString(PanelEditOps.KEY_OP, PanelEditOps.OP_ADD);
        tag.putString("Type", type);
        tag.putFloat("X", 6);
        tag.putFloat("Y", 6);
        return tag;
    }

    @Test
    void statusIsNoneSomeOrAll() {
        assertEquals(MaterialList.Status.NONE, MaterialList.status(0, 2));
        assertEquals(MaterialList.Status.SOME, MaterialList.status(1, 2));
        assertEquals(MaterialList.Status.ALL, MaterialList.status(2, 2));
        assertEquals(MaterialList.Status.ALL, MaterialList.status(5, 2));
    }

    @Test
    void totalAddsUpEveryPartByItem() {
        PanelLayout layout = new PanelLayout();
        layout.addElement(PanelElements.LAMP.create());
        layout.addElement(PanelElements.LARGE_LAMP.create());
        layout.addElement(PanelElements.TOGGLE_SWITCH.create());
        layout.addElement(PanelElements.LATCHING_BUTTON.create());

        List<ComponentCosts.Cost> total = ComponentCosts.totalOf(layout);
        assertEquals(3, total.size(), "lamps, levers and glowstone: " + total);
        assertTrue(total.get(0).matches(new ItemStack(Items.REDSTONE_LAMP)));
        assertEquals(3, total.get(0).count(), "one lamp + a large lamp's two");
        assertTrue(total.get(1).matches(new ItemStack(Items.LEVER)));
        assertEquals(2, total.get(1).count(), "the switch's lever + the latching button's");
        assertTrue(total.get(2).matches(new ItemStack(Items.GLOWSTONE_DUST)));
        assertEquals(1, total.get(2).count());
        assertTrue(ComponentCosts.totalOf(new PanelLayout()).isEmpty());
    }

    @Test
    void shortfallReusesWhatTheConsolePaid() {
        PanelLayout console = new PanelLayout();
        PanelElement paidLamp = PanelElements.LAMP.create();
        paidLamp.setPaid(List.of(new ItemStack(Items.REDSTONE_LAMP)));
        console.addElement(paidLamp);

        PanelLayout design = new PanelLayout();
        design.addElement(PanelElements.LARGE_LAMP.create());
        design.addElement(PanelElements.TOGGLE_SWITCH.create());

        List<ComponentCosts.Cost> shortfall = ComponentCosts.shortfall(console, design);
        assertEquals(2, shortfall.size(), shortfall.toString());
        assertEquals(1, shortfall.get(0).count(), "two lamps needed, one already on the console");
        assertEquals(1, shortfall.get(1).count(), "the lever is all new");
        assertEquals(1, paidLamp.paid().get(0).getCount(), "working it out must not touch the console's parts");
        assertTrue(ComponentCosts.shortfall(console, console).isEmpty(), "the same design again costs nothing");
    }

    @Test
    void drawingOnABlueprintIsFreeAndClears() {
        ItemStack blueprint = new ItemStack(CCContent.PANEL_BLUEPRINT.get());
        assertEquals(0, PanelBlueprintItem.partCount(blueprint));
        assertTrue(PanelBlueprintItem.design(blueprint, registries).isEmpty());

        assertTrue(PanelBlueprintItem.edit(blueprint, add("lamp"), registries));
        assertTrue(PanelBlueprintItem.edit(blueprint, add("toggle_switch"), registries));
        assertEquals(2, PanelBlueprintItem.partCount(blueprint));
        PanelLayout design = PanelBlueprintItem.design(blueprint, registries);
        assertEquals(2, design.elementCount());
        for (PanelElement element : design.elements())
            assertTrue(element.paid().isEmpty(), "a drawn part owns no items");

        // the same ops as on a console: move the lamp, then remove it
        PanelElement lamp = design.elements().get(0);
        CompoundTag move = new CompoundTag();
        move.putString(PanelEditOps.KEY_OP, PanelEditOps.OP_MOVE);
        move.putUUID("Id", lamp.id());
        move.putFloat("X", 11);
        move.putFloat("Y", 11);
        assertTrue(PanelBlueprintItem.edit(blueprint, move, registries));
        assertEquals(11f, PanelBlueprintItem.design(blueprint, registries).elementById(lamp.id()).orElseThrow().x());

        // what belongs to a console alone is no edit on a drawing
        CompoundTag shape = new CompoundTag();
        shape.putString(PanelEditOps.KEY_OP, PanelEditOps.OP_SET_SHAPE);
        shape.putFloat("Angle", 30);
        assertFalse(PanelBlueprintItem.edit(blueprint, shape, registries));

        CompoundTag clear = new CompoundTag();
        clear.putString(PanelEditOps.KEY_OP, PanelEditOps.OP_CLEAR);
        assertTrue(PanelBlueprintItem.edit(blueprint, clear, registries));
        assertEquals(0, PanelBlueprintItem.partCount(blueprint));
        assertFalse(PanelBlueprintItem.edit(blueprint, clear, registries), "an empty drawing has nothing to clear");
    }
}
