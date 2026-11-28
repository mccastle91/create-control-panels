package dev.mccastle91.createcontrolling.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.elevator.ElevatorContactBlock;
import com.simibubi.create.content.contraptions.elevator.ElevatorContraption;
import com.simibubi.create.content.contraptions.elevator.ElevatorPulleyBlock;
import com.simibubi.create.content.contraptions.elevator.ElevatorPulleyBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.redstone.contact.RedstoneContactBlock;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.mccastle91.createcontrolling.CreateControlling;
import dev.mccastle91.createcontrolling.compat.create.contraption.MovingConsole;
import dev.mccastle91.createcontrolling.panel.CostPayer;
import dev.mccastle91.createcontrolling.panel.FrequencyPair;
import dev.mccastle91.createcontrolling.panel.PanelEditOps;
import dev.mccastle91.createcontrolling.util.McCompat;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.element.ActorSwitchElement;
import dev.mccastle91.createcontrolling.panel.element.FloorButtonElement;
import dev.mccastle91.createcontrolling.panel.element.FloorReadoutElement;
import dev.mccastle91.createcontrolling.panel.element.KnobElement;
import dev.mccastle91.createcontrolling.panel.element.LampElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElements;
import dev.mccastle91.createcontrolling.panel.element.SwitchElement;
import dev.mccastle91.createcontrolling.registry.CCContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

//? if neoforge {
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
//?} else {
/*import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
*///?}

/**
 * Consoles on Create contraptions, on a real elevator: an Elevator Pulley
 * turned by a creative motor, a cabin of one Redstone Contact with a console
 * on its side, and an Elevator Contact for it to stop at. The server half
 * only: nothing here draws a console or aims a click.
 */
@GameTestHolder(CreateControlling.MOD_ID)
@PrefixGameTestTemplate(false)
public class ContraptionGameTests {

    private static final String EMPTY = "empty";
    private static final String READOUT = "FLOOR 2";

    // The cabin: the contact is what the pulley picks up, the console hangs on its south side
    private static final BlockPos CABIN = new BlockPos(3, 1, 3);
    private static final BlockPos CONSOLE = CABIN.south();
    private static final BlockPos FLOOR_CONTACT = CABIN.north();
    private static final BlockPos PULLEY = CABIN.above(2);
    private static final BlockPos MOTOR = PULLEY.west();

    // Create's own links, off the cabin: one hears the console's switch, one sends to its lamp
    private static final BlockPos RECEIVER = new BlockPos(5, 1, 5);
    private static final BlockPos TRANSMITTER = new BlockPos(1, 1, 5);

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public void consoleOnAnElevatorKeepsWorking(GameTestHelper helper) {
        SwitchElement toggle = PanelElements.TOGGLE_SWITCH.create();
        toggle.setPos(2, 2);
        toggle.setFrequency(freq(Items.COPPER_INGOT, Items.CLAY_BALL));
        LampElement lamp = PanelElements.LAMP.create();
        lamp.setPos(9, 9);
        lamp.setFrequency(freq(Items.BRICK, Items.STRING));
        KnobElement knob = PanelElements.KNOB.create();
        knob.setPos(9, 2);

        PanelBlockEntity placed = build(helper, toggle, lamp, knob);
        // as a Display Link leaves it: no link reaches the console once it rides
        placed.setDisplayLine(1, Component.literal(READOUT));
        placed.syncDisplayLines();
        BlockPos local = CONSOLE.subtract(CABIN);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    setLinkFrequency(helper, RECEIVER, toggle.frequency());
                    setLinkFrequency(helper, TRANSMITTER, lamp.frequency());
                })
                .thenExecute(() -> {
                    BlockState state = helper.getBlockState(RECEIVER);
                    RedstoneLinkBlock link = (RedstoneLinkBlock) state.getBlock();
                    link.toggleMode(state, helper.getLevel(), helper.absolutePos(RECEIVER));
                    helper.getLevel().scheduleTick(helper.absolutePos(RECEIVER), link, 1);
                })
                // the pulley only assembles while it turns
                .thenWaitUntil(() -> {
                    if (pulley(helper).getSpeed() == 0)
                        throw new GameTestAssertException("The motor has not reached the elevator pulley yet");
                })
                .thenExecute(() -> pulley(helper).clicked())
                .thenWaitUntil(() -> {
                    AbstractContraptionEntity entity = elevator(helper);
                    if (!(entity.getContraption() instanceof ElevatorContraption))
                        throw new GameTestAssertException("Expected an elevator, got " + entity.getContraption());
                    if (!helper.getBlockState(CONSOLE).isAir())
                        throw new GameTestAssertException("The console was not taken along: " + helper.getBlockState(CONSOLE));
                    if (!entity.getContraption().getBlocks().containsKey(local))
                        throw new GameTestAssertException("The elevator has no block where the console should be");
                })
                .thenExecuteAfter(2, () -> {
                    AbstractContraptionEntity entity = elevator(helper);
                    // Create's click ray asks for the shape like this; without the block entity it used to get a floor slab
                    if (CCContent.CONTROL_PANEL.getDefaultState()
                            .getShape(entity.getContraption().getContraptionWorld(), BlockPos.ZERO.below()) != Shapes.block())
                        throw new GameTestAssertException("A console on a contraption should offer the click ray its whole block");
                    //? if >=1.21 {
                    Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
                    //?} else {
                    /*Player player = helper.makeMockPlayer();
                    *///?}
                    Vec3 beside = Vec3.atCenterOf(helper.absolutePos(CONSOLE.south()));
                    player.setPos(beside.x, beside.y - 0.5, beside.z);
                    MovingConsole.click(player, entity.getId(), local, toggle.id(), 0, 1f, 1f, false);
                    // a drag sets a knob outright, a sneak-scroll steps it
                    MovingConsole.setValue(player, entity.getId(), local, knob.id(), 7, false);
                    MovingConsole.setValue(player, entity.getId(), local, knob.id(), 1, true);
                    PanelBlockEntity riding = MovingConsole.at(entity, local).console();
                    if (!(riding.getLayout().elementById(knob.id()).orElseThrow() instanceof KnobElement turned)
                            || turned.value() != 8)
                        throw new GameTestAssertException("The knob on the elevator should stand at 8 after a drag to 7 and a step up");
                    if (!READOUT.equals(riding.displayLine(1).getString()))
                        throw new GameTestAssertException("The readout lost its text on the elevator: '"
                                + riding.displayLine(1).getString() + "'");
                    // ...and it is in what clients are sent
                    if (!MovingConsole.at(entity, local).syncPayload(entity).tag().contains(PanelBlockEntity.TAG_DISPLAY_LINES))
                        throw new GameTestAssertException("The readout's text is missing from the state sent to clients");
                })
                .thenWaitUntil(() -> assertLinkPowered(helper, true))
                .thenExecute(() -> {
                    if (!(helper.getBlockEntity(TRANSMITTER) instanceof RedstoneLinkBlockEntity link))
                        throw new GameTestAssertException("Create link block entity missing");
                    link.transmit(9);
                })
                .thenWaitUntil(() -> {
                    MovingConsole moving = MovingConsole.at(elevator(helper), local);
                    if (moving == null)
                        throw new GameTestAssertException("No running console on the elevator");
                    int shown = moving.console().getLayout().elementById(lamp.id()).orElseThrow().displayStrength();
                    if (shown != 9)
                        throw new GameTestAssertException("Expected the lamp on the elevator at 9, got " + shown);
                })
                // taking the elevator apart puts the console back as it is now, switch on
                .thenExecute(() -> pulley(helper).clicked())
                .thenWaitUntil(() -> {
                    if (!(console(helper).getLayout().elementById(toggle.id()).orElseThrow() instanceof SwitchElement back)
                            || !back.isOn())
                        throw new GameTestAssertException("The switch should come back from the elevator switched on");
                    if (!(console(helper).getLayout().elementById(knob.id()).orElseThrow() instanceof KnobElement back2)
                            || back2.value() != 8)
                        throw new GameTestAssertException("The knob should come back from the elevator at 8");
                    if (!READOUT.equals(console(helper).displayLine(1).getString()))
                        throw new GameTestAssertException("The readout should come back from the elevator with its text");
                    assertLinkPowered(helper, true);
                })
                // ...and it is the only one sending: nothing of the elevator's console is left on the network
                .thenExecuteAfter(2, () -> {
                    if (!console(helper).clickElement(toggle.id(), 1f, 1f, false))
                        throw new GameTestAssertException("The placed console's switch did not react");
                })
                .thenWaitUntil(() -> assertLinkPowered(helper, false))
                .thenSucceed();
    }

    /**
     * The actor switch does what a Contraption Controls block does, here to
     * the cabin's own Redstone Contact (an actor like a drill, and one of the
     * kinds a filter can name): off when the elevator assembles, it has the
     * contact stopped before its first tick; thrown, it starts it; a signal on
     * its frequency holds it stopped and lets go again; and when something
     * else stops the contact, the lever follows.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public void actorSwitchStopsAndStartsActors(GameTestHelper helper) {
        ItemStack contact = AllBlocks.REDSTONE_CONTACT.asStack();
        ActorSwitchElement actors = PanelElements.ACTOR_SWITCH.create();
        actors.setPos(2, 2);
        actors.setOn(false);
        actors.setFrequency(freq(Items.FLINT, Items.FEATHER));
        PanelBlockEntity placed = build(helper, actors);
        // the filter goes in as the designer sends it: the third slot takes an actor's item and nothing else
        setFilter(helper, placed, actors, new ItemStack(Items.STICK));
        if (!filterOf(placed, actors).isEmpty())
            throw new GameTestAssertException("A stick is no kind of actor: the filter should have refused it");
        setFilter(helper, placed, actors, contact);
        if (!ItemStack.isSameItem(filterOf(placed, actors), contact))
            throw new GameTestAssertException("The filter should be the Redstone Contact, is " + filterOf(placed, actors));
        BlockPos local = CONSOLE.subtract(CABIN);

        helper.startSequence()
                .thenWaitUntil(() -> setLinkFrequency(helper, TRANSMITTER, actors.frequency()))
                .thenWaitUntil(() -> {
                    if (pulley(helper).getSpeed() == 0)
                        throw new GameTestAssertException("The motor has not reached the elevator pulley yet");
                })
                .thenExecute(() -> pulley(helper).clicked())
                // stopped by the time the contraption exists: nothing has ticked yet
                .thenWaitUntil(() -> assertActors(helper, contact, false, "an actor switch that was off at assembly"))
                .thenExecuteAfter(2, () -> {
                    AbstractContraptionEntity entity = elevator(helper);
                    assertActors(helper, contact, false, "the switch still off two ticks on");
                    MovingConsole.click(playerBeside(helper), entity.getId(), local, actors.id(), 0, 1f, 1f, false);
                })
                .thenWaitUntil(() -> {
                    assertActors(helper, contact, true, "the switch thrown on");
                    if (!riding(helper, local, actors).isOn())
                        throw new GameTestAssertException("The lever should be on");
                })
                // a signal on its frequency holds the actors, whatever the lever says
                .thenExecute(() -> transmitter(helper).transmit(15))
                .thenWaitUntil(() -> {
                    assertActors(helper, contact, false, "a signal on the switch's frequency");
                    ActorSwitchElement part = riding(helper, local, actors);
                    if (!part.held() || !part.isOn())
                        throw new GameTestAssertException("The switch should be held by its signal with the lever still on");
                })
                .thenExecute(() -> transmitter(helper).transmit(0))
                .thenWaitUntil(() -> assertActors(helper, contact, true, "the signal gone"))
                // something else stops them, as a Contraption Controls block would: the lever follows
                .thenExecuteAfter(2, () -> {
                    com.simibubi.create.content.contraptions.Contraption contraption = elevator(helper).getContraption();
                    contraption.getDisabledActors().add(contact.copy());
                    contraption.setActorsActive(contact, false);
                })
                .thenWaitUntil(() -> {
                    if (riding(helper, local, actors).isOn())
                        throw new GameTestAssertException("The lever should follow actors stopped another way");
                    assertActors(helper, contact, false, "actors stopped another way");
                })
                // and the console comes back with the lever where it was left
                .thenExecuteAfter(2, () -> pulley(helper).clicked())
                .thenWaitUntil(() -> {
                    if (!(console(helper).getLayout().elementById(actors.id()).orElseThrow() instanceof ActorSwitchElement back)
                            || back.isOn())
                        throw new GameTestAssertException("The actor switch should come back from the elevator off");
                })
                .thenSucceed();
    }

    /**
     * An elevator panel: with two floors in the shaft, the readout names the
     * floor the cabin stands at and that floor's button glows; a press on the
     * other floor's button sends the cabin there, as picking it on a
     * Contraption Controls block would.
     */
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public void floorButtonCallsTheCabin(GameTestHelper helper) {
        FloorButtonElement ground = PanelElements.FLOOR_BUTTON.create();
        ground.setPos(2, 2);
        ground.setFloor(1);
        FloorButtonElement upper = PanelElements.FLOOR_BUTTON.create();
        upper.setPos(6, 2);
        upper.setFloor(2);
        FloorReadoutElement readout = PanelElements.FLOOR_READOUT.create();
        readout.setPos(2, 8);
        build(helper, ground, upper, readout);
        helper.setBlock(FLOOR_CONTACT.above(), AllBlocks.ELEVATOR_CONTACT.getDefaultState()
                .setValue(ElevatorContactBlock.FACING, Direction.SOUTH));
        BlockPos local = CONSOLE.subtract(CABIN);
        double[] startY = new double[1];

        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (pulley(helper).getSpeed() == 0)
                        throw new GameTestAssertException("The motor has not reached the elevator pulley yet");
                })
                .thenExecute(() -> pulley(helper).clicked())
                // Create hands out the floors' names a few ticks after assembly
                .thenWaitUntil(() -> {
                    AbstractContraptionEntity entity = elevator(helper);
                    ElevatorContraption cabin = (ElevatorContraption) entity.getContraption();
                    if (cabin.namesList.size() != 2)
                        throw new GameTestAssertException("Expected two floors, found " + cabin.namesList.size());
                    String name = floorName(cabin, 0);
                    if (name.isEmpty())
                        throw new GameTestAssertException("The lower floor has no name to show");
                    if (!name.equals(riding(helper, local, readout).displayText()))
                        throw new GameTestAssertException("The readout should name the lower floor '" + name + "', shows '"
                                + riding(helper, local, readout).displayText() + "'");
                    if (!riding(helper, local, ground).isLit() || riding(helper, local, upper).isLit())
                        throw new GameTestAssertException("Only the lower floor's button should glow at the start");
                    startY[0] = entity.getY();
                })
                .thenExecute(() -> MovingConsole.click(playerBeside(helper), elevator(helper).getId(), local,
                        upper.id(), 0, 1f, 1f, false))
                .thenWaitUntil(() -> {
                    AbstractContraptionEntity entity = elevator(helper);
                    if (entity.getY() < startY[0] + 0.99)
                        throw new GameTestAssertException("The cabin has not reached the upper floor: " + entity.getY());
                    String name = floorName((ElevatorContraption) entity.getContraption(), 1);
                    if (!name.equals(riding(helper, local, readout).displayText()))
                        throw new GameTestAssertException("The readout should name the upper floor '" + name + "', shows '"
                                + riding(helper, local, readout).displayText() + "'");
                    if (riding(helper, local, ground).isLit() || !riding(helper, local, upper).isLit())
                        throw new GameTestAssertException("Only the upper floor's button should glow now");
                })
                .thenSucceed();
    }

    /**
     * The elevator the tests share: a cabin of one Redstone Contact with a
     * wall console on it carrying {@code parts}, an Elevator Contact for it to
     * stop at, the pulley and its motor, and two of Create's links to the side.
     */
    private static PanelBlockEntity build(GameTestHelper helper, PanelElement... parts) {
        helper.setBlock(CABIN, AllBlocks.REDSTONE_CONTACT.getDefaultState()
                .setValue(RedstoneContactBlock.FACING, Direction.NORTH));
        helper.setBlock(FLOOR_CONTACT, AllBlocks.ELEVATOR_CONTACT.getDefaultState()
                .setValue(ElevatorContactBlock.FACING, Direction.SOUTH));
        helper.setBlock(PULLEY, AllBlocks.ELEVATOR_PULLEY.getDefaultState()
                .setValue(ElevatorPulleyBlock.HORIZONTAL_FACING, Direction.NORTH));
        helper.setBlock(MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(CreativeMotorBlock.FACING, Direction.EAST));
        helper.setBlock(CONSOLE, CCContent.CONTROL_PANEL.getDefaultState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL)
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACING, Direction.SOUTH));
        PanelBlockEntity placed = console(helper);
        for (PanelElement part : parts)
            placed.getLayout().addElement(part);
        placed.markLayoutChanged();
        for (BlockPos link : new BlockPos[] { RECEIVER, TRANSMITTER }) {
            helper.setBlock(link.below(), Blocks.STONE.defaultBlockState());
            // placed as a transmitter, like a player's: see PanelLinkGameTests#placeCreateLink
            helper.setBlock(link, AllBlocks.REDSTONE_LINK.getDefaultState()
                    .setValue(RedstoneLinkBlock.RECEIVER, false)
                    .setValue(RedstoneLinkBlock.FACING, Direction.UP));
        }
        return placed;
    }

    /** The part with {@code part}'s id on the console the elevator carries, as it is now. */
    @SuppressWarnings("unchecked")
    private static <T extends PanelElement> T riding(GameTestHelper helper, BlockPos local, T part) {
        MovingConsole moving = MovingConsole.at(elevator(helper), local);
        if (moving == null)
            throw new GameTestAssertException("No running console on the elevator");
        return (T) moving.console().getLayout().elementById(part.id()).orElseThrow();
    }

    /** Whether the cabin's Redstone Contact runs, by the contraption's list and by the actor itself. */
    private static void assertActors(GameTestHelper helper, ItemStack kind, boolean running, String after) {
        com.simibubi.create.content.contraptions.Contraption contraption = elevator(helper).getContraption();
        var actor = contraption.getActorAt(BlockPos.ZERO);
        if (actor == null || actor.right == null)
            throw new GameTestAssertException("The cabin's contact is not an actor of the elevator");
        if (contraption.isActorTypeDisabled(kind) == running || actor.right.disabled == running)
            throw new GameTestAssertException("After " + after + " the contact should be " + (running ? "running" : "stopped")
                    + ": on the list=" + contraption.isActorTypeDisabled(kind) + ", actor disabled=" + actor.right.disabled);
    }

    private static void setFilter(GameTestHelper helper, PanelBlockEntity console, ActorSwitchElement part, ItemStack item) {
        net.minecraft.nbt.CompoundTag op = new net.minecraft.nbt.CompoundTag();
        op.putString(PanelEditOps.KEY_OP, PanelEditOps.OP_SET_FREQUENCY);
        op.putUUID("Id", part.id());
        op.putInt("Slot", ActorSwitchElement.FILTER_SLOT);
        op.put("Item", McCompat.saveStack(item, helper.getLevel().registryAccess()));
        PanelEditOps.apply(console, op, helper.getLevel().registryAccess(), CostPayer.FREE);
    }

    private static ItemStack filterOf(PanelBlockEntity console, ActorSwitchElement part) {
        return ((ActorSwitchElement) console.getLayout().elementById(part.id()).orElseThrow()).filter();
    }

    private static String floorName(ElevatorContraption cabin, int index) {
        return cabin.namesList.get(index).getSecond().getFirst();
    }

    private static Player playerBeside(GameTestHelper helper) {
        //? if >=1.21 {
        Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        //?} else {
        /*Player player = helper.makeMockPlayer();
        *///?}
        Vec3 beside = Vec3.atCenterOf(helper.absolutePos(CONSOLE.south()));
        player.setPos(beside.x, beside.y - 0.5, beside.z);
        return player;
    }

    private static RedstoneLinkBlockEntity transmitter(GameTestHelper helper) {
        if (!(helper.getBlockEntity(TRANSMITTER) instanceof RedstoneLinkBlockEntity link))
            throw new GameTestAssertException("Create link block entity missing");
        return link;
    }

    private static PanelBlockEntity console(GameTestHelper helper) {
        if (!(helper.getBlockEntity(CONSOLE) instanceof PanelBlockEntity panel))
            throw new GameTestAssertException("No console at " + CONSOLE + ": " + helper.getBlockState(CONSOLE));
        return panel;
    }

    private static ElevatorPulleyBlockEntity pulley(GameTestHelper helper) {
        if (!(helper.getBlockEntity(PULLEY) instanceof ElevatorPulleyBlockEntity pulley))
            throw new GameTestAssertException("No elevator pulley at " + PULLEY);
        return pulley;
    }

    private static AbstractContraptionEntity elevator(GameTestHelper helper) {
        AbstractContraptionEntity entity = pulley(helper).movedContraption;
        if (entity == null || entity.getContraption() == null)
            throw new GameTestAssertException("The elevator has not assembled");
        return entity;
    }

    private static FrequencyPair freq(net.minecraft.world.level.ItemLike first, net.minecraft.world.level.ItemLike second) {
        return FrequencyPair.of(new ItemStack(first), new ItemStack(second));
    }

    private static void setLinkFrequency(GameTestHelper helper, BlockPos pos, FrequencyPair pair) {
        LinkBehaviour link = BlockEntityBehaviour.get(helper.getLevel(), helper.absolutePos(pos), LinkBehaviour.TYPE);
        if (link == null)
            throw new GameTestAssertException("Create link behaviour not initialized yet");
        link.setFrequency(true, pair.first());
        link.setFrequency(false, pair.second());
    }

    private static void assertLinkPowered(GameTestHelper helper, boolean powered) {
        BlockState state = helper.getBlockState(RECEIVER);
        if (!state.hasProperty(RedstoneLinkBlock.POWERED) || state.getValue(RedstoneLinkBlock.POWERED) != powered)
            throw new GameTestAssertException("Expected link powered=" + powered + ", found state: " + state);
    }
}
