package dev.mccastle91.createcontrolling.gametest;

import dev.mccastle91.createcontrolling.CreateControlling;
import dev.mccastle91.createcontrolling.item.PanelBlueprintItem;
import dev.mccastle91.createcontrolling.panel.ComponentCosts;
import dev.mccastle91.createcontrolling.panel.CostPayer;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.PanelEditOps;
import dev.mccastle91.createcontrolling.panel.PanelLayout;
import dev.mccastle91.createcontrolling.panel.element.LampElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElementType;
import dev.mccastle91.createcontrolling.panel.element.PanelElements;
import dev.mccastle91.createcontrolling.panel.element.SwitchElement;
import dev.mccastle91.createcontrolling.registry.CCContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

//? if neoforge {
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
//?} else {
/*import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
*///?}

/**
 * The Console Blueprint in a running server, with a player who pays: drawing
 * on it is free, a plain click on a console changes nothing, a sneak-click
 * builds the design for the difference in parts, and a blueprint that holds
 * a design is never overwritten by a click.
 */
@GameTestHolder(CreateControlling.MOD_ID)
@PrefixGameTestTemplate(false)
public class BlueprintGameTests {

    private static final String EMPTY = "empty";
    private static final BlockPos PANEL_POS = new BlockPos(1, 1, 1);
    private static final BlockPos OTHER_POS = new BlockPos(1, 1, 4);

    @GameTest(template = EMPTY)
    public void blueprintDrawsForFreeAndBuildsOnSneakClick(GameTestHelper helper) {
        PanelBlockEntity panel = placeConsole(helper, PANEL_POS);
        Player player = payingPlayer(helper);
        ItemStack blueprint = new ItemStack(CCContent.PANEL_BLUEPRINT.get());

        // drawing: two parts, nothing in the inventory, nothing asked for
        draw(helper, blueprint, PanelElements.LAMP);
        draw(helper, blueprint, PanelElements.LARGE_LAMP);
        if (PanelBlueprintItem.partCount(blueprint) != 2)
            throw new GameTestAssertException("Drawing two parts should leave two on the blueprint, got "
                    + PanelBlueprintItem.partCount(blueprint));
        PanelLayout design = PanelBlueprintItem.design(blueprint, helper.getLevel().registryAccess());
        List<ComponentCosts.Cost> total = ComponentCosts.totalOf(design);
        if (total.size() != 1 || total.get(0).count() != 3 || !total.get(0).matches(new ItemStack(Items.REDSTONE_LAMP)))
            throw new GameTestAssertException("A lamp and a large lamp should total 3 redstone lamps, got " + total);
        for (PanelElement element : design.elements())
            if (!element.paid().isEmpty())
                throw new GameTestAssertException("A drawn part should own no items");

        // a plain click says what is missing and changes nothing
        click(helper, player, PANEL_POS, blueprint, false);
        if (!panel.getLayout().isEmpty())
            throw new GameTestAssertException("A plain click with a blueprint must not build it");

        // a sneak-click without the parts builds nothing and takes nothing
        give(player, Items.REDSTONE_LAMP, 2);
        click(helper, player, PANEL_POS, blueprint, true);
        if (!panel.getLayout().isEmpty() || count(player, Items.REDSTONE_LAMP) != 2)
            throw new GameTestAssertException("A design the player can't pay for must not be built, nor part-paid");

        // with the parts it builds, and takes exactly them
        give(player, Items.REDSTONE_LAMP, 2);
        click(helper, player, PANEL_POS, blueprint, true);
        if (panel.getLayout().elementCount() != 2)
            throw new GameTestAssertException("A sneak-click with the parts should build the design, got "
                    + panel.getLayout().elementCount() + " parts");
        if (count(player, Items.REDSTONE_LAMP) != 1)
            throw new GameTestAssertException("Building should take 3 of 4 lamps, left " + count(player, Items.REDSTONE_LAMP));
        if (PanelBlueprintItem.partCount(blueprint) != 2)
            throw new GameTestAssertException("The blueprint should keep its design after building it");

        // the same design again costs nothing more: the console already has the parts
        click(helper, player, PANEL_POS, blueprint, true);
        if (count(player, Items.REDSTONE_LAMP) != 1)
            throw new GameTestAssertException("Building a design the console already has should cost nothing");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public void blueprintCopiesOnlyWhileEmpty(GameTestHelper helper) {
        PanelBlockEntity first = placeConsole(helper, PANEL_POS);
        Player player = payingPlayer(helper);
        give(player, Items.LEVER, 1);
        if (!PanelEditOps.apply(first, addOp(PanelElements.TOGGLE_SWITCH), helper.getLevel().registryAccess(),
                CostPayer.of(player)))
            throw new GameTestAssertException("Could not mount a switch on the first console");
        PanelBlockEntity second = placeConsole(helper, OTHER_POS);
        second.getLayout().addElement(PanelElements.LAMP.create());
        second.getLayout().addElement(PanelElements.BUTTON.create());
        second.markLayoutChanged();

        ItemStack blueprint = new ItemStack(CCContent.PANEL_BLUEPRINT.get());
        click(helper, player, PANEL_POS, blueprint, false);
        PanelLayout copied = PanelBlueprintItem.design(blueprint, helper.getLevel().registryAccess());
        if (copied.elementCount() != 1 || !(copied.elements().get(0) instanceof SwitchElement))
            throw new GameTestAssertException("An empty blueprint should copy the console it is clicked on");
        if (!copied.elements().get(0).paid().isEmpty())
            throw new GameTestAssertException("A copy must not carry the items paid for the original");
        if (first.getLayout().elements().get(0).paid().isEmpty())
            throw new GameTestAssertException("Copying must leave the original's parts paid for");

        // holding a design, it copies nothing: neither on a plain click nor on a sneak-click it can't pay for
        click(helper, player, OTHER_POS, blueprint, false);
        click(helper, player, OTHER_POS, blueprint, true);
        copied = PanelBlueprintItem.design(blueprint, helper.getLevel().registryAccess());
        if (copied.elementCount() != 1 || !(copied.elements().get(0) instanceof SwitchElement))
            throw new GameTestAssertException("A blueprint holding a design was overwritten by a click");
        if (second.getLayout().elementCount() != 2)
            throw new GameTestAssertException("A design the player can't pay for replaced a console's parts");

        // cleared, it copies again
        CompoundTag clear = new CompoundTag();
        clear.putString(PanelEditOps.KEY_OP, PanelEditOps.OP_CLEAR);
        if (!PanelBlueprintItem.edit(blueprint, clear, helper.getLevel().registryAccess())
                || PanelBlueprintItem.partCount(blueprint) != 0)
            throw new GameTestAssertException("Clearing should empty the blueprint");
        click(helper, player, OTHER_POS, blueprint, false);
        if (PanelBlueprintItem.partCount(blueprint) != 2)
            throw new GameTestAssertException("A cleared blueprint should copy again");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public void buildingChargesTheDifferenceAndRefundsTheRest(GameTestHelper helper) {
        PanelBlockEntity panel = placeConsole(helper, PANEL_POS);
        Player player = payingPlayer(helper);
        give(player, Items.LEVER, 1);
        if (!PanelEditOps.apply(panel, addOp(PanelElements.TOGGLE_SWITCH), helper.getLevel().registryAccess(),
                CostPayer.of(player)) || count(player, Items.LEVER) != 0)
            throw new GameTestAssertException("Mounting a switch should take the lever");

        // a design with a switch and a lamp: the console's lever is reused, only the lamp is charged
        ItemStack both = new ItemStack(CCContent.PANEL_BLUEPRINT.get());
        draw(helper, both, PanelElements.TOGGLE_SWITCH);
        draw(helper, both, PanelElements.LAMP);
        give(player, Items.REDSTONE_LAMP, 1);
        click(helper, player, PANEL_POS, both, true);
        if (panel.getLayout().elementCount() != 2 || count(player, Items.REDSTONE_LAMP) != 0 || count(player, Items.LEVER) != 0)
            throw new GameTestAssertException("Building over a console should charge only what it lacks");

        // a design with only a lamp: the lever comes back
        ItemStack lampOnly = new ItemStack(CCContent.PANEL_BLUEPRINT.get());
        draw(helper, lampOnly, PanelElements.LAMP);
        click(helper, player, PANEL_POS, lampOnly, true);
        if (panel.getLayout().elementCount() != 1 || !(panel.getLayout().elements().get(0) instanceof LampElement))
            throw new GameTestAssertException("The console should now hold just the lamp");
        if (count(player, Items.LEVER) != 1 || count(player, Items.REDSTONE_LAMP) != 0)
            throw new GameTestAssertException("Parts the new design doesn't need should be handed back");

        // a player in creative builds for nothing
        player.getAbilities().instabuild = true;
        player.getInventory().clearContent();
        click(helper, player, PANEL_POS, both, true);
        if (panel.getLayout().elementCount() != 2)
            throw new GameTestAssertException("A creative player should build without parts");
        helper.succeed();
    }

    // --- helpers -----------------------------------------------------------------

    private static PanelBlockEntity placeConsole(GameTestHelper helper, BlockPos pos) {
        for (int x = 0; x < 7; x++)
            for (int z = 0; z < 7; z++)
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE.defaultBlockState());
        helper.setBlock(pos, CCContent.CONTROL_PANEL.getDefaultState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR));
        if (!(helper.getBlockEntity(pos) instanceof PanelBlockEntity panel))
            throw new GameTestAssertException("Panel block entity missing at " + pos);
        return panel;
    }

    /** A player who pays for parts, with nothing on them. */
    private static Player payingPlayer(GameTestHelper helper) {
        //? if >=1.21 {
        Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        //?} else {
        /*Player player = helper.makeMockPlayer();
        *///?}
        player.getAbilities().instabuild = false;
        player.getInventory().clearContent();
        return player;
    }

    private static CompoundTag addOp(PanelElementType<?> type) {
        CompoundTag op = new CompoundTag();
        op.putString(PanelEditOps.KEY_OP, PanelEditOps.OP_ADD);
        op.putString("Type", type.id());
        op.putFloat("X", 6);
        op.putFloat("Y", 6);
        return op;
    }

    /** Adds a part to the blueprint's drawing, as its designer does. */
    private static void draw(GameTestHelper helper, ItemStack blueprint, PanelElementType<?> type) {
        if (!PanelBlueprintItem.edit(blueprint, addOp(type), helper.getLevel().registryAccess()))
            throw new GameTestAssertException("Could not draw a " + type.id() + " on the blueprint");
    }

    private static void give(Player player, Item item, int count) {
        player.getInventory().add(new ItemStack(item, count));
    }

    private static int count(Player player, Item item) {
        return player.getInventory().countItem(item);
    }

    /**
     * A click on the console at {@code pos} with the blueprint in hand, the
     * way the game routes it: a plain click goes to the block, a sneaking
     * player's click skips the block and goes to the item.
     */
    private static void click(GameTestHelper helper, Player player, BlockPos pos, ItemStack blueprint, boolean sneaking) {
        // in the off hand, so the parts in the inventory stay where they are
        InteractionHand hand = InteractionHand.OFF_HAND;
        player.setItemInHand(hand, blueprint);
        player.setShiftKeyDown(sneaking);
        BlockPos abs = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        if (sneaking) {
            blueprint.useOn(new UseOnContext(player, hand, hit));
        } else {
            BlockState state = helper.getBlockState(pos);
            //? if >=1.21 {
            state.useItemOn(blueprint, helper.getLevel(), player, hand, hit);
            //?} else {
            /*state.use(helper.getLevel(), player, hand, hit);
            *///?}
        }
        player.setShiftKeyDown(false);
    }
}
