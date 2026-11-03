package dev.mccastle91.createcontrolling.compat.create;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import dev.mccastle91.createcontrolling.panel.PanelBlock;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create-wrench integration: sneak-wrenching a console steps its angle
 * through the presets (flat, 15°, 30°, 45°; a wall console runs through them
 * with its top edge raised, then with its bottom edge raised). Anything else
 * falls through to Create's default sneak-wrench handling.
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

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        InteractionResult cycled = cycleShapePreset(state, context.getLevel(), context.getClickedPos());
        return cycled == InteractionResult.PASS
                ? IWrenchable.super.onSneakWrenched(state, context)
                : cycled;
    }
}
