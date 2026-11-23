package dev.mccastle91.createcontrolling.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.mccastle91.createcontrolling.CreateControlling;
import dev.mccastle91.createcontrolling.compat.computercraft.ConsoleGroup;
import dev.mccastle91.createcontrolling.compat.create.ConsoleDisplayTarget;
import dev.mccastle91.createcontrolling.registry.CCContent;
import dev.mccastle91.createcontrolling.panel.ConsoleShape;
import dev.mccastle91.createcontrolling.panel.FrequencyPair;
import dev.mccastle91.createcontrolling.panel.PanelBlock;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.CostPayer;
import dev.mccastle91.createcontrolling.panel.PanelEditOps;
import dev.mccastle91.createcontrolling.panel.PanelFaceGeometry;
import dev.mccastle91.createcontrolling.panel.element.ButtonElement;
import dev.mccastle91.createcontrolling.panel.element.DisplayBoardElement;
import dev.mccastle91.createcontrolling.panel.element.KnobElement;
import dev.mccastle91.createcontrolling.panel.element.LampElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElements;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.ItemLike;

import java.util.List;

//? if neoforge {
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
//?} else {
/*import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
*///?}

/**
 * End-to-end proof that panel elements ride Create's Redstone Link network:
 * real Create link blocks on one side, our panel on the other, in a running
 * server. Run with: gradlew :1.21.1-neoforge:runGametest (or :1.20.1-forge:)
 *
 * GameTestHolder sets the test namespace (the enabledGameTestNamespaces
 * filter matches against it, NOT the template string) and enables automatic
 * discovery via annotation scan.
 */
@GameTestHolder(CreateControlling.MOD_ID)
@PrefixGameTestTemplate(false)
public class PanelLinkGameTests {

    private static final String EMPTY = "empty";
    private static final BlockPos PANEL_POS = new BlockPos(1, 1, 1);
    private static final BlockPos LINK_POS = new BlockPos(4, 1, 1);

    @GameTest(template = EMPTY)
    public void buttonPulsePowersCreateReceiver(GameTestHelper helper) {
        ButtonElement button = PanelElements.BUTTON.create();
        button.setPulseTicks(30);
        button.setFrequency(freq(Items.GOLD_INGOT, Items.REDSTONE));
        PanelBlockEntity panel = placePanelWith(helper, button);
        placeCreateLink(helper);

        helper.startSequence()
                // LinkBehaviour attaches on the BE's first tick; retry until it exists
                .thenWaitUntil(() -> setCreateLinkFrequency(helper, button.frequency()))
                .thenExecute(() -> makeCreateLinkReceiver(helper))
                .thenExecuteAfter(2, () -> {
                    if (!panel.clickElement(button.id(), 1.5f, 1.5f, false))
                        throw new GameTestAssertException("Button element not found on panel");
                })
                .thenWaitUntil(() -> assertLinkPowered(helper, true))
                .thenWaitUntil(() -> {
                    BlockState state = helper.getBlockState(LINK_POS);
                    if (!state.hasProperty(RedstoneLinkBlock.POWERED) || state.getValue(RedstoneLinkBlock.POWERED))
                        throw new GameTestAssertException("Pulse should have expired; createReceivedSignal="
                                + createLinkReceivedSignal(helper) + ", link state: " + state);
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY)
    public void knobAnalogStrengthReachesCreateReceiver(GameTestHelper helper) {
        KnobElement knob = PanelElements.KNOB.create();
        knob.setFrequency(freq(Items.LAPIS_LAZULI, Items.EMERALD));
        PanelBlockEntity panel = placePanelWith(helper, knob);
        placeCreateLink(helper);

        helper.startSequence()
                // LinkBehaviour attaches on the BE's first tick; retry until it exists
                .thenWaitUntil(() -> setCreateLinkFrequency(helper, knob.frequency()))
                .thenExecute(() -> makeCreateLinkReceiver(helper))
                .thenExecuteAfter(2, () -> {
                    CompoundTag op = new CompoundTag();
                    op.putString(PanelEditOps.KEY_OP, PanelEditOps.OP_ADJUST);
                    op.putUUID("Id", knob.id());
                    op.putInt("Delta", 11);
                    if (!PanelEditOps.apply(panel, op, helper.getLevel().registryAccess(), CostPayer.FREE))
                        throw new GameTestAssertException("Knob element not adjusted");
                })
                .thenWaitUntil(() -> {
                    int received = createLinkReceivedSignal(helper);
                    if (received != 11)
                        throw new GameTestAssertException("Expected Create receiver at analog 11, got " + received);
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY)
    public void createTransmitterDrivesPanelLampAnalog(GameTestHelper helper) {
        LampElement lamp = PanelElements.LAMP.create();
        lamp.setFrequency(freq(Items.DIAMOND, Items.COMPASS));
        placePanelWith(helper, lamp);
        placeCreateLink(helper);

        helper.startSequence()
                // LinkBehaviour attaches on the BE's first tick; retry until it exists
                .thenWaitUntil(() -> setCreateLinkFrequency(helper, lamp.frequency()))
                .thenExecuteAfter(2, () -> {
                    if (!(helper.getBlockEntity(LINK_POS) instanceof RedstoneLinkBlockEntity link))
                        throw new GameTestAssertException("Create link block entity missing");
                    link.transmit(7);
                })
                .thenWaitUntil(() -> {
                    if (lamp.displayStrength() != 7)
                        throw new GameTestAssertException(
                                "Expected lamp at analog 7, got " + lamp.displayStrength());
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY)
    public void playerClickOnSlopedFacePressesButton(GameTestHelper helper) {
        ButtonElement button = PanelElements.BUTTON.create(); // 3x3 px
        button.setPulseTicks(60);
        button.setPos(6, 6);
        button.setFrequency(freq(Items.IRON_INGOT, Items.SLIME_BALL));
        PanelBlockEntity panel = placePanelWith(helper, button);
        placeCreateLink(helper);

        helper.startSequence()
                .thenWaitUntil(() -> setCreateLinkFrequency(helper, button.frequency()))
                .thenExecute(() -> makeCreateLinkReceiver(helper))
                .thenExecuteAfter(2, () -> {
                    // A player standing south of the (15° sloped, north-facing)
                    // console looks at the button's centre; the click ray runs
                    // from the eye through the hit point, as in real play
                    BlockPos abs = helper.absolutePos(PANEL_POS);
                    Vec3 origin = Vec3.atLowerCornerOf(abs);
                    PanelFaceGeometry face = PanelFaceGeometry.of(panel.getBlockState(), panel.shape());
                    Vec3 target = origin.add(face.point(7.5, 7.5, button.height()));
                    var hitResult = new net.minecraft.world.phys.BlockHitResult(target, Direction.UP, abs, false);
                    //? if >=1.21 {
                    var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                    player.setPos(origin.x + 0.5, origin.y + 0.3, origin.z + 2.0);
                    var result = helper.getLevel().getBlockState(abs).useWithoutItem(helper.getLevel(), player, hitResult);
                    //?} else {
                    /*var player = helper.makeMockPlayer();
                    player.setPos(origin.x + 0.5, origin.y + 0.3, origin.z + 2.0);
                    var result = helper.getLevel().getBlockState(abs).use(helper.getLevel(), player,
                            net.minecraft.world.InteractionHand.MAIN_HAND, hitResult);
                    *///?}
                    if (!result.consumesAction())
                        throw new GameTestAssertException("Panel click was not consumed: " + result);
                })
                .thenWaitUntil(() -> assertLinkPowered(helper, true))
                .thenSucceed();
    }

    /**
     * A console item remembers its old console's slope (drops and pick-block
     * keep the design). Placed on a wall it must start flat — it used to
     * keep the slope, which a wall console then couldn't change — and the
     * sneak-wrench then tilts it top-out, then bottom-out.
     */
    @GameTest(template = EMPTY)
    public void wallConsoleFromSlopedItemStartsFlatAndTiltsBothWays(GameTestHelper helper) {
        PanelBlockEntity floor = placePanelWith(helper, PanelElements.BUTTON.create());
        floor.setShape(ConsoleShape.of(30, 2));
        floor.markLayoutChanged();
        ItemStack stack = new ItemStack(CCContent.CONTROL_PANEL.get());
        floor.writeToItem(stack);

        // a player facing south uses the item on the north side of a block
        BlockPos support = new BlockPos(4, 1, 4);
        BlockPos wallPos = support.north();
        helper.setBlock(support, Blocks.STONE.defaultBlockState());
        helper.setBlock(wallPos, Blocks.AIR.defaultBlockState()); // the test world may be solid here
        BlockPos absSupport = helper.absolutePos(support);
        //? if >=1.21 {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        //?} else {
        /*var player = helper.makeMockPlayer();
        *///?}
        player.setPos(absSupport.getX() + 0.5, absSupport.getY(), absSupport.getZ() - 1.5);
        player.setYRot(0);
        player.setXRot(0);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(absSupport).add(0, 0, -0.5),
                Direction.NORTH, absSupport, false);
        stack.useOn(new net.minecraft.world.item.context.UseOnContext(player,
                net.minecraft.world.InteractionHand.MAIN_HAND, hit));

        BlockState state = helper.getBlockState(wallPos);
        if (!(state.getBlock() instanceof PanelBlock block) || state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE) != AttachFace.WALL)
            throw new GameTestAssertException("Expected a wall console at " + wallPos + ", found " + state);
        if (!(helper.getBlockEntity(wallPos) instanceof PanelBlockEntity wall))
            throw new GameTestAssertException("Wall console block entity missing");
        if (!wall.shape().isFlat())
            throw new GameTestAssertException("Wall console kept the item's slope: " + wall.shape());
        if (wall.getLayout().elementCount() != 1)
            throw new GameTestAssertException("Wall console lost the item's design");

        BlockPos absWall = helper.absolutePos(wallPos);
        float[] angles = { 15, 30, 45, 15, 30, 45, 0 };
        for (int i = 0; i < angles.length; i++) {
            block.cycleShapePreset(helper.getBlockState(wallPos), helper.getLevel(), absWall);
            ConsoleShape shape = wall.shape();
            boolean bottomOut = i >= 3 && i < 6;
            if (Math.abs(shape.angle() - angles[i]) > 1e-3 || shape.reversed() != bottomOut)
                throw new GameTestAssertException("Wrench step " + (i + 1) + " gave " + shape);
        }
        helper.succeed();
    }

    /**
     * Sneak-wrenching is Create's own pick-up: the console leaves the world
     * and lands in the player's inventory with its design, like a broken one.
     */
    @GameTest(template = EMPTY)
    public void sneakWrenchPicksConsoleUpWithItsDesign(GameTestHelper helper) {
        PanelBlockEntity panel = placePanelWith(helper, PanelElements.KNOB.create());
        panel.setShape(ConsoleShape.of(30, 2));
        panel.markLayoutChanged();
        BlockPos abs = helper.absolutePos(PANEL_POS);
        //? if >=1.21 {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        //?} else {
        /*var player = helper.makeMockPlayer();
        *///?}
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, com.simibubi.create.AllItems.WRENCH.asStack());
        var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        var context = new net.minecraft.world.item.context.UseOnContext(player,
                net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        BlockState state = helper.getBlockState(PANEL_POS);
        if (!(state.getBlock() instanceof com.simibubi.create.content.equipment.wrench.IWrenchable wrenchable))
            throw new GameTestAssertException("Console is not wrenchable: " + state);

        // a plain wrench click only changes the slope
        wrenchable.onWrenched(state, context);
        if (!(helper.getBlockEntity(PANEL_POS) instanceof PanelBlockEntity still) || still.shape().angle() != 45)
            throw new GameTestAssertException("Wrench click should step 30° to 45° and leave the console in place");

        // what Create hands a survival player on a sneak-wrench: the block's own drops
        ItemStack picked = ItemStack.EMPTY;
        for (ItemStack drop : net.minecraft.world.level.block.Block.getDrops(helper.getBlockState(PANEL_POS),
                helper.getLevel(), abs, helper.getBlockEntity(PANEL_POS), player, player.getMainHandItem()))
            if (drop.is(CCContent.CONTROL_PANEL.get().asItem()))
                picked = drop;
        if (picked.isEmpty())
            throw new GameTestAssertException("A picked-up console should drop as a console item");

        wrenchable.onSneakWrenched(helper.getBlockState(PANEL_POS), context);
        if (helper.getBlockState(PANEL_POS).getBlock() instanceof PanelBlock)
            throw new GameTestAssertException("Sneak-wrench left the console in the world");
        //? if >=1.21 {
        // (1.20.1's mock player is always creative, and Create gives creative players nothing)
        if (!player.getInventory().hasAnyMatching(stack -> stack.is(CCContent.CONTROL_PANEL.get().asItem())))
            throw new GameTestAssertException("Sneak-wrench did not put the console in the inventory");
        //?}

        // placed again, the item brings the design back
        helper.setBlock(PANEL_POS, CCContent.CONTROL_PANEL.getDefaultState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR));
        net.minecraft.world.item.BlockItem.updateCustomBlockEntityTag(helper.getLevel(), player, abs, picked);
        if (!(helper.getBlockEntity(PANEL_POS) instanceof PanelBlockEntity restored)
                || restored.getLayout().elementCount() != 1
                || !(restored.getLayout().elements().get(0) instanceof KnobElement))
            throw new GameTestAssertException("Picked-up console lost its design");
        helper.succeed();
    }

    /**
     * Create's clipboard carries frequencies between real Redstone Links and
     * console components, both ways, in Create's own format.
     */
    @GameTest(template = EMPTY)
    public void clipboardCarriesFrequenciesToAndFromCreateLinks(GameTestHelper helper) {
        KnobElement knob = PanelElements.KNOB.create();
        knob.setFrequency(freq(Items.PRISMARINE_SHARD, Items.NAUTILUS_SHELL));
        LampElement lamp = PanelElements.LAMP.create();
        lamp.setPos(8, 8);
        PanelBlockEntity panel = placePanelWith(helper, knob);
        panel.getLayout().addElement(lamp);
        panel.markLayoutChanged();
        placeCreateLink(helper);
        var registries = helper.getLevel().registryAccess();

        helper.startSequence()
                // LinkBehaviour attaches on the BE's first tick; retry until it exists
                .thenWaitUntil(() -> setCreateLinkFrequency(helper, freq(Items.HONEYCOMB, Items.ECHO_SHARD)))
                .thenExecute(() -> {
                    LinkBehaviour link = BlockEntityBehaviour.get(helper.getLevel(), helper.absolutePos(LINK_POS),
                            LinkBehaviour.TYPE);

                    // a real link's frequency, copied by Create's own code, pastes onto the lamp
                    ItemStack fromLink = AllBlocks.CLIPBOARD.asStack();
                    CompoundTag copied = new CompoundTag();
                    //? if >=1.21 {
                    link.writeToClipboard(registries, copied, Direction.UP);
                    //?} else {
                    /*link.writeToClipboard(copied, Direction.UP);
                    *///?}
                    CompoundTag values = new CompoundTag();
                    values.put(link.getClipboardKey(), copied);
                    //? if >=1.21 {
                    fromLink.set(com.simibubi.create.AllDataComponents.CLIPBOARD_CONTENT,
                            com.simibubi.create.content.equipment.clipboard.ClipboardContent.EMPTY.setCopiedValues(values));
                    //?} else {
                    /*fromLink.getOrCreateTag().put("CopiedValues", values);
                    *///?}
                    if (!dev.mccastle91.createcontrolling.compat.create.ClipboardFrequencies.paste(fromLink, lamp, registries))
                        throw new GameTestAssertException("Lamp did not take the link's frequency");
                    if (!lamp.frequency().first().is(Items.HONEYCOMB) || !lamp.frequency().second().is(Items.ECHO_SHARD))
                        throw new GameTestAssertException("Lamp got the wrong frequency: "
                                + lamp.frequency().first() + " / " + lamp.frequency().second());

                    // the knob's frequency, copied by us, pastes onto the real link through Create's own code
                    ItemStack fromKnob = AllBlocks.CLIPBOARD.asStack();
                    dev.mccastle91.createcontrolling.compat.create.ClipboardFrequencies.copy(fromKnob, knob, registries);
                    CompoundTag ours = dev.mccastle91.createcontrolling.compat.create.ClipboardFrequencies
                            .copiedValues(fromKnob);
                    if (ours == null || !ours.contains(link.getClipboardKey()))
                        throw new GameTestAssertException("Our copy is not under Create's link key: " + ours);
                    //? if >=1.21 {
                    boolean pasted = link.readFromClipboard(registries, ours.getCompound(link.getClipboardKey()),
                            null, Direction.UP, false);
                    //?} else {
                    /*boolean pasted = link.readFromClipboard(ours.getCompound(link.getClipboardKey()),
                            null, Direction.UP, false);
                    *///?}
                    if (!pasted)
                        throw new GameTestAssertException("Create's link refused the frequency we copied");
                    var key = link.getNetworkKey();
                    if (!key.getFirst().getStack().is(Items.PRISMARINE_SHARD)
                            || !key.getSecond().getStack().is(Items.NAUTILUS_SHELL))
                        throw new GameTestAssertException("Link got the wrong frequency from our copy");
                })
                .thenSucceed();
    }

    // --- helpers -----------------------------------------------------------

    /** Real Display Links writing through the target Create finds for a console. */
    @GameTest(template = EMPTY)
    public void displayLinksFillConsoleLines(GameTestHelper helper) {
        DisplayBoardElement board = PanelElements.DISPLAY_BOARD.create();
        board.setLine(0, 1);
        board.setLine(1, 2);
        PanelBlockEntity panel = placePanelWith(helper, board);
        DisplayLinkContext first = displayLinkContext(helper, LINK_POS);
        DisplayLinkContext second = displayLinkContext(helper, LINK_POS.east());
        BlockPos target = helper.absolutePos(PANEL_POS);

        DisplayTarget display = DisplayTarget.get(helper.getLevel(), target);
        if (!(display instanceof ConsoleDisplayTarget))
            throw new GameTestAssertException("Consoles should be display targets, found " + display);
        if (display.provideStats(first).maxRows() != 2)
            throw new GameTestAssertException("Board shows two lines, stats say " + display.provideStats(first));

        display.acceptText(0, text("230 V", "Grid OK", "no third line"), first);
        assertDisplayLine(panel, 1, "230 V");
        assertDisplayLine(panel, 2, "Grid OK");
        assertDisplayLine(panel, 3, "");

        display.acceptText(0, text("231 V"), first);
        assertDisplayLine(panel, 1, "231 V");
        assertDisplayLine(panel, 2, "");

        display.acceptText(1, text("Mine"), second);
        display.acceptText(0, text("232 V", "Pushed in"), first);
        assertDisplayLine(panel, 1, "232 V");
        assertDisplayLine(panel, 2, "Mine");
        helper.succeed();
    }

    private static DisplayLinkContext displayLinkContext(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, AllBlocks.DISPLAY_LINK.getDefaultState());
        if (!(helper.getBlockEntity(pos) instanceof DisplayLinkBlockEntity link))
            throw new GameTestAssertException("Display link block entity missing");
        link.target(helper.absolutePos(PANEL_POS));
        return new DisplayLinkContext(helper.getLevel(), link);
    }

    private static List<MutableComponent> text(String... lines) {
        return java.util.Arrays.stream(lines).map(Component::literal).toList();
    }

    private static void assertDisplayLine(PanelBlockEntity panel, int line, String expected) {
        String shown = panel.displayLine(line).getString();
        if (!shown.equals(expected))
            throw new GameTestAssertException("Line " + line + " should show '" + expected + "', shows '" + shown + "'");
    }

    /**
     * Each test uses a distinct pair: tests run in parallel in one world and
     * Create's link range (256) spans all test structures — a shared
     * frequency leaks the strongest transmitter into every other test.
     */
    /** What a computer does through the peripheral: write to a lamp, see the change, let go. */
    @GameTest(template = EMPTY)
    public void automationDrivesLampFrequencyAndReportsIt(GameTestHelper helper) {
        LampElement lamp = PanelElements.LAMP.create();
        lamp.setFrequency(freq(Items.QUARTZ, Items.CLOCK));
        PanelBlockEntity panel = placePanelWith(helper, lamp);
        placeCreateLink(helper);
        int[] reported = {-1};
        PanelBlockEntity.SignalListener listener = (console, element, strength) -> {
            if (console == panel && element == lamp)
                reported[0] = strength;
        };
        PanelBlockEntity.addSignalListener(listener);

        helper.startSequence()
                .thenWaitUntil(() -> setCreateLinkFrequency(helper, lamp.frequency()))
                .thenExecute(() -> makeCreateLinkReceiver(helper))
                .thenExecuteAfter(2, () -> {
                    if (!panel.setSignal(lamp.id(), 9))
                        throw new GameTestAssertException("Lamp did not accept a signal");
                })
                .thenWaitUntil(() -> {
                    int received = createLinkReceivedSignal(helper);
                    if (lamp.displayStrength() != 9 || received != 9 || reported[0] != 9)
                        throw new GameTestAssertException("Expected 9 everywhere, got lamp=" + lamp.displayStrength()
                                + " createLink=" + received + " reported=" + reported[0]);
                })
                .thenExecute(panel::clearDrivenSignals)
                .thenWaitUntil(() -> {
                    int received = createLinkReceivedSignal(helper);
                    if (lamp.displayStrength() != 0 || received != 0 || reported[0] != 0)
                        throw new GameTestAssertException("Expected 0 after letting go, got lamp="
                                + lamp.displayStrength() + " createLink=" + received + " reported=" + reported[0]);
                })
                .thenExecute(() -> PanelBlockEntity.removeSignalListener(listener))
                .thenSucceed();
    }

    /**
     * Issue #1: a computer works components that have no frequency. Controls
     * operate as ever; an unlinked lamp shows what the computer sets, sends
     * nothing anywhere, and goes dark when the computer lets go.
     */
    @GameTest(template = EMPTY)
    public void automationWorksUnlinkedComponents(GameTestHelper helper) {
        LampElement lamp = PanelElements.LAMP.create();
        PanelBlockEntity panel = placePanelWith(helper, lamp);
        dev.mccastle91.createcontrolling.panel.element.SwitchElement toggle = PanelElements.TOGGLE_SWITCH.create();
        toggle.setPos(9, 9);
        panel.getLayout().addElement(toggle);
        panel.markLayoutChanged();
        int[] reported = {-1};
        PanelBlockEntity.SignalListener listener = (console, element, strength) -> {
            if (console == panel && element == lamp)
                reported[0] = strength;
        };
        PanelBlockEntity.addSignalListener(listener);
        try {
            if (!panel.setSignal(lamp.id(), 11) || lamp.displayStrength() != 11 || reported[0] != 11)
                throw new GameTestAssertException("Unlinked lamp should show 11, shows " + lamp.displayStrength()
                        + ", reported " + reported[0]);
            if (!panel.setSignal(toggle.id(), 15) || !toggle.isOn() || toggle.transmittedStrength() != 15)
                throw new GameTestAssertException("Unlinked switch should turn on");
            panel.clearDrivenSignals();
            if (lamp.displayStrength() != 0 || reported[0] != 0)
                throw new GameTestAssertException("Unlinked lamp should go dark when the computer lets go");
            if (!toggle.isOn())
                throw new GameTestAssertException("A switch stays where it was put, like one flipped by hand");

            // linking a lamp the computer lit hands it to the network, and unlinking it again clears it
            panel.setSignal(lamp.id(), 5);
            lamp.setFrequency(freq(Items.CAKE, Items.BONE));
            panel.markLayoutChanged();
            lamp.setFrequency(FrequencyPair.EMPTY);
            panel.markLayoutChanged();
            if (lamp.displayStrength() != 0)
                throw new GameTestAssertException("A just-unlinked lamp should not keep an old value, shows "
                        + lamp.displayStrength());
        } finally {
            PanelBlockEntity.removeSignalListener(listener);
        }
        helper.succeed();
    }

    /**
     * A computer touching one console reaches every console joined to it and
     * tells them apart by name or position (the peripheral's selection rules,
     * which need no CC: Tweaked to test).
     */
    @GameTest(template = EMPTY)
    public void joinedConsolesAreFoundByNameAndPosition(GameTestHelper helper) {
        PanelBlockEntity first = placePanelWith(helper, PanelElements.LAMP.create());
        BlockPos secondPos = PANEL_POS.east();
        BlockPos loosePos = PANEL_POS.east(3);
        PanelBlockEntity second = placeConsole(helper, secondPos);
        second.getLayout().addElement(PanelElements.KNOB.create());
        second.markLayoutChanged();
        PanelBlockEntity loose = placeConsole(helper, loosePos);
        PanelBlock.refreshConnections(helper.getLevel(), helper.absolutePos(secondPos));
        PanelBlock.refreshConnections(helper.getLevel(), helper.absolutePos(loosePos));

        List<PanelBlockEntity> group = ConsoleGroup.of(first);
        if (group.size() != 2 || group.get(0) != first || !group.contains(second))
            throw new GameTestAssertException("Expected the two joined consoles, touched one first; got " + group.size());
        if (ConsoleGroup.of(second).size() != 2 || ConsoleGroup.of(loose).size() != 1)
            throw new GameTestAssertException("A console standing apart must not join the group");

        if (!PanelEditOps.apply(second, nameOp("  Reactor§c  "), helper.getLevel().registryAccess(), CostPayer.FREE)
                || !second.name().equals("Reactor"))
            throw new GameTestAssertException("Name should be set and cleaned, got '" + second.name() + "'");
        if (ConsoleGroup.select(first, null) != first)
            throw new GameTestAssertException("No console argument means the touched console");
        if (ConsoleGroup.select(first, "Reactor") != second)
            throw new GameTestAssertException("The name should find the second console");
        BlockPos abs = helper.absolutePos(secondPos);
        java.util.Map<String, Object> table = new java.util.HashMap<>();
        table.put("x", (double) abs.getX());
        table.put("y", (double) abs.getY());
        table.put("z", (double) abs.getZ());
        if (ConsoleGroup.select(first, table) != second)
            throw new GameTestAssertException("A position table should find the second console");
        if (ConsoleGroup.select(first, ConsoleGroup.describe(second)) != second)
            throw new GameTestAssertException("A console's own description should select it");
        assertRefused(() -> ConsoleGroup.select(first, "Nowhere"), "an unknown name");
        BlockPos looseAbs = helper.absolutePos(loosePos);
        assertRefused(() -> ConsoleGroup.select(first, java.util.Map.of("x", (double) looseAbs.getX(),
                "y", (double) looseAbs.getY(), "z", (double) looseAbs.getZ())), "a console outside the group");
        first.setName("Reactor");
        assertRefused(() -> ConsoleGroup.select(first, "Reactor"), "a name two consoles share");

        // a computer sets frequencies as the designer does
        PanelElement knob = ConsoleGroup.component(second, 1);
        knob.setFrequency(ConsoleGroup.frequency("minecraft:redstone", null));
        if (!knob.frequency().first().is(Items.REDSTONE) || !knob.frequency().second().isEmpty())
            throw new GameTestAssertException("Frequency should be redstone + blank");
        assertRefused(() -> ConsoleGroup.frequency("minecraft:not_an_item", null), "an unknown item");
        assertRefused(() -> ConsoleGroup.frequency("minecraft:air", null), "air");
        if (!ConsoleGroup.frequency(null, null).isEmpty())
            throw new GameTestAssertException("Two blank slots should unlink");
        helper.succeed();
    }

    private static CompoundTag nameOp(String name) {
        CompoundTag op = new CompoundTag();
        op.putString(PanelEditOps.KEY_OP, PanelEditOps.OP_SET_NAME);
        op.putString("Name", name);
        return op;
    }

    private static void assertRefused(Runnable action, String what) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new GameTestAssertException("Should have refused " + what);
    }

    @GameTest(template = EMPTY)
    public void automationOperatesControls(GameTestHelper helper) {
        KnobElement knob = PanelElements.KNOB.create();
        knob.setFrequency(freq(Items.AMETHYST_SHARD, Items.FEATHER));
        PanelBlockEntity panel = placePanelWith(helper, knob);
        placeCreateLink(helper);

        helper.startSequence()
                .thenWaitUntil(() -> setCreateLinkFrequency(helper, knob.frequency()))
                .thenExecute(() -> makeCreateLinkReceiver(helper))
                .thenExecuteAfter(2, () -> {
                    if (!panel.setSignal(knob.id(), 6))
                        throw new GameTestAssertException("Knob did not accept a signal");
                })
                .thenWaitUntil(() -> {
                    int received = createLinkReceivedSignal(helper);
                    if (knob.value() != 6 || received != 6)
                        throw new GameTestAssertException("Expected knob and Create receiver at 6, got knob="
                                + knob.value() + " createLink=" + received);
                })
                .thenSucceed();
    }

    /**
     * The copycat console: a block applied to it becomes its look and is kept
     * to hand back; the same block again turns it, another swaps it, and the
     * designer's op takes it off. An ordinary console takes no block.
     */
    @GameTest(template = EMPTY)
    public void copycatConsoleCopiesBlocks(GameTestHelper helper) {
        floor(helper);
        PanelBlockEntity panel = placeCopycat(helper, PANEL_POS);
        if (panel.casing() != dev.mccastle91.createcontrolling.panel.ConsoleCasing.COPYCAT || panel.material() != null)
            throw new GameTestAssertException("A new copycat console should be bare, in its own casing");
        //? if >=1.21 {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        //?} else {
        /*var player = helper.makeMockPlayer();
        *///?}

        use(helper, player, PANEL_POS, new ItemStack(Items.OAK_LOG, 2));
        BlockState log = panel.material();
        if (log == null || !log.is(Blocks.OAK_LOG) || !panel.materialItem().is(Items.OAK_LOG))
            throw new GameTestAssertException("Copycat console did not take the log: " + log);
        //? if >=1.21 {
        if (player.getMainHandItem().getCount() != 1)
            throw new GameTestAssertException("Applying a block should use one up");
        //?}
        use(helper, player, PANEL_POS, player.getMainHandItem());
        if (panel.material() == log || !panel.material().is(Blocks.OAK_LOG))
            throw new GameTestAssertException("The same block again should turn the log");

        use(helper, player, PANEL_POS, new ItemStack(Items.STONE));
        if (!panel.material().is(Blocks.STONE))
            throw new GameTestAssertException("Another block should replace the log");
        //? if >=1.21 {
        if (!player.getInventory().hasAnyMatching(stack -> stack.is(Items.OAK_LOG)))
            throw new GameTestAssertException("The replaced log should come back");
        //?}

        // things that aren't whole blocks are refused, as Create's copycats refuse them
        use(helper, player, PANEL_POS, new ItemStack(Items.OAK_STAIRS));
        use(helper, player, PANEL_POS, new ItemStack(Items.CHEST));
        if (!panel.material().is(Blocks.STONE))
            throw new GameTestAssertException("Stairs or a chest should not be copied");

        // an ordinary console copies nothing
        BlockPos plainPos = PANEL_POS.offset(0, 0, 3);
        PanelBlockEntity plain = placeConsole(helper, plainPos);
        use(helper, player, plainPos, new ItemStack(Items.STONE));
        if (plain.material() != null)
            throw new GameTestAssertException("An ordinary console took a block");

        // the block travels with the console item
        BlockPos abs = helper.absolutePos(PANEL_POS);
        ItemStack dropped = ItemStack.EMPTY;
        for (ItemStack drop : net.minecraft.world.level.block.Block.getDrops(helper.getBlockState(PANEL_POS),
                helper.getLevel(), abs, helper.getBlockEntity(PANEL_POS)))
            if (drop.is(CCContent.COPYCAT_CONTROL_PANEL.get().asItem()))
                dropped = drop;
        if (dropped.isEmpty())
            throw new GameTestAssertException("A copycat console should drop as its own item");
        helper.setBlock(PANEL_POS, Blocks.AIR.defaultBlockState());
        PanelBlockEntity restored = placeCopycat(helper, PANEL_POS);
        net.minecraft.world.item.BlockItem.updateCustomBlockEntityTag(helper.getLevel(), player, abs, dropped);
        if (restored.material() == null || !restored.material().is(Blocks.STONE)
                || !restored.materialItem().is(Items.STONE))
            throw new GameTestAssertException("The console item lost its copied block");

        // the designer takes it off and hands it back
        CompoundTag clear = new CompoundTag();
        clear.putString(PanelEditOps.KEY_OP, PanelEditOps.OP_CLEAR_MATERIAL);
        player.getInventory().clearContent();
        if (!PanelEditOps.apply(restored, clear, helper.getLevel().registryAccess(), CostPayer.of(player))
                || restored.material() != null)
            throw new GameTestAssertException("Taking the block off left it on");
        //? if >=1.21 {
        if (!player.getInventory().hasAnyMatching(stack -> stack.is(Items.STONE)))
            throw new GameTestAssertException("Taking the block off should hand it back");
        //?}
        if (PanelEditOps.apply(restored, clear, helper.getLevel().registryAccess(), CostPayer.of(player)))
            throw new GameTestAssertException("A bare console has no block to take off");
        helper.succeed();
    }

    /**
     * A copycat console gives off the light of the block it copies, and to
     * another console's connected textures it counts as that block: to any
     * other block it stays a console.
     */
    @GameTest(template = EMPTY)
    public void copycatConsoleGlowsAndConnectsAsItsBlock(GameTestHelper helper) {
        floor(helper);
        PanelBlockEntity panel = placeCopycat(helper, PANEL_POS);
        BlockPos abs = helper.absolutePos(PANEL_POS);
        //? if >=1.21 {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        //?} else {
        /*var player = helper.makeMockPlayer();
        *///?}
        if (helper.getBlockState(PANEL_POS).getLightEmission(helper.getLevel(), abs) != 0)
            throw new GameTestAssertException("A bare copycat console should give off no light");

        use(helper, player, PANEL_POS, new ItemStack(Items.GLOWSTONE));
        if (panel.material() == null || !panel.material().is(Blocks.GLOWSTONE))
            throw new GameTestAssertException("Copycat console did not take the glowstone: " + panel.material());
        int light = helper.getBlockState(PANEL_POS).getLightEmission(helper.getLevel(), abs);
        if (light != 15)
            throw new GameTestAssertException("A console copying glowstone should shine like it, got light " + light);

        BlockState console = helper.getBlockState(PANEL_POS);
        BlockState toConsole = console.getAppearance(helper.getLevel(), abs, Direction.UP, console, abs.east());
        if (!toConsole.is(Blocks.GLOWSTONE))
            throw new GameTestAssertException("To another console it should count as the block it copies, got " + toConsole);
        BlockState toBlock = console.getAppearance(helper.getLevel(), abs, Direction.UP,
                Blocks.GLOWSTONE.defaultBlockState(), abs.east());
        if (toBlock != console)
            throw new GameTestAssertException("To a real block it should stay a console, got " + toBlock);

        use(helper, player, PANEL_POS, new ItemStack(Items.STONE));
        light = helper.getBlockState(PANEL_POS).getLightEmission(helper.getLevel(), abs);
        if (light != 0)
            throw new GameTestAssertException("Swapping the glowstone for stone should put the light out, got " + light);

        // joined consoles answer "joined toward" for each other, and for nothing else
        BlockPos eastPos = PANEL_POS.east();
        placeCopycat(helper, eastPos);
        use(helper, player, eastPos, new ItemStack(Items.STONE));
        if (!(helper.getBlockState(PANEL_POS).getBlock() instanceof PanelBlock block))
            throw new GameTestAssertException("Console missing");
        BlockState joined = helper.getBlockState(PANEL_POS);
        if (!block.joinedToward(joined, helper.getLevel(), abs, abs.east()))
            throw new GameTestAssertException("Two consoles copying the same block side by side should be joined");
        if (block.joinedToward(joined, helper.getLevel(), abs, abs.west())
                || block.joinedToward(joined, helper.getLevel(), abs, abs.above())
                || block.joinedToward(joined, helper.getLevel(), abs, abs.east(2)))
            throw new GameTestAssertException("A console is joined only to consoles beside it");
        use(helper, player, eastPos, new ItemStack(Items.OAK_PLANKS));
        if (block.joinedToward(helper.getBlockState(PANEL_POS), helper.getLevel(), abs, abs.east()))
            throw new GameTestAssertException("Consoles copying different blocks should not be joined");
        helper.succeed();
    }

    /**
     * A dye paints the face plate: one dye is used up, the same dye again
     * changes and takes nothing, gray puts the standard plate back, and the
     * colour travels with the console item. Both kinds of console take it.
     */
    @GameTest(template = EMPTY)
    public void dyePaintsTheFacePlate(GameTestHelper helper) {
        floor(helper);
        PanelBlockEntity panel = placeConsole(helper, PANEL_POS);
        panel.getLayout().addElement(PanelElements.BUTTON.create());
        panel.markLayoutChanged();
        if (panel.plateColor() != null)
            throw new GameTestAssertException("A new console should have the standard plate");
        //? if >=1.21 {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        //?} else {
        /*var player = helper.makeMockPlayer();
        *///?}
        player.getAbilities().instabuild = false;

        use(helper, player, PANEL_POS, new ItemStack(Items.RED_DYE, 3));
        if (panel.plateColor() != net.minecraft.world.item.DyeColor.RED)
            throw new GameTestAssertException("Red dye should paint the plate red, got " + panel.plateColor());
        if (player.getMainHandItem().getCount() != 2)
            throw new GameTestAssertException("Painting should use one dye up, left " + player.getMainHandItem().getCount());
        use(helper, player, PANEL_POS, player.getMainHandItem());
        if (player.getMainHandItem().getCount() != 2)
            throw new GameTestAssertException("The colour the plate already has should cost nothing");
        if (panel.getLayout().elementCount() != 1)
            throw new GameTestAssertException("Painting the plate must leave the parts alone");

        // the colour is saved, and travels with the console item
        BlockPos abs = helper.absolutePos(PANEL_POS);
        ItemStack dropped = ItemStack.EMPTY;
        for (ItemStack drop : net.minecraft.world.level.block.Block.getDrops(helper.getBlockState(PANEL_POS),
                helper.getLevel(), abs, helper.getBlockEntity(PANEL_POS)))
            if (drop.is(CCContent.CONTROL_PANEL.get().asItem()))
                dropped = drop;
        helper.setBlock(PANEL_POS, Blocks.AIR.defaultBlockState());
        PanelBlockEntity restored = placeConsole(helper, PANEL_POS);
        net.minecraft.world.item.BlockItem.updateCustomBlockEntityTag(helper.getLevel(), player, abs, dropped);
        if (restored.plateColor() != net.minecraft.world.item.DyeColor.RED)
            throw new GameTestAssertException("The console item lost its plate colour: " + restored.plateColor());

        // gray is the standard plate
        use(helper, player, PANEL_POS, new ItemStack(Items.GRAY_DYE));
        if (restored.plateColor() != null)
            throw new GameTestAssertException("Gray dye should put the standard plate back");
        ItemStack gray = new ItemStack(Items.GRAY_DYE);
        use(helper, player, PANEL_POS, gray);
        if (gray.getCount() != 1)
            throw new GameTestAssertException("Gray dye on the standard plate should cost nothing");

        // a copycat console's plate is painted the same way
        BlockPos copycatPos = PANEL_POS.offset(0, 0, 3);
        PanelBlockEntity copycat = placeCopycat(helper, copycatPos);
        use(helper, player, copycatPos, new ItemStack(Items.LIME_DYE));
        if (copycat.plateColor() != net.minecraft.world.item.DyeColor.LIME || copycat.material() != null)
            throw new GameTestAssertException("A copycat console should take a dye on its plate, not as its block");
        helper.succeed();
    }

    /** Copycat consoles join each other only while they copy the same block, and never an ordinary console. */
    @GameTest(template = EMPTY)
    public void copycatConsolesJoinBySameBlock(GameTestHelper helper) {
        floor(helper);
        BlockPos next = PANEL_POS.east();
        PanelBlockEntity a = placeCopycat(helper, PANEL_POS);
        PanelBlockEntity b = placeCopycat(helper, next);
        placeConsole(helper, PANEL_POS.west());
        assertJoined(helper, PANEL_POS, 1, "two bare copycat consoles, but not the ordinary one");

        a.setMaterial(Blocks.STONE.defaultBlockState(), new ItemStack(Items.STONE));
        assertJoined(helper, PANEL_POS, 0, "stone beside bare");
        b.setMaterial(Blocks.STONE.defaultBlockState(), new ItemStack(Items.STONE));
        assertJoined(helper, PANEL_POS, 1, "stone beside stone");
        b.setMaterial(Blocks.DIRT.defaultBlockState(), new ItemStack(Items.DIRT));
        assertJoined(helper, PANEL_POS, 0, "stone beside dirt");
        helper.succeed();
    }

    private static void assertJoined(GameTestHelper helper, BlockPos pos, int expected, String what) {
        BlockState state = helper.getBlockState(pos);
        int joined = 0;
        for (var edge : List.of(PanelBlock.CONNECT_N, PanelBlock.CONNECT_S, PanelBlock.CONNECT_W, PanelBlock.CONNECT_E))
            if (state.getValue(edge))
                joined++;
        if (joined != expected)
            throw new GameTestAssertException("Expected " + expected + " joined edge(s) for " + what + ", got " + joined);
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 7; x++)
            for (int z = 0; z < 7; z++)
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
    }

    private static PanelBlockEntity placeCopycat(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, CCContent.COPYCAT_CONTROL_PANEL.getDefaultState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR));
        if (!(helper.getBlockEntity(pos) instanceof PanelBlockEntity panel))
            throw new GameTestAssertException("Panel block entity missing at " + pos);
        return panel;
    }

    /** A right-click on the top of the block at {@code pos} with {@code stack} in the main hand. */
    private static void use(GameTestHelper helper, net.minecraft.world.entity.player.Player player, BlockPos pos,
                            ItemStack stack) {
        var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
        player.setItemInHand(hand, stack);
        BlockPos abs = helper.absolutePos(pos);
        var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        BlockState state = helper.getBlockState(pos);
        //? if >=1.21 {
        state.useItemOn(stack, helper.getLevel(), player, hand, hit);
        //?} else {
        /*state.use(helper.getLevel(), player, hand, hit);
        *///?}
    }

    private static FrequencyPair freq(ItemLike first, ItemLike second) {
        return FrequencyPair.of(new ItemStack(first), new ItemStack(second));
    }

    private static PanelBlockEntity placePanelWith(GameTestHelper helper, PanelElement element) {
        // Self-sufficient floor: face-attached blocks pop off on neighbor
        // updates if unsupported, regardless of what the template provides
        for (int x = 0; x < 7; x++)
            for (int z = 0; z < 7; z++)
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
        PanelBlockEntity panel = placeConsole(helper, PANEL_POS);
        panel.getLayout().addElement(element);
        panel.markLayoutChanged();
        return panel;
    }

    private static PanelBlockEntity placeConsole(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, CCContent.CONTROL_PANEL.getDefaultState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR));
        if (!(helper.getBlockEntity(pos) instanceof PanelBlockEntity panel))
            throw new GameTestAssertException("Panel block entity missing at " + pos);
        return panel;
    }

    /**
     * Always placed in transmitter state, like a player placement: freshly
     * placed SmartBlockEntities only attach their deferred LinkBehaviour via
     * the first-tick mode-mismatch swap (RECEIVER=true from the start is a
     * state normal gameplay never produces — the behaviour would never
     * attach). Receiver tests flip the state afterwards, which swaps the
     * behaviour and copies the frequency across, exactly like wrenching.
     */
    private static void placeCreateLink(GameTestHelper helper) {
        helper.setBlock(LINK_POS, AllBlocks.REDSTONE_LINK.getDefaultState()
                .setValue(RedstoneLinkBlock.RECEIVER, false)
                .setValue(RedstoneLinkBlock.FACING, Direction.UP));
    }

    /** Flips mode via Create's own wrench path. */
    private static void makeCreateLinkReceiver(GameTestHelper helper) {
        BlockState state = helper.getBlockState(LINK_POS);
        if (!(state.getBlock() instanceof RedstoneLinkBlock link))
            throw new GameTestAssertException("Expected redstone link at " + LINK_POS + ", found " + state);
        link.toggleMode(state, helper.getLevel(), helper.absolutePos(LINK_POS));
        helper.getLevel().scheduleTick(helper.absolutePos(LINK_POS), link, 1);
    }

    private static void assertLinkPowered(GameTestHelper helper, boolean powered) {
        BlockState state = helper.getBlockState(LINK_POS);
        if (!state.hasProperty(RedstoneLinkBlock.POWERED) || state.getValue(RedstoneLinkBlock.POWERED) != powered)
            throw new GameTestAssertException("Expected link powered=" + powered + ", found state: " + state);
    }

    private static void setCreateLinkFrequency(GameTestHelper helper, FrequencyPair pair) {
        LinkBehaviour link = BlockEntityBehaviour.get(helper.getLevel(), helper.absolutePos(LINK_POS), LinkBehaviour.TYPE);
        if (link == null)
            throw new GameTestAssertException("Create link behaviour not initialized yet");
        link.setFrequency(true, pair.first());
        link.setFrequency(false, pair.second());
    }

    private static int createLinkReceivedSignal(GameTestHelper helper) {
        if (!(helper.getBlockEntity(LINK_POS) instanceof RedstoneLinkBlockEntity link))
            throw new GameTestAssertException("Create link block entity missing");
        return link.getReceivedSignal();
    }
}
