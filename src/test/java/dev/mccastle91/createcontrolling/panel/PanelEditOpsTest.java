package dev.mccastle91.createcontrolling.panel;

import dev.mccastle91.createcontrolling.panel.element.BarMeterElement;
import dev.mccastle91.createcontrolling.panel.element.ButtonElement;
import dev.mccastle91.createcontrolling.panel.element.KnobElement;
import dev.mccastle91.createcontrolling.panel.element.LabelElement;
import dev.mccastle91.createcontrolling.panel.element.NixieStripElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElements;
import dev.mccastle91.createcontrolling.panel.element.ReadoutElement;
import dev.mccastle91.createcontrolling.registry.CCContent;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
    void knobClickStepsForwardAndSneakClickStepsBack() {
        KnobElement knob = PanelElements.KNOB.create();
        assertTrue(knob.takesSneakClicks());
        assertTrue(knob.click(0, 0, false));
        assertEquals(1, knob.value(), "click steps forward, wherever it lands");
        assertTrue(knob.click(0, 0, true));
        assertEquals(0, knob.value(), "sneak-click steps back");
        assertFalse(knob.click(0, 0, true), "already at min: no change");
        knob.setValue(15);
        assertFalse(knob.click(0, 0, false), "already at max: no change");
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

    private static CompoundTag readoutOption(PanelElement element, String key, int value) {
        CompoundTag tag = op(PanelEditOps.OP_SET_OPTION, element.id());
        tag.putString("Key", key);
        tag.putInt("Value", value);
        return tag;
    }

    private static CompoundTag line(PanelElement element, int row, int line) {
        CompoundTag tag = readoutOption(element, "line", line);
        tag.putInt("Row", row);
        return tag;
    }

    @Test
    void newReadoutsTakeTheirOwnLines() {
        PanelBlockEntity panel = newPanel();
        assertTrue(apply(panel, add("display_board", 1, 1)));
        assertTrue(apply(panel, add("nixie_strip", 1, 8)));
        ReadoutElement board = (ReadoutElement) panel.getLayout().elements().get(0);
        ReadoutElement strip = (ReadoutElement) panel.getLayout().elements().get(1);
        assertEquals(1, board.line(0));
        assertEquals(2, board.line(1));
        assertEquals(3, strip.line(0));
        assertEquals(3, panel.getLayout().lineCount());
    }

    @Test
    void readoutRowsChooseTheirLines() {
        PanelBlockEntity panel = newPanel();
        assertTrue(apply(panel, add("display_board", 1, 1)));
        assertTrue(apply(panel, add("nixie_strip", 1, 8)));
        ReadoutElement board = (ReadoutElement) panel.getLayout().elements().get(0);
        ReadoutElement strip = (ReadoutElement) panel.getLayout().elements().get(1);

        assertTrue(apply(panel, line(board, 1, 6)));
        assertEquals(6, board.line(1));
        assertEquals(6, panel.getLayout().lineCount(), "offers lines up to the highest shown");

        assertTrue(apply(panel, line(board, 1, ReadoutElement.NO_LINE)));
        assertEquals(ReadoutElement.NO_LINE, board.line(1), "a board row can be left off");
        assertTrue(apply(panel, line(strip, 0, ReadoutElement.NO_LINE)));
        assertEquals(1, strip.line(0), "a strip's only row can't be");

        assertTrue(apply(panel, line(strip, 0, 99)));
        assertEquals(ReadoutElement.MAX_LINES, strip.line(0));

        PanelLayout loaded = PanelLayout.load(panel.getLayout().save(registries), registries);
        assertEquals(ReadoutElement.NO_LINE, ((ReadoutElement) loaded.elementById(board.id()).orElseThrow()).line(1));
        assertEquals(ReadoutElement.MAX_LINES, ((ReadoutElement) loaded.elementById(strip.id()).orElseThrow()).line(0));
    }

    @Test
    void displayBoardsSpanTheFaceWithinTwoPixels() {
        PanelBlockEntity panel = newPanel();
        assertTrue(apply(panel, add("display_board", 2, 1)));
        ReadoutElement board = (ReadoutElement) panel.getLayout().elements().get(0);
        assertEquals(12, board.width(), "1 px short of the design area on each side");
        assertEquals(2, board.x());

        assertTrue(apply(panel, readoutOption(board, "size", 99)));
        assertEquals(14, board.width(), "up to the design area's full width");
        assertEquals(PanelElement.AREA_MIN, board.x(), "grows about its centre");
        assertTrue(apply(panel, readoutOption(board, "size", 1)));
        assertEquals(12, board.width());
        assertEquals(2, board.x(), "and shrinks back to even margins");
        assertEquals(2, board.rows());
    }

    @Test
    void nixieStripsAreSizedInTubes() {
        PanelBlockEntity panel = newPanel();
        assertTrue(apply(panel, add("nixie_strip", 1, 1)));
        assertTrue(apply(panel, add("button", 12, 1)));
        ReadoutElement strip = (ReadoutElement) panel.getLayout().elements().get(0);
        assertEquals(NixieStripElement.DEFAULT_TUBES, strip.size());
        assertEquals(10.5f, strip.width());

        assertFalse(apply(panel, readoutOption(strip, "size", 5)), "would run into the button");
        assertEquals(10.5f, strip.width());
        assertTrue(apply(panel, readoutOption(strip, "size", 2)));
        assertEquals(5.5f, strip.width());
    }

    @Test
    void nixieTubesFitTheirText() {
        assertEquals(" 230V", NixieStripElement.fit(" 229.871 V", 5), "decimals round away first");
        assertEquals("229.9", NixieStripElement.fit("229.87", 5));
        assertEquals("1.5kV", NixieStripElement.fit("1.5 kV", 5));
        assertEquals("  7", NixieStripElement.fit("7", 3), "short text sits right");
        assertEquals("1500", NixieStripElement.fit("1500 V", 4), "then the unit goes");
        assertEquals(" 1501V", NixieStripElement.fit("1,500.5 V", 6), "thousands commas aren't kept");
        assertEquals("----", NixieStripElement.fit("12345 V", 4), "never a wrong number");
        assertEquals("-3.3", NixieStripElement.fit("-3.25", 4));
        assertEquals(">200V", NixieStripElement.fit("> 200.4 V", 5));
        assertEquals("IronI", NixieStripElement.fit("Iron Ingot", 5), "words are cut off");
        assertEquals("12:30", NixieStripElement.fit("12:30:05", 5), "digits after the number: not a unit");
    }

    @Test
    void displayLinesSyncToClientsButAreNeverSaved() {
        PanelBlockEntity panel = newPanel();
        assertTrue(panel.setDisplayLine(2, Component.literal("230 V")));
        assertFalse(panel.setDisplayLine(2, Component.literal("230 V")), "same text: nothing to sync");

        CompoundTag update = panel.getUpdateTag(registries);
        assertTrue(update.contains(PanelBlockEntity.TAG_DISPLAY_LINES));
        assertFalse(panel.saveWithoutMetadata(registries).contains(PanelBlockEntity.TAG_DISPLAY_LINES));

        PanelBlockEntity client = newPanel();
        client.loadAdditional(update, registries);
        assertEquals("230 V", client.displayLine(2).getString());
        assertEquals("", client.displayLine(1).getString());

        assertTrue(panel.setDisplayLine(2, Component.empty()));
        assertFalse(panel.setDisplayLine(2, Component.empty()), "already blank");
        client.loadAdditional(panel.getUpdateTag(registries), registries);
        assertEquals("", client.displayLine(2).getString(), "a cleared line clears on clients too");
    }
}
