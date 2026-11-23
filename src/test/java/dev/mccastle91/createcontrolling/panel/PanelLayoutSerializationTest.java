package dev.mccastle91.createcontrolling.panel;

import dev.mccastle91.createcontrolling.panel.element.ButtonElement;
import dev.mccastle91.createcontrolling.panel.element.CoveredSwitchElement;
import dev.mccastle91.createcontrolling.panel.element.KnobElement;
import dev.mccastle91.createcontrolling.panel.element.LabelElement;
import dev.mccastle91.createcontrolling.panel.element.LampElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElements;
import dev.mccastle91.createcontrolling.panel.element.PartSound;
import dev.mccastle91.createcontrolling.panel.element.SliderElement;
import dev.mccastle91.createcontrolling.panel.element.SwitchElement;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanelLayoutSerializationTest {

    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        PanelElements.init();
        registries = VanillaRegistries.createLookup();
    }

    private static PanelLayout fullLayout() {
        PanelLayout layout = new PanelLayout();

        ButtonElement button = PanelElements.BUTTON.create();
        button.setPos(1, 1);
        button.setPulseTicks(40);
        button.setColor(DyeColor.CYAN);
        button.setFrequency(FrequencyPair.of(new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.REDSTONE)));
        layout.addElement(button);

        SwitchElement toggle = PanelElements.TOGGLE_SWITCH.create();
        toggle.setPos(4.5f, 1);
        toggle.setOn(true);
        toggle.setColor(DyeColor.PURPLE);
        toggle.setFrequency(FrequencyPair.of(new ItemStack(Items.COMPASS), ItemStack.EMPTY));
        layout.addElement(toggle);

        KnobElement knob = PanelElements.KNOB.create();
        knob.setPos(8, 1);
        knob.setValue(11);
        layout.addElement(knob);

        CoveredSwitchElement covered = PanelElements.COVERED_SWITCH.create();
        covered.setPos(12, 1);
        covered.setGuardOpen(true);
        covered.setOn(true);
        layout.addElement(covered);

        LampElement lamp = PanelElements.LAMP.create();
        lamp.setPos(1, 6);
        lamp.setColor(DyeColor.RED);
        lamp.setFrequency(FrequencyPair.of(ItemStack.EMPTY, new ItemStack(Items.LAPIS_LAZULI)));
        layout.addElement(lamp);

        SliderElement slider = PanelElements.SLIDER_H.create();
        slider.setPos(4, 6);
        slider.setValue(3);
        layout.addElement(slider);

        layout.addElement(PanelElements.NIXIE.create());

        LabelElement label = PanelElements.LABEL.create();
        label.setPos(1, 12);
        label.setText("Kiln Master Switch");
        label.setColor(0xFFAA00);
        label.setScale(2);
        label.setLabelWidth(9.5f);
        label.setAlignment(LabelElement.Alignment.LEFT);
        layout.addElement(label);

        return layout;
    }

    @Test
    void roundTripPreservesEverything() {
        PanelLayout original = fullLayout();
        CompoundTag saved = original.save(registries);

        PanelLayout loaded = PanelLayout.load(saved, registries);
        CompoundTag savedAgain = loaded.save(registries);

        assertEquals(saved, savedAgain, "save -> load -> save must be lossless");
        assertEquals(8, loaded.elementCount());
    }

    @Test
    void typedFieldsSurviveRoundTrip() {
        PanelLayout loaded = PanelLayout.load(fullLayout().save(registries), registries);

        ButtonElement button = (ButtonElement) loaded.elements().get(0);
        assertEquals(40, button.pulseTicks());
        assertEquals(DyeColor.CYAN, button.color());
        assertTrue(button.frequency().first().is(Items.GOLD_INGOT));
        assertTrue(button.frequency().second().is(Items.REDSTONE));

        SwitchElement toggle = (SwitchElement) loaded.elements().get(1);
        assertTrue(toggle.isOn());
        assertEquals(4.5f, toggle.x(), "half-pixel positions survive");
        assertEquals(DyeColor.PURPLE, toggle.color());
        assertTrue(toggle.frequency().second().isEmpty());

        assertEquals(11, ((KnobElement) loaded.elements().get(2)).value());
        CoveredSwitchElement guarded = (CoveredSwitchElement) loaded.elements().get(3);
        assertTrue(guarded.isGuardOpen() && guarded.isOn(), "guard and lever state survive");

        LampElement lamp = (LampElement) loaded.elements().get(4);
        assertEquals(DyeColor.RED, lamp.color());
        assertTrue(lamp.frequency().second().is(Items.LAPIS_LAZULI));

        assertEquals(3, ((SliderElement) loaded.elements().get(5)).value());

        LabelElement label = (LabelElement) loaded.elements().get(7);
        assertEquals("Kiln Master Switch", label.text());
        assertEquals(0xFFAA00, label.color());
        assertEquals(2, label.scale());
        assertEquals(9.5f, label.width());
        assertEquals(LabelElement.Alignment.LEFT, label.alignment());
    }

    @Test
    void unknownElementTypesAreSkippedNotFatal() {
        CompoundTag saved = fullLayout().save(registries);
        CompoundTag bogus = new CompoundTag();
        bogus.putString(PanelElement.TAG_TYPE, "hologram_from_the_future");
        ListTag elements = saved.getList(PanelLayout.TAG_ELEMENTS, CompoundTag.TAG_COMPOUND);
        elements.add(bogus);
        saved.put(PanelLayout.TAG_ELEMENTS, elements);

        PanelLayout loaded = PanelLayout.load(saved, registries);
        assertEquals(8, loaded.elementCount(), "unknown type dropped, known ones kept");
    }

    @Test
    void v1LayoutsAreDroppedNotMisplaced() {
        CompoundTag saved = fullLayout().save(registries);
        saved.putInt(PanelLayout.TAG_VERSION, 1);
        assertTrue(PanelLayout.load(saved, registries).isEmpty());
    }

    @Test
    void elementCapIsEnforced() {
        int cap = dev.mccastle91.createcontrolling.config.CCConfig.maxElementsPerPanel;
        PanelLayout layout = new PanelLayout();
        for (int i = 0; i < cap; i++)
            assertTrue(layout.addElement(PanelElements.LAMP.create()));
        assertFalse(layout.addElement(PanelElements.LAMP.create()));
        assertEquals(cap, layout.elementCount());
    }

    @Test
    void placementIsClampedInsideTheFrame() {
        LabelElement label = PanelElements.LABEL.create(); // 6 x 1 px
        label.setPos(100, -5);
        assertEquals(PanelElement.AREA_MAX - label.width(), label.x());
        assertEquals(PanelElement.AREA_MIN, label.y());

        label.setLabelWidth(99);
        assertEquals(PanelElement.AREA_MAX - PanelElement.AREA_MIN, label.width());
        assertEquals(PanelElement.AREA_MIN, label.x(), "re-clamped after growing");
    }

    @Test
    void emptyFrequencyOmitsTagAndNormalizesCount() {
        assertTrue(FrequencyPair.of(ItemStack.EMPTY, ItemStack.EMPTY).isEmpty());

        ItemStack sixteen = new ItemStack(Items.BRICK, 16);
        FrequencyPair pair = FrequencyPair.of(sixteen, ItemStack.EMPTY);
        assertEquals(1, pair.first().getCount(), "frequency stacks are identities, count-1");

        FrequencyPair loaded = FrequencyPair.load(pair.save(registries), registries);
        assertTrue(loaded.first().is(Items.BRICK));
        assertTrue(loaded.second().isEmpty());
    }

    @Test
    void guardedSwitchWorksGuardAndLeverSeparately() {
        CoveredSwitchElement guarded = PanelElements.COVERED_SWITCH.create();
        int lever = CoveredSwitchElement.ZONE_LEVER, lid = CoveredSwitchElement.ZONE_LID;

        assertTrue(guarded.click(lever, 1, 1.5f, false), "first click lifts the guard");
        assertTrue(guarded.isGuardOpen());
        assertFalse(guarded.isOn(), "lifting the guard doesn't switch on");
        assertEquals(0, guarded.transmittedStrength());

        assertTrue(guarded.click(lever, 1, 1.5f, false));
        assertTrue(guarded.isOn());
        assertEquals(15, guarded.transmittedStrength());
        assertTrue(guarded.click(lever, 1, 1.5f, false));
        assertFalse(guarded.isOn(), "the lever switches off again with the guard still up");
        assertTrue(guarded.isGuardOpen());

        assertTrue(guarded.click(lid, 1, 0.2f, false), "a click on the raised lid closes it");
        assertFalse(guarded.isGuardOpen());
        assertFalse(guarded.isOn(), "closed without ever switching on");

        // sneak-click works the guard from anywhere; closing switches off
        assertTrue(guarded.click(lever, 1, 1.5f, true));
        assertTrue(guarded.isGuardOpen());
        assertTrue(guarded.click(lever, 1, 1.5f, false));
        assertTrue(guarded.isOn());
        assertTrue(guarded.click(lever, 1, 1.5f, true));
        assertFalse(guarded.isGuardOpen());
        assertFalse(guarded.isOn(), "closing the guard switches off");
        assertTrue(guarded.takesSneakClicks(), "sneak-click goes to the guard, not the designer");
    }

    @Test
    void guardedSwitchCanKeepItsStateUnderTheGuard() {
        CoveredSwitchElement guarded = PanelElements.COVERED_SWITCH.create();
        guarded.setKeepsState(true);
        guarded.click(CoveredSwitchElement.ZONE_LEVER, 1, 1.5f, false);
        guarded.click(CoveredSwitchElement.ZONE_LEVER, 1, 1.5f, false);
        assertTrue(guarded.click(CoveredSwitchElement.ZONE_LID, 1, 0.2f, false));
        assertFalse(guarded.isGuardOpen());
        assertTrue(guarded.isOn(), "locked on under the guard");
        assertEquals(15, guarded.transmittedStrength());

        CompoundTag saved = guarded.save(registries);
        CoveredSwitchElement loaded = PanelElements.COVERED_SWITCH.create();
        loaded.load(saved, registries);
        assertTrue(loaded.keepsState() && loaded.isOn() && !loaded.isGuardOpen(), "saved as it was");

        guarded.setKeepsState(false);
        assertFalse(guarded.isOn(), "no longer allowed to be on under a closed guard");
    }

    @Test
    void guardedSwitchLoadsItsOldThreeStateSaves() {
        CoveredSwitchElement template = PanelElements.COVERED_SWITCH.create();
        String[] states = { "CLOSED", "OPEN", "ARMED" };
        boolean[] open = { false, true, true };
        boolean[] on = { false, false, true };
        for (int i = 0; i < states.length; i++) {
            CompoundTag old = template.save(registries);
            old.remove(CoveredSwitchElement.TAG_GUARD_OPEN);
            old.remove(CoveredSwitchElement.TAG_ON);
            old.remove(CoveredSwitchElement.TAG_KEEPS_STATE);
            old.putString("State", states[i]);
            CoveredSwitchElement loaded = PanelElements.COVERED_SWITCH.create();
            loaded.load(old, registries);
            assertEquals(open[i], loaded.isGuardOpen(), states[i]);
            assertEquals(on[i], loaded.isOn(), states[i]);
        }
    }

    @Test
    void guardedSwitchObeysAutomation() {
        CoveredSwitchElement guarded = PanelElements.COVERED_SWITCH.create();
        assertTrue(guarded.operate(15));
        assertTrue(guarded.isGuardOpen() && guarded.isOn(), "switching on lifts the guard on the way");
        assertFalse(guarded.operate(7), "already on");
        assertTrue(guarded.operate(0));
        assertFalse(guarded.isGuardOpen() || guarded.isOn(), "switching off closes it");
    }

    @Test
    void partsSoundWhenWorkedAndOnlyThen() {
        ButtonElement button = PanelElements.BUTTON.create();
        button.setPulseTicks(2);
        assertEquals(null, button.takeSound(), "silent until worked");
        assertTrue(button.click(0, 0, false));
        PartSound down = button.takeSound();
        assertEquals(PartSound.Kind.BUTTON_ON, down.kind(), "going down");
        assertEquals(null, button.takeSound(), "each sound plays once");
        assertFalse(button.click(0, 0, false), "already down");
        assertEquals(null, button.takeSound(), "pressing a held button makes no new sound");
        assertFalse(button.tick());
        assertTrue(button.tick());
        assertEquals(PartSound.Kind.BUTTON_OFF, button.takeSound().kind(), "coming back up");

        ButtonElement large = PanelElements.LARGE_BUTTON.create();
        large.click(0, 0, false);
        assertTrue(large.takeSound().pitch() < down.pitch(), "the large button sits lower");

        SwitchElement toggle = PanelElements.TOGGLE_SWITCH.create();
        toggle.click(0, 0, false);
        PartSound on = toggle.takeSound();
        toggle.click(0, 0, false);
        PartSound off = toggle.takeSound();
        assertEquals(PartSound.Kind.LEVER, on.kind());
        assertTrue(on.pitch() > off.pitch(), "on clicks higher than off, like a lever");
        assertFalse(toggle.operate(0), "already off");
        assertEquals(null, toggle.takeSound(), "no change, no sound");

        CoveredSwitchElement guarded = PanelElements.COVERED_SWITCH.create();
        guarded.click(CoveredSwitchElement.ZONE_LEVER, 1, 1.5f, false);
        assertEquals(PartSound.Kind.LID_OPEN, guarded.takeSound().kind(), "the guard lifting");
        guarded.click(CoveredSwitchElement.ZONE_LEVER, 1, 1.5f, false);
        assertEquals(PartSound.Kind.LEVER, guarded.takeSound().kind(), "the lever under it");
        guarded.click(CoveredSwitchElement.ZONE_LID, 1, 0.2f, false);
        assertEquals(PartSound.Kind.LID_CLOSE, guarded.takeSound().kind(), "the guard closing");

        KnobElement knob = PanelElements.KNOB.create();
        knob.click(0, 0, false);
        PartSound low = knob.takeSound();
        knob.setValue(9);
        assertEquals(null, knob.takeSound(), "setting a value directly (loading, limits) is silent");
        assertTrue(knob.turnTo(10));
        assertTrue(knob.takeSound().pitch() > low.pitch(), "steps rise with the value");
        knob.setMax(10);
        assertFalse(knob.click(0, 0, false), "at its limit");
        assertEquals(null, knob.takeSound(), "a knob that can't move doesn't click");

        // a loaded part never plays the sound of whatever it was last doing
        toggle.click(0, 0, false);
        toggle.load(toggle.save(registries), registries);
        assertEquals(null, toggle.takeSound());
    }
}
