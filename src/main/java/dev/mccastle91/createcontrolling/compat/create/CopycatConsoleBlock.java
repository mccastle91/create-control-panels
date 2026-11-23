package dev.mccastle91.createcontrolling.compat.create;

import com.simibubi.create.AllBlocks;
import dev.mccastle91.createcontrolling.panel.ConsoleCasing;
import dev.mccastle91.createcontrolling.panel.PanelBlock;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The copycat console: a console that takes the look of a block applied to it,
 * the way Create's copycat panels and steps do. Its body wears that block's
 * textures; its face plate and the zinc trim of its parts stay as they are.
 *
 * Which blocks it accepts is Create's own rule (its allow and deny tags
 * included), asked of the Copycat Step. Right-clicking with a block applies
 * it; with a different block, swaps it and hands the old one back; with the
 * same block again, turns it (a log's axis). The designer removes it.
 */
public class CopycatConsoleBlock extends WrenchablePanelBlock {

    public CopycatConsoleBlock(Properties properties) {
        super(properties);
    }

    //? if >=1.21 {
    @Override
    protected com.mojang.serialization.MapCodec<? extends PanelBlock> codec() {
        return simpleCodec(CopycatConsoleBlock::new);
    }
    //?}

    @Override
    public ConsoleCasing fixedCasing() {
        return ConsoleCasing.COPYCAT;
    }

    /**
     * What this console counts as when a neighbour's connected texture asks
     * (the loaders' appearance hook): to another console, the block it copies,
     * so the copies connect to each other as the real blocks would. To any
     * other block it stays a console; a wall of real casing doesn't run its
     * texture into a wedge.
     */
    @Override
    public BlockState getAppearance(BlockState state, net.minecraft.world.level.BlockAndTintGetter level, BlockPos pos,
                                    net.minecraft.core.Direction side, @Nullable BlockState queryState,
                                    @Nullable BlockPos queryPos) {
        if (queryState == null || !(queryState.getBlock() instanceof PanelBlock))
            return state;
        BlockState material = level.getBlockEntity(pos) instanceof PanelBlockEntity panel ? panel.material() : null;
        return material == null ? state : material;
    }

    // A console copying a block that gives off light gives off the same light.

    //? if neoforge {
    @Override
    public boolean hasDynamicLightEmission(BlockState state) {
        return true;
    }

    /** The light the console's block entity noted with the chunk (see {@code PanelBlockEntity.updateMaterialLight}). */
    @Override
    public int getLightEmission(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        var lights = level.getAuxLightManager(pos);
        return lights != null ? lights.getLightAt(pos) : super.getLightEmission(state, level, pos);
    }
    //?} else {
    /*@Override
    public int getLightEmission(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        BlockState material = level.getBlockEntity(pos) instanceof PanelBlockEntity panel ? panel.material() : null;
        return material == null ? super.getLightEmission(state, level, pos) : material.getLightEmission(level, pos);
    }
    *///?}

    /** The block state the console would copy from this item, or null if it can't be copied. */
    @Nullable
    public static BlockState acceptedMaterial(Level level, BlockPos pos, ItemStack stack, BlockHitResult hit) {
        if (stack.isEmpty())
            return null;
        return AllBlocks.COPYCAT_STEP.get().getAcceptedBlockState(level, pos, stack, hit.getDirection());
    }

    @Override
    protected boolean applyCasing(ItemStack stack, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockState material = acceptedMaterial(level, pos, stack, hit);
        if (material == null || !(level.getBlockEntity(pos) instanceof PanelBlockEntity panel))
            return false;
        if (level.isClientSide)
            return true;

        BlockState current = panel.material();
        if (current != null && current.is(material.getBlock())) {
            BlockState turned = turned(current);
            if (turned != null) {
                panel.turnMaterial(turned);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, .75f, .95f);
            }
            return true;
        }

        ItemStack previous = panel.setMaterial(material, stack);
        level.playSound(null, pos, material.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1, .75f);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            if (!previous.isEmpty())
                player.getInventory().placeItemBackInInventory(previous);
        }
        return true;
    }

    /** The next way round for a block that has one, as Create's copycats cycle them; null if it has none. */
    @Nullable
    private static BlockState turned(BlockState material) {
        if (material.hasProperty(BlockStateProperties.FACING))
            return material.cycle(BlockStateProperties.FACING);
        if (material.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
            return material.setValue(BlockStateProperties.HORIZONTAL_FACING,
                    material.getValue(BlockStateProperties.HORIZONTAL_FACING).getClockWise());
        if (material.hasProperty(BlockStateProperties.AXIS))
            return material.cycle(BlockStateProperties.AXIS);
        if (material.hasProperty(BlockStateProperties.HORIZONTAL_AXIS))
            return material.cycle(BlockStateProperties.HORIZONTAL_AXIS);
        if (material.hasProperty(BlockStateProperties.LIT))
            return material.cycle(BlockStateProperties.LIT);
        return null;
    }
}
