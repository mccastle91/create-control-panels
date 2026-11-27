package dev.mccastle91.createcontrolling.compat.create.contraption;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.mccastle91.createcontrolling.network.CCNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
//? if neoforge {
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
//?} else {
/*import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
*///?}

/**
 * Makes a console an actor of the contraption carrying it, which is how
 * Create gives a moved block a tick: the tick keeps a {@link MovingConsole}
 * running on the server. Clients need nothing here; they draw the block
 * entity Create builds for them and are sent its state.
 */
public final class ConsoleMovement implements MovementBehaviour {

    @Override
    public void tick(MovementContext context) {
        MovingConsole moving = MovingConsole.of(context);
        if (moving != null)
            moving.tick();
    }

    @Override
    public void visitNewPosition(MovementContext context, BlockPos pos) {
        if (context.temporaryData instanceof MovingConsole moving)
            moving.console().linkPositionChanged();
    }

    @Override
    public void stopMoving(MovementContext context) {
        if (context.temporaryData instanceof MovingConsole moving)
            moving.close();
    }

    /** Contraption Controls switch actors off by item; a console is not something to switch off. */
    @Override
    public ItemStack canBeDisabledVia(MovementContext context) {
        return null;
    }

    @Override
    public boolean mustTickWhileDisabled() {
        return true;
    }

    /**
     * A player who comes to see a contraption is sent its blocks as they
     * were when it was assembled; its consoles follow as they are now.
     */
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof AbstractContraptionEntity entity && event.getEntity() instanceof ServerPlayer player)
            MovingConsole.forEachOn(entity, true, moving -> CCNetwork.sendToPlayer(player, moving.syncPayload(entity)));
    }

    /** A contraption unloaded with its chunk is never told to stop: its consoles leave the link network here. */
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof AbstractContraptionEntity entity)
            MovingConsole.forEachOn(entity, false, MovingConsole::close);
    }
}
