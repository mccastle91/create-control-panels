package dev.mccastle91.createcontrolling.compat.create.contraption;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/**
 * Lets a console on a contraption be clicked. Create finds the block under
 * the crosshair on the client and asks here whether the click did anything;
 * it tells neither side where on the block the click landed, so the client
 * works out the part itself and sends that in a packet of our own (see
 * {@code client/ContraptionConsoles} and {@link MovingConsole#click}).
 */
public final class ConsoleMovingInteraction extends MovingInteractionBehaviour {

    /** The client's half; set by the client's init, null on a dedicated server. */
    public interface ClientClick {
        /** Returns true if the click pressed a part of the console at {@code localPos}. */
        boolean click(AbstractContraptionEntity entity, BlockPos localPos, InteractionHand hand);
    }

    public static ClientClick CLIENT_CLICK;

    @Override
    public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos localPos,
                                           AbstractContraptionEntity entity) {
        // Create's own packet reaches the server only after the client said yes; the part arrives in ours
        if (!entity.level().isClientSide)
            return true;
        return CLIENT_CLICK != null && CLIENT_CLICK.click(entity, localPos, hand);
    }
}
