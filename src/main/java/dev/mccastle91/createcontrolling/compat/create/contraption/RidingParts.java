package dev.mccastle91.createcontrolling.compat.create.contraption;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsMovement;
import com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionDisableActorPacket;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.elevator.ElevatorColumn;
import com.simibubi.create.content.contraptions.elevator.ElevatorContactBlock;
import com.simibubi.create.content.contraptions.elevator.ElevatorContraption;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.PanelLayout;
import dev.mccastle91.createcontrolling.panel.element.ActorSwitchElement;
import dev.mccastle91.createcontrolling.panel.element.FloorButtonElement;
import dev.mccastle91.createcontrolling.panel.element.FloorReadoutElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.IntAttached;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.apache.commons.lang3.tuple.MutablePair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The parts of a console that act on the contraption it rides, where a part
 * on a frequency acts on the link network: the Actor Switch, which does what
 * Create's Contraption Controls block does on any contraption, and the Floor
 * Button and Floor Readout, which do what it does on an elevator. Server
 * side; one per {@link MovingConsole}, ticked with it.
 *
 * <p>Nothing here keeps a state of its own to draw from. Which actors are
 * stopped is the contraption's own list, the one Create's block edits and
 * sends to clients, so a switch and a Contraption Controls block on the same
 * contraption show the same thing; where the cabin is and is going is the
 * elevator's column.
 */
final class RidingParts {

    /** What each actor switch asked for at the last tick: a change since then is the switch's own doing. */
    private final Map<UUID, Boolean> asked = new HashMap<>();

    void tick(MovementContext context, PanelBlockEntity console) {
        AbstractContraptionEntity entity = context.contraption.entity;
        if (entity == null)
            return;
        boolean floors = false;
        for (PanelElement element : console.getLayout().elements()) {
            if (element instanceof ActorSwitchElement actors)
                reconcile(entity, console, actors);
            else if (element instanceof FloorButtonElement || element instanceof FloorReadoutElement)
                floors = true;
        }
        if (floors)
            showFloors(context, entity, console);
    }

    // --- Actors ------------------------------------------------------------------

    /**
     * The filters of the switches that stop their actors, read from a
     * console's saved data. Asked while the contraption assembles, so that
     * no actor gets a first tick it should not have had.
     */
    static void stopAtAssembly(MovementContext context) {
        CompoundTag data = context.blockEntityData;
        if (data == null || !data.contains(PanelBlockEntity.TAG_LAYOUT))
            return;
        PanelLayout layout = PanelLayout.load(data.getCompound(PanelBlockEntity.TAG_LAYOUT), context.world.registryAccess());
        for (PanelElement element : layout.elements()) {
            if (element instanceof ActorSwitchElement actors && actors.stoppedWhenSaved()
                    && !context.contraption.isActorTypeDisabled(actors.filter()))
                context.contraption.getDisabledActors().add(actors.filter().copy());
        }
    }

    /**
     * Brings a switch and the contraption's list into step. A switch that was
     * just thrown, or whose signal just came or went, has its way; so does one
     * whose signal is holding the actors off. Otherwise a difference means
     * someone switched the actors another way (a Contraption Controls block,
     * a switch on another console), and the lever follows.
     */
    private void reconcile(AbstractContraptionEntity entity, PanelBlockEntity console, ActorSwitchElement part) {
        Contraption contraption = entity.getContraption();
        boolean want = part.running();
        Boolean before = asked.put(part.id(), want);
        if (want != stopped(contraption, part.filter()))
            return;
        if (before == null || before != want || part.held()) {
            set(entity, part.filter(), want);
            return;
        }
        if (part.setOn(!want)) {
            asked.put(part.id(), part.running());
            console.elementChanged(part);
        }
    }

    /** Whether the actors a filter names are stopped: under their own entry, or under the entry for all. */
    static boolean stopped(Contraption contraption, ItemStack filter) {
        return contraption.isActorTypeDisabled(filter)
                || !filter.isEmpty() && contraption.isActorTypeDisabled(ItemStack.EMPTY);
    }

    /**
     * Lets the actors a filter names run, or stops them: the edit Create's
     * Contraption Controls block makes to the contraption's list on a click,
     * with a state to reach in place of a toggle, sent to clients in Create's
     * own packet. Doors are switched on every carriage of a train, as Create does.
     */
    static void set(AbstractContraptionEntity entity, ItemStack filter, boolean run) {
        Contraption contraption = entity.getContraption();
        List<ItemStack> list = contraption.getDisabledActors();
        if (run) {
            boolean allWereStopped = list.removeIf(ItemStack::isEmpty);
            list.removeIf(present -> ContraptionControlsMovement.isSameFilter(present, filter));
            if (filter.isEmpty()) {
                list.clear();
            } else if (allWereStopped) {
                // only these start again: every other kind keeps its stop, under an entry of its own
                for (MutablePair<StructureBlockInfo, MovementContext> actor : contraption.getActors()) {
                    MovementBehaviour behaviour = MovementBehaviour.REGISTRY.get(actor.left.state());
                    ItemStack kind = behaviour == null || actor.right == null ? null : behaviour.canBeDisabledVia(actor.right);
                    if (kind == null || ContraptionControlsMovement.isSameFilter(kind, filter)
                            || contraption.isActorTypeDisabled(kind))
                        continue;
                    list.add(kind);
                    send(entity, kind, false);
                }
            }
        } else {
            if (filter.isEmpty())
                list.clear();
            else
                list.removeIf(present -> ContraptionControlsMovement.isSameFilter(present, filter));
            list.add(filter.copy());
        }
        contraption.setActorsActive(filter, run);
        send(entity, filter, run);

        if (entity instanceof CarriageContraptionEntity carriage && filter.is(ItemTags.DOORS)
                && carriage.getCarriage() != null && carriage.getCarriage().train != null) {
            for (Carriage other : carriage.getCarriage().train.carriages) {
                CarriageContraptionEntity there = other.anyAvailableEntity();
                if (there == null || there == entity || there.getContraption() == null)
                    continue;
                List<ItemStack> theirs = there.getContraption().getDisabledActors();
                theirs.removeIf(present -> ContraptionControlsMovement.isSameFilter(present, filter));
                if (!run)
                    theirs.add(filter.copy());
                there.getContraption().setActorsActive(filter, run);
                send(there, filter, run);
            }
        }
    }

    private static void send(AbstractContraptionEntity entity, ItemStack filter, boolean run) {
        ContraptionDisableActorPacket packet = new ContraptionDisableActorPacket(entity.getId(), filter, run);
        //? if neoforge {
        net.createmod.catnip.platform.CatnipServices.NETWORK.sendToClientsTrackingEntity(entity, packet);
        //?} else {
        /*com.simibubi.create.AllPackets.getChannel()
                .send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> entity), packet);
        *///?}
    }

    // --- Elevator floors -----------------------------------------------------------

    /** Lights the button of the floor the cabin is called to, and names the floor it is nearest to. */
    private void showFloors(MovementContext context, AbstractContraptionEntity entity, PanelBlockEntity console) {
        int target = -1;
        String here = "";
        if (context.contraption instanceof ElevatorContraption elevator) {
            List<IntAttached<Couple<String>>> floors = elevator.namesList;
            Integer targetY = elevator.getCurrentTargetY(context.world);
            // the height of the cabin's own contact, which is level with a floor's contact when it stops there
            int cabinY = (int) Math.round(entity.getY()) + elevator.getContactYOffset();
            int nearest = Integer.MAX_VALUE;
            for (int i = 0; i < floors.size(); i++) {
                int y = floors.get(i).getFirst();
                if (targetY != null && y == targetY)
                    target = i;
                if (Math.abs(y - cabinY) < nearest) {
                    nearest = Math.abs(y - cabinY);
                    here = floors.get(i).getSecond().getFirst();
                }
            }
        }
        boolean changed = false;
        for (PanelElement element : console.getLayout().elements()) {
            if (element instanceof FloorButtonElement button)
                changed |= button.setLit(button.floor() - 1 == target);
            else if (element instanceof FloorReadoutElement readout)
                changed |= readout.setText(here);
        }
        if (changed)
            console.syncRuntime();
    }

    /**
     * Sends the cabin to a floor, 1 for the lowest: what the server does when
     * a player picks a floor on a Contraption Controls block. Nothing happens
     * off an elevator, or for a floor the shaft doesn't have or the cabin can't reach.
     */
    static void callFloor(MovementContext context, int floor) {
        if (!(context.contraption instanceof ElevatorContraption elevator))
            return;
        List<IntAttached<Couple<String>>> floors = elevator.namesList;
        if (floor < 1 || floor > floors.size())
            return;
        int y = floors.get(floor - 1).getFirst();
        Level level = context.world;
        ElevatorColumn column = ElevatorColumn.get(level, elevator.getGlobalColumn());
        if (column == null || elevator.isTargetUnreachable(y))
            return;
        BlockPos contact = column.contactAt(y);
        BlockState state = level.getBlockState(contact);
        if (state.getBlock() instanceof ElevatorContactBlock block)
            block.callToContactAndUpdate(column, state, level, contact, false);
    }
}
