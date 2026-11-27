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
import dev.mccastle91.createcontrolling.panel.FrequencyPair;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.element.LampElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElements;
import dev.mccastle91.createcontrolling.panel.element.SwitchElement;
import dev.mccastle91.createcontrolling.registry.CCContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
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
        placed.getLayout().addElement(toggle);
        placed.getLayout().addElement(lamp);
        placed.markLayoutChanged();
        for (BlockPos link : new BlockPos[] { RECEIVER, TRANSMITTER }) {
            helper.setBlock(link.below(), Blocks.STONE.defaultBlockState());
            // placed as a transmitter, like a player's: see PanelLinkGameTests#placeCreateLink
            helper.setBlock(link, AllBlocks.REDSTONE_LINK.getDefaultState()
                    .setValue(RedstoneLinkBlock.RECEIVER, false)
                    .setValue(RedstoneLinkBlock.FACING, Direction.UP));
        }
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
