package dev.mccastle91.createcontrolling.panel;

import dev.mccastle91.createcontrolling.panel.element.BarMeterElement;
import dev.mccastle91.createcontrolling.panel.element.ButtonElement;
import dev.mccastle91.createcontrolling.panel.element.KnobElement;
import dev.mccastle91.createcontrolling.panel.element.LabelElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElements;
import dev.mccastle91.createcontrolling.registry.CCContent;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The designer edit-op pipeline, applied to a level-less block entity. */
class PanelEditOpsTest {

    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        PanelElements.init();
        registries = VanillaRegistries.createLookup();
    }

    /**
     * The BE type is only consulted for state validation and save ids —
     * borrowing a vanilla one (with its matching state) avoids needing the
     * package-private BlockEntitySupplier to build our own in a unit test.
     */
    @SuppressWarnings("unchecked")
    private static PanelBlockEntity newPanel() {
        BlockEntityType<? extends PanelBlockEntity> type =
                (BlockEntityType<? extends PanelBlockEntity>) (BlockEntityType<?>) BlockEntityType.FURNACE;
        return new PanelBlockEntity(type, BlockPos.ZERO, Blocks.FURNACE.defaultBlockState());
    }

    private static CompoundTag op(String kind, UUID id) {
        CompoundTag tag = new CompoundTag();
        tag.putString(PanelEditOps.KEY_OP, kind);
        if (id != null)
            tag.putUUID("Id", id);
        return tag;
    }

    private static CompoundTag add(String type, float x, float y) {
        CompoundTag tag = op(PanelEditOps.OP_ADD, null);
        tag.putString("Type", type);
        tag.putFloat("X", x);
        tag.putFloat("Y", y);
        return tag;
    }

    private static boolean apply(PanelBlockEntity panel, CompoundTag op) {
        return PanelEditOps.apply(panel, op, registries, CostPayer.FREE);
    }

    /** Pays redstone lamps from a fixed purse and records refunds. */
    static final class Purse implements CostPayer {
        int redstoneLamps;
        final List<ItemStack> refunds = new ArrayList<>();

        Purse(int redstoneLamps) {
            this.redstoneLamps = redstoneLamps;
        }

        @Override
        public List<ItemStack> tryPay(List<ComponentCosts.Cost> cost) {
            int need = 0;
            for (ComponentCosts.Cost line : cost) {
                if (!line.matches(new ItemStack(Items.REDSTONE_LAMP)))
                    return null;
                need += line.count();
            }
            if (need > redstoneLamps)
                return null;
            redstoneLamps -= need;
            return need == 0 ? List.of() : List.of(new ItemStack(Items.REDSTONE_LAMP, need));
        }

        @Override
        public void refund(List<ItemStack> items) {
            items.forEach(stack -> refunds.add(stack.copy()));
        }

        int refunded() {
            return refunds.stream().filter(s -> s.is(Items.REDSTONE_LAMP)).mapToInt(ItemStack::getCount).sum();
        }
    }

    @Test
    void addMoveDeleteOnTheHalfPixelGrid() {
        PanelBlockEntity panel = newPanel();

        assertTrue(apply(panel, add("button", 5.3f, 4.7f)));
        assertEquals(1, panel.getLayout().elementCount());
        PanelElement element = panel.getLayout().elements().get(0);
        assertEquals(5.5f, element.x(), "snapped to 0.5 px");
        assertEquals(4.5f, element.y());

        CompoundTag move = op(PanelEditOps.OP_MOVE, element.id());
        move.putFloat("X", 99);
        move.putFloat("Y", 0);
        assertTrue(apply(panel, move));
        assertEquals(PanelElement.AREA_MAX - element.width(), element.x(), "clamped inside the frame");
        assertEquals(PanelElement.AREA_MIN, element.y());

        assertTrue(apply(panel, op(PanelEditOps.OP_DELETE, element.id())));
        assertTrue(panel.getLayout().isEmpty());
    }

    @Test
    void mountingChargesTheCostAndRemovingRefundsIt() {
        PanelBlockEntity panel = newPanel();
        Purse purse = new Purse(3);

        // a large lamp costs two redstone lamps
        assertTrue(PanelEditOps.apply(panel, add("large_lamp", 2, 2), registries, purse));
        assertEquals(1, purse.redstoneLamps);
        assertFalse(PanelEditOps.apply(panel, add("large_lamp", 8, 8), registries, purse), "can't afford a second");
        assertEquals(1, panel.getLayout().elementCount(), "nothing mounted without payment");

        UUID id = panel.getLayout().elements().get(0).id();
        assertTrue(PanelEditOps.apply(panel, op(PanelEditOps.OP_DELETE, id), registries, purse));
        assertEquals(2, purse.refunded(), "exactly what was paid comes back");
    }

    @Test
    void partsMountedForFreeRefundNothing() {
        PanelBlockEntity panel = newPanel();
        assertTrue(apply(panel, add("large_lamp", 2, 2)));
        Purse purse = new Purse(0);
        UUID id = panel.getLayout().elements().get(0).id();
        assertTrue(PanelEditOps.apply(panel, op(PanelEditOps.OP_DELETE, id), registries, purse));
        assertTrue(purse.refunds.isEmpty(), "a creative-mounted part is no item source");
    }

    @Test
    void addIsNotChargedWhenThereIsNoRoom() {
        PanelBlockEntity panel = newPanel();
        // 4x4 lamps fill the 14x14 px area (or the element cap) within a dozen adds
        int adds = 0;
        while (apply(panel, add("large_lamp", 1, 1)))
            assertTrue(++adds < 20, "the face must fill up");
        int count = panel.getLayout().elementCount();
        Purse purse = new Purse(2);
        assertFalse(PanelEditOps.apply(panel, add("large_lamp", 1, 1), registries, purse));
        assertEquals(2, purse.redstoneLamps, "not charged when nothing was mounted");
        assertEquals(count, panel.getLayout().elementCount());
    }

    @Test
    void setFrequencyPerSlot() {
        PanelBlockEntity panel = newPanel();
        ButtonElement button = PanelElements.BUTTON.create();
        panel.getLayout().addElement(button);

        CompoundTag setA = op(PanelEditOps.OP_SET_FREQUENCY, button.id());
        setA.putInt("Slot", 0);
        setA.put("Item", new ItemStack(Items.GOLD_INGOT).save(registries));
        assertTrue(apply(panel, setA));
        assertTrue(button.frequency().first().is(Items.GOLD_INGOT));
        assertTrue(button.frequency().second().isEmpty());

        CompoundTag setB = op(PanelEditOps.OP_SET_FREQUENCY, button.id());
        setB.putInt("Slot", 1);
        setB.put("Item", new ItemStack(Items.REDSTONE).save(registries));
        assertTrue(apply(panel, setB));
        assertTrue(button.frequency().first().is(Items.GOLD_INGOT), "slot A preserved");
        assertTrue(button.frequency().second().is(Items.REDSTONE));

        CompoundTag clearA = op(PanelEditOps.OP_SET_FREQUENCY, button.id());
        clearA.putInt("Slot", 0);
        assertTrue(apply(panel, clearA));
        assertTrue(button.frequency().first().isEmpty());
        assertTrue(button.frequency().second().is(Items.REDSTONE));
    }

    @Test
    void optionsAreClampedAndTyped() {
        PanelBlockEntity panel = newPanel();
        ButtonElement button = PanelElements.BUTTON.create();
        LabelElement label = PanelElements.LABEL.create();
        panel.getLayout().addElement(button);
        panel.getLayout().addElement(label);

        CompoundTag pulse = op(PanelEditOps.OP_SET_OPTION, button.id());
        pulse.putString("Key", "pulse_ticks");
        pulse.putInt("Value", 9999);
        assertTrue(apply(panel, pulse));
        assertEquals(ButtonElement.MAX_PULSE_TICKS, button.pulseTicks());

        CompoundTag color = op(PanelEditOps.OP_SET_OPTION, button.id());
        color.putString("Key", "color");
        color.putString("Value", "lime");
        assertTrue(apply(panel, color));
        assertEquals(net.minecraft.world.item.DyeColor.LIME, button.color());

        CompoundTag text = op(PanelEditOps.OP_SET_OPTION, label.id());
        text.putString("Key", "text");
        text.putString("Value", "Boiler");
        assertTrue(apply(panel, text));
        assertEquals("Boiler", label.text());

        CompoundTag scale = op(PanelEditOps.OP_SET_OPTION, label.id());
        scale.putString("Key", "scale");
        scale.putInt("Value", 99);
        assertTrue(apply(panel, scale));
        assertEquals(LabelElement.MAX_SCALE, label.scale());
        assertEquals(LabelElement.MAX_SCALE * LabelElement.DEPTH_PER_SCALE, label.depth(), "depth follows scale");
    }

    @Test
    void knobAdjustClamps() {
        PanelBlockEntity panel = newPanel();
        KnobElement knob = PanelElements.KNOB.create();
        knob.setValue(14);
        panel.getLayout().addElement(knob);

        CompoundTag up = op(PanelEditOps.OP_ADJUST, knob.id());
        up.putInt("Delta", 1);
        assertTrue(apply(panel, up));
        assertEquals(15, knob.value());
        assertFalse(apply(panel, up), "already at max: no change");
        assertEquals(15, knob.value());

        CompoundTag down = op(PanelEditOps.OP_ADJUST, knob.id());
        down.putInt("Delta", -99);
        assertTrue(apply(panel, down));
        assertEquals(0, knob.value(), "clamped at min");
    }

    @Test
    void knobClickSetsTheValueAtThePointerAngle() {
        KnobElement knob = PanelElements.KNOB.create();
        float c = knob.width() / 2;
        assertEquals(0, knob.valueAt(c - 2, c + 2), "lower left is 0");
        assertEquals(15, knob.valueAt(c + 2, c + 2), "lower right is 15");
        assertEquals(8, knob.valueAt(c, c - 2), "straight up is the middle");
    }

    @Test
    void overlappingPlacementsAreRejected() {
        PanelBlockEntity panel = newPanel();
        ButtonElement first = PanelElements.BUTTON.create(); // 3x3
        first.setPos(1, 1);
        panel.getLayout().addElement(first);
        ButtonElement second = PanelElements.BUTTON.create();
        second.setPos(8, 8);
        panel.getLayout().addElement(second);

        CompoundTag move = op(PanelEditOps.OP_MOVE, second.id());
        move.putFloat("X", 2.5f);
        move.putFloat("Y", 1);
        assertFalse(apply(panel, move), "half a pixel of overlap still collides");
        assertEquals(8, second.x());

        move.putFloat("X", 4);
        assertTrue(apply(panel, move), "touching edges are fine");
        assertEquals(4, second.x());
    }

    @Test
    void labelsAreOverlapExempt() {
        PanelBlockEntity panel = newPanel();
        ButtonElement button = PanelElements.BUTTON.create();
        button.setPos(4, 4);
        panel.getLayout().addElement(button);
        LabelElement label = PanelElements.LABEL.create();
        panel.getLayout().addElement(label);

        CompoundTag move = op(PanelEditOps.OP_MOVE, label.id());
        move.putFloat("X", 4);
        move.putFloat("Y", 4);
        assertTrue(apply(panel, move), "labels may sit over controls");
        assertEquals(4, label.x());

        CompoundTag moveButton = op(PanelEditOps.OP_MOVE, button.id());
        moveButton.putFloat("X", 5);
        moveButton.putFloat("Y", 5);
        assertTrue(apply(panel, moveButton), "labels never block controls");
    }

    @Test
    void setShapeClampsToTheBlock() {
        PanelBlockEntity panel = newPanel();
        CompoundTag shape = op(PanelEditOps.OP_SET_SHAPE, null);
        shape.putFloat("Angle", 45);
        shape.putFloat("Front", 8);
        assertTrue(apply(panel, shape));
        assertEquals(45, panel.shape().angle(), 1e-4);
        assertEquals(0, panel.shape().frontHeight(), 1e-4, "45° rises a whole block: knife-edged front");

        shape.putFloat("Angle", 80);
        shape.putFloat("Front", 0);
        assertFalse(apply(panel, shape), "clamped to the same 45° shape: no change");
    }

    @Test
    void setShapeRaisesEitherWallEdgeButOnlyOneWayElsewhere() {
        PanelBlockEntity wall = panelOn(AttachFace.WALL);
        assertTrue(wall.shape().isFlat(), "wall consoles start flat");
        CompoundTag shape = op(PanelEditOps.OP_SET_SHAPE, null);
        shape.putFloat("Angle", 30);
        shape.putFloat("Front", 2);
        shape.putBoolean("Reversed", true);
        assertTrue(apply(wall, shape));
        assertEquals(30, wall.shape().angle(), 1e-4);
        assertTrue(wall.shape().reversed(), "bottom edge out");

        PanelBlockEntity floor = panelOn(AttachFace.FLOOR);
        assertTrue(apply(floor, shape));
        assertFalse(floor.shape().reversed(), "floor consoles slope one way only");

        PanelBlockEntity ceiling = panelOn(AttachFace.CEILING);
        shape.putBoolean("Reversed", false);
        assertTrue(apply(ceiling, shape));
        assertTrue(ceiling.shape().reversed(), "ceiling consoles keep their (model-space reversed) slope");
    }

    /** A real console block entity (the mod is loaded under FML) on the given face. */
    private static PanelBlockEntity panelOn(AttachFace face) {
        return new PanelBlockEntity(CCContent.CONTROL_PANEL_ENTITY.get(), BlockPos.ZERO,
                CCContent.CONTROL_PANEL.getDefaultState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE, face));
    }

    @Test
    void barMetersTakeTheirOwnLength() {
        PanelBlockEntity panel = newPanel();
        assertTrue(apply(panel, add("bar_meter", 1, 1)));
        PanelElement bar = panel.getLayout().elements().get(0);
        assertEquals(6, bar.width(), "starts at the default length");

        assertTrue(apply(panel, length(bar, 10)));
        assertEquals(10, bar.width());
        assertFalse(apply(panel, length(bar, 10)), "same length: no change");
        assertTrue(apply(panel, length(bar, 1)));
        assertEquals(BarMeterElement.MIN_LENGTH, bar.width(), "clamped to the minimum");

        // at the right edge, growing slides it left to stay on the face
        CompoundTag move = op(PanelEditOps.OP_MOVE, bar.id());
        move.putFloat("X", 12);
        move.putFloat("Y", 1);
        assertTrue(apply(panel, move));
        assertTrue(apply(panel, length(bar, 8)));
        assertEquals(PanelElement.AREA_MAX - 8, bar.x());

        // a neighbour in the way refuses the resize
        assertTrue(apply(panel, add("button", 1, 1)));
        assertFalse(apply(panel, length(bar, 14)), "would run into the button");
        assertEquals(8, bar.width());

        PanelLayout loaded = PanelLayout.load(panel.getLayout().save(registries), registries);
        assertEquals(8, loaded.elementById(bar.id()).orElseThrow().width(), "length is saved");
    }

    private static CompoundTag length(PanelElement element, float value) {
        CompoundTag tag = op(PanelEditOps.OP_SET_OPTION, element.id());
        tag.putString("Key", "length");
        tag.putFloat("Value", value);
        return tag;
    }

    @Test
    void unknownOpsAndIdsAreRejected() {
        PanelBlockEntity panel = newPanel();
        assertFalse(apply(panel, op("teleport_panel", null)));
        CompoundTag move = op(PanelEditOps.OP_MOVE, UUID.randomUUID());
        move.putFloat("X", 1);
        move.putFloat("Y", 1);
        assertFalse(apply(panel, move));
        assertFalse(apply(panel, add("hologram_from_the_future", 1, 1)));
    }
}
