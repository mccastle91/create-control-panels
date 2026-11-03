package dev.mccastle91.createcontrolling.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.mccastle91.createcontrolling.CreateControlling;
import dev.mccastle91.createcontrolling.registry.CCContent;
import dev.mccastle91.createcontrolling.panel.ConsoleShape;
import dev.mccastle91.createcontrolling.panel.FrequencyPair;
import dev.mccastle91.createcontrolling.panel.PanelBlock;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.CostPayer;
import dev.mccastle91.createcontrolling.panel.PanelEditOps;
import dev.mccastle91.createcontrolling.panel.PanelFaceGeometry;
import dev.mccastle91.createcontrolling.panel.element.ButtonElement;
import dev.mccastle91.createcontrolling.panel.element.KnobElement;
import dev.mccastle91.createcontrolling.panel.element.LampElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElements;
import net.minecraft.nbt.CompoundTag;
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

    // --- helpers -----------------------------------------------------------

    /**
     * Each test uses a distinct pair: tests run in parallel in one world and
     * Create's link range (256) spans all test structures — a shared
     * frequency leaks the strongest transmitter into every other test.
     */
    private static FrequencyPair freq(ItemLike first, ItemLike second) {
        return FrequencyPair.of(new ItemStack(first), new ItemStack(second));
    }

    private static PanelBlockEntity placePanelWith(GameTestHelper helper, PanelElement element) {
        // Self-sufficient floor: face-attached blocks pop off on neighbor
        // updates if unsupported, regardless of what the template provides
        for (int x = 0; x < 7; x++)
            for (int z = 0; z < 7; z++)
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
        helper.setBlock(PANEL_POS, CCContent.CONTROL_PANEL.getDefaultState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR));
        if (!(helper.getBlockEntity(PANEL_POS) instanceof PanelBlockEntity panel))
            throw new GameTestAssertException("Panel block entity missing");
        panel.getLayout().addElement(element);
        panel.markLayoutChanged();
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
