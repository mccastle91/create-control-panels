package dev.mccastle91.createcontrolling.compat.create;

import com.simibubi.create.content.contraptions.ContraptionWorld;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import dev.mccastle91.createcontrolling.panel.ConsoleShape;
import dev.mccastle91.createcontrolling.panel.ConsoleShapes;
import dev.mccastle91.createcontrolling.panel.PanelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Create-wrench integration: wrenching a console steps its angle through the
 * presets (flat, 15°, 30°, 45°; a wall console runs through them with its top
 * edge raised, then with its bottom edge raised). Sneak-wrenching is left to
 * Create: it picks the console up into the player's inventory, design and all
 * (the drop carries it, see {@link PanelBlock#getDrops}) — unless the server
 * config {@code sneakWrenchOpensDesigner} gives it to the designer instead.
 */
public class WrenchablePanelBlock extends PanelBlock implements IWrenchable {

    public WrenchablePanelBlock(Properties properties) {
        super(properties);
    }

    //? if >=1.21 {
    @Override
    protected com.mojang.serialization.MapCodec<? extends PanelBlock> codec() {
        return simpleCodec(WrenchablePanelBlock::new);
    }
    //?}

    // On a contraption Create asks a block for its shapes with no block entity
    // to read the real one from, and for the click ray at a made-up position.
    // The ray gets the whole block (client/ContraptionConsoles then finds the
    // part, or passes the click on); walking into it, the console as it is first placed.

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return level instanceof ContraptionWorld ? Shapes.block() : super.getShape(state, level, pos, context);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level instanceof ContraptionWorld)
            return ConsoleShapes.collision(state, ConsoleShape.defaultFor(state.getValue(FACE)));
        return super.getCollisionShape(state, level, pos, context);
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        if (!dev.mccastle91.createcontrolling.config.CCConfig.sneakWrenchOpensDesigner)
            return IWrenchable.super.onSneakWrenched(state, context);
        if (context.getLevel().isClientSide && dev.mccastle91.createcontrolling.client.CCClientHooks.OPEN_DESIGNER != null)
            dev.mccastle91.createcontrolling.client.CCClientHooks.OPEN_DESIGNER.accept(context.getClickedPos());
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        InteractionResult cycled = cycleShapePreset(state, context.getLevel(), context.getClickedPos());
        if (cycled == InteractionResult.PASS)
            return IWrenchable.super.onWrenched(state, context);
        IWrenchable.playRotateSound(context.getLevel(), context.getClickedPos());
        return cycled;
    }
}
