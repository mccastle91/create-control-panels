package dev.mccastle91.createcontrolling.panel;

import com.mojang.serialization.MapCodec;
import dev.mccastle91.createcontrolling.client.CCClientHooks;
import dev.mccastle91.createcontrolling.item.PanelBlueprintItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;

/**
 * A control console: a one-block sloped body that attaches to floors, walls
 * and ceilings lever-style, carrying player-designed components on its face.
 * Shape, casing and layout live on the {@link PanelBlockEntity}; the
 * blockstate holds placement plus which edges join an identical neighbour, so
 * the casing frame wraps a whole group (border connections, see the model).
 */
public class PanelBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {

    // Model-space edge connections (see ConsoleOrientation): edges that meet an
    // identical neighbour lose their frame; diagonals keep a frame pixel at the
    // inner corner of an L unless the diagonal neighbour is there too
    public static final BooleanProperty CONNECT_N = BooleanProperty.create("connect_n");
    public static final BooleanProperty CONNECT_S = BooleanProperty.create("connect_s");
    public static final BooleanProperty CONNECT_W = BooleanProperty.create("connect_w");
    public static final BooleanProperty CONNECT_E = BooleanProperty.create("connect_e");
    public static final BooleanProperty DIAG_NE = BooleanProperty.create("diag_ne");
    public static final BooleanProperty DIAG_NW = BooleanProperty.create("diag_nw");
    public static final BooleanProperty DIAG_SE = BooleanProperty.create("diag_se");
    public static final BooleanProperty DIAG_SW = BooleanProperty.create("diag_sw");

    private static final VoxelShape FALLBACK_SHAPE = Block.box(0, 0, 0, 16, 2, 16);

    public PanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACE, AttachFace.FLOOR)
                .setValue(FACING, Direction.NORTH)
                .setValue(CONNECT_N, false).setValue(CONNECT_S, false)
                .setValue(CONNECT_W, false).setValue(CONNECT_E, false)
                .setValue(DIAG_NE, false).setValue(DIAG_NW, false)
                .setValue(DIAG_SE, false).setValue(DIAG_SW, false));
    }

    /** The registered block entity type for consoles; set by registration. */
    protected BlockEntityType<? extends PanelBlockEntity> blockEntityType() {
        return CCTypes.PANEL_BLOCK_ENTITY.get();
    }

    //? if >=1.21 {
    @Override
    protected MapCodec<? extends PanelBlock> codec() {
        return simpleCodec(PanelBlock::new);
    }
    //?}

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, CONNECT_N, CONNECT_S, CONNECT_W, CONNECT_E, DIAG_NE, DIAG_NW, DIAG_SE, DIAG_SW);
    }

    // --- Shapes -------------------------------------------------------------

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return level.getBlockEntity(pos) instanceof PanelBlockEntity panel ? panel.selectionShape() : FALLBACK_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return level.getBlockEntity(pos) instanceof PanelBlockEntity panel ? panel.collisionShape() : FALLBACK_SHAPE;
    }

    // --- Border connections -------------------------------------------------

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : withConnections(state, context.getLevel(), context.getClickedPos());
    }

    /**
     * A console item carries its old console's design, shape included (drops
     * and pick-block keep it). A wall console always starts flat, whatever
     * the item remembers — the layout comes along, the slope doesn't.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (state.getValue(FACE) == AttachFace.WALL && level.getBlockEntity(pos) instanceof PanelBlockEntity panel
                && panel.setShape(ConsoleShape.FLAT))
            panel.markLayoutChanged();
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BlockState updated = super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        return updated.is(this) ? withConnections(updated, level, pos) : updated;
    }

    /**
     * Re-evaluates the connections of the console at {@code pos} and its
     * eight neighbours — after a change neighbours can't see through a block
     * update (shape, casing, or block entity data arriving after placement).
     */
    public static void refreshConnections(Level level, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos at = pos.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(at);
                    if (!(state.getBlock() instanceof PanelBlock block))
                        continue;
                    BlockState updated = block.withConnections(state, level, at);
                    if (updated != state)
                        level.setBlock(at, updated, Block.UPDATE_CLIENTS);
                }
    }

    private BlockState withConnections(BlockState state, BlockGetter level, BlockPos pos) {
        ConsoleOrientation orientation = ConsoleOrientation.of(state);
        return state
                .setValue(CONNECT_N, joins(state, orientation, level, pos, 0, -1))
                .setValue(CONNECT_S, joins(state, orientation, level, pos, 0, 1))
                .setValue(CONNECT_W, joins(state, orientation, level, pos, -1, 0))
                .setValue(CONNECT_E, joins(state, orientation, level, pos, 1, 0))
                .setValue(DIAG_NE, joins(state, orientation, level, pos, 1, -1))
                .setValue(DIAG_NW, joins(state, orientation, level, pos, -1, -1))
                .setValue(DIAG_SE, joins(state, orientation, level, pos, 1, 1))
                .setValue(DIAG_SW, joins(state, orientation, level, pos, -1, 1));
    }

    /**
     * Whether the console one step along model x/z joins this one: same
     * placement, shape and casing. Sloped consoles only join side by side —
     * front-to-back neighbours make a sawtooth, not one surface.
     */
    private boolean joins(BlockState state, ConsoleOrientation orientation, BlockGetter level, BlockPos pos, int mx, int mz) {
        Vec3 d = orientation.rotate(new Vec3(mx, 0, mz));
        BlockPos other = pos.offset((int) Math.round(d.x), (int) Math.round(d.y), (int) Math.round(d.z));
        BlockState neighbor = level.getBlockState(other);
        if (!neighbor.is(this) || neighbor.getValue(FACE) != state.getValue(FACE)
                || neighbor.getValue(FACING) != state.getValue(FACING))
            return false;
        ConsoleShape shape = shapeAt(level, pos, state);
        if (mz != 0 && !shape.isFlat())
            return false;
        return shape.equals(shapeAt(level, other, neighbor)) && casingAt(level, pos) == casingAt(level, other);
    }

    private static ConsoleShape shapeAt(BlockGetter level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof PanelBlockEntity panel)
            return panel.shape();
        return ConsoleShape.defaultFor(state.getValue(FACE));
    }

    private static ConsoleCasing casingAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PanelBlockEntity panel ? panel.casing() : ConsoleCasing.DEFAULT;
    }

    // --- Wrench and casing --------------------------------------------------

    /**
     * Sneak-wrench: steps the console through the angle presets; a wall
     * console runs through them with its top edge raised, then its bottom.
     */
    public InteractionResult cycleShapePreset(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof PanelBlockEntity panel))
            return InteractionResult.PASS;
        boolean bothWays = state.getValue(FACE) == AttachFace.WALL;
        if (!level.isClientSide && panel.setShape(panel.shape().nextPreset(bothWays)))
            panel.markLayoutChanged();
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Right-click with a Create casing re-dresses the console; the old casing comes back. */
    private boolean applyCasing(ItemStack stack, Level level, BlockPos pos, Player player) {
        ConsoleCasing casing = ConsoleCasing.byItemId(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (casing == null || !(level.getBlockEntity(pos) instanceof PanelBlockEntity panel))
            return false;
        if (panel.casing() == casing || level.isClientSide)
            return true;
        ConsoleCasing previous = panel.casing();
        panel.setCasing(casing);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            previous.item().ifPresent(item -> player.getInventory().placeItemBackInInventory(new ItemStack(item)));
        }
        return true;
    }

    // --- Drops ---------------------------------------------------------------

    // Broken consoles keep their design: the drop carries the block entity
    // data (shulker-box style), which vanilla BlockItem placement restores
    @Override
    public java.util.List<ItemStack> getDrops(BlockState state,
            net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        var drops = super.getDrops(state, params);
        if (params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY)
                instanceof PanelBlockEntity panel)
            for (ItemStack stack : drops)
                if (stack.is(asItem()))
                    panel.writeToItem(stack);
        return drops;
    }

    @Override
    public ItemStack getCloneItemStack(
            //? if >=1.21 {
            net.minecraft.world.level.LevelReader level,
            //?} else {
            /*BlockGetter level,
            *///?}
            BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (level.getBlockEntity(pos) instanceof PanelBlockEntity panel)
            panel.writeToItem(stack);
        return stack;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return blockEntityType().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != blockEntityType())
            return null;
        return (lvl, pos, st, be) -> ((PanelBlockEntity) be).serverTick();
    }

    // --- Interaction --------------------------------------------------------

    //? if >=1.21 {
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state,
            Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
        if (useSpecialItem(stack, level, pos, player))
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return interact(state, level, pos, player, hitResult);
    }
    //?} else {
    /*@Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
        if (useSpecialItem(player.getItemInHand(hand), level, pos, player))
            return InteractionResult.sidedSuccess(level.isClientSide);
        return interact(state, level, pos, player, hitResult);
    }
    *///?}

    /** Items that act on the console itself rather than on a component. */
    private boolean useSpecialItem(ItemStack stack, Level level, BlockPos pos, Player player) {
        if (stack.getItem() instanceof PanelBlueprintItem) {
            PanelBlueprintItem.useOnPanel(stack, level, pos, player);
            return true;
        }
        return applyCasing(stack, level, pos, player);
    }

    private InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        // Edit mode: sneak-use opens the designer (client screen)
        if (player.isShiftKeyDown()) {
            if (level.isClientSide && CCClientHooks.OPEN_DESIGNER != null)
                CCClientHooks.OPEN_DESIGNER.accept(pos);
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof PanelBlockEntity panel))
            return InteractionResult.PASS;

        // Play mode: the component under the crosshair acts, server-side
        Vec3 origin = Vec3.atLowerCornerOf(pos);
        Vec3 eye = player.getEyePosition().subtract(origin);
        Vec3 direction = hitResult.getLocation().subtract(origin).subtract(eye);
        Optional<ConsolePicker.Pick> pick = ConsolePicker.pick(state, panel.shape(), panel.getLayout(), eye, direction);
        if (pick.isEmpty())
            return InteractionResult.PASS;
        if (!level.isClientSide) {
            ConsolePicker.Pick hit = pick.get();
            panel.clickElement(hit.element().id(), hit.localX(), hit.localY(), false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
