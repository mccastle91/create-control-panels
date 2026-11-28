package dev.mccastle91.createcontrolling.compat.create.contraption;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import dev.mccastle91.createcontrolling.network.CCNetwork;
import dev.mccastle91.createcontrolling.network.ContraptionSyncPayload;
import dev.mccastle91.createcontrolling.panel.ConsoleHost;
import dev.mccastle91.createcontrolling.panel.PanelBlock;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.element.ActorSwitchElement;
import dev.mccastle91.createcontrolling.panel.element.AnalogElement;
import dev.mccastle91.createcontrolling.panel.element.FloorButtonElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.util.McCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.MutablePair;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A console riding a contraption, on the server. Create keeps a contraption's
 * blocks as plain data, so the console's block entity is gone for the ride;
 * this keeps one running on the side, built from that data, so its parts stay
 * on the Redstone Link network, its buttons time out and it can be clicked.
 * It is the very class a placed console uses, told through {@link ConsoleHost}
 * where it is and how to reach its viewers. Every change is written back into
 * the contraption's data at once, so a save or a disassembly carries the
 * console as it is now.
 *
 * <p>One lives in the {@link MovementContext#temporaryData} of the console's
 * actor, made the first time it is needed and closed when the contraption
 * stops or leaves the world.
 */
public final class MovingConsole implements ConsoleHost {

    /** How far beyond a player's reach a click still counts: the cabin moves between the click and its arrival. */
    private static final double REACH_SLACK = 4;

    private final MovementContext context;
    private final PanelBlockEntity console;
    /** The parts that act on the contraption itself: its actors, an elevator's floors. */
    private final RidingParts parts = new RidingParts();
    private boolean syncDue;
    private boolean closed;

    private MovingConsole(MovementContext context, PanelBlockEntity console) {
        this.context = context;
        this.console = console;
        console.setHost(this);
        CompoundTag data = data();
        if (data != null) {
            //? if >=1.21 {
            console.loadWithComponents(data, context.world.registryAccess());
            //?} else {
            /*console.load(data);
            *///?}
        }
        console.setLevel(context.world);
        console.rebuildLinkPorts();
    }

    /** The running console of this actor, started if need be; null on a client or for anything but a console. */
    @Nullable
    public static MovingConsole of(@Nullable MovementContext context) {
        if (context == null || context.world == null || context.world.isClientSide)
            return null;
        if (context.temporaryData instanceof MovingConsole moving && !moving.closed)
            return moving;
        if (!(context.state.getBlock() instanceof PanelBlock block)
                || !(block.newBlockEntity(context.localPos, context.state) instanceof PanelBlockEntity console))
            return null;
        MovingConsole moving = new MovingConsole(context, console);
        context.temporaryData = moving;
        return moving;
    }

    /** The console at {@code localPos} of a contraption, started if need be. */
    @Nullable
    public static MovingConsole at(AbstractContraptionEntity entity, BlockPos localPos) {
        Contraption contraption = entity.getContraption();
        if (contraption == null)
            return null;
        MutablePair<StructureBlockInfo, MovementContext> actor = contraption.getActorAt(localPos);
        return actor == null ? null : of(actor.right);
    }

    /** Every console on a contraption; with {@code start}, those not running yet are started. */
    public static void forEachOn(AbstractContraptionEntity entity, boolean start, Consumer<MovingConsole> action) {
        Contraption contraption = entity.getContraption();
        if (contraption == null)
            return;
        for (MutablePair<StructureBlockInfo, MovementContext> actor : List.copyOf(contraption.getActors())) {
            if (actor.right == null || !(actor.left.state().getBlock() instanceof PanelBlock))
                continue;
            MovingConsole moving = start ? of(actor.right)
                    : actor.right.temporaryData instanceof MovingConsole running ? running : null;
            if (moving != null && !moving.closed)
                action.accept(moving);
        }
    }

    public PanelBlockEntity console() {
        return console;
    }

    /** Once a tick: button pulses run out, and viewers get what changed since the last tick. */
    void tick() {
        console.serverTick();
        parts.tick(context, console);
        if (!syncDue)
            return;
        syncDue = false;
        AbstractContraptionEntity entity = context.contraption.entity;
        if (entity != null)
            CCNetwork.sendToTracking(entity, syncPayload(entity));
    }

    /** Takes the console's parts off the link network; the next tick of a contraption still moving starts it again. */
    void close() {
        if (closed)
            return;
        closed = true;
        console.unloadLinkPorts();
        if (context.temporaryData == this)
            context.temporaryData = null;
    }

    /** What a client needs to draw the console as it is now. */
    public ContraptionSyncPayload syncPayload(AbstractContraptionEntity entity) {
        //? if >=1.21 {
        CompoundTag tag = console.getUpdateTag(context.world.registryAccess());
        //?} else {
        /*CompoundTag tag = console.getUpdateTag();
        *///?}
        // contraptions assembled before Create 6.0.9 rebuild their client block entities from full data, type id included
        tag.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(console.getType()).toString());
        return new ContraptionSyncPayload(entity.getId(), context.localPos, tag);
    }

    /**
     * A player's click on a part, as their client saw it under the crosshair
     * (the server's idea of where a moving cabin is lags the picture, so the
     * client picks). Everything in it is checked: reach, the part, its zone.
     */
    public static void click(Player player, int entityId, BlockPos localPos, UUID elementId, int zone,
                             float localX, float localY, boolean sneaking) {
        MovingConsole moving = reached(player, entityId, localPos);
        if (moving == null || Float.isNaN(localX) || Float.isNaN(localY))
            return;
        PanelElement element = moving.console.getLayout().elementById(elementId).orElse(null);
        if (element == null || zone < 0 || zone >= element.pickBoxes().length)
            return;
        // on an elevator an actor switch with no filter picks floors: its press calls one and switches nothing
        if (element instanceof ActorSwitchElement selector && RidingParts.selectsFloors(moving.context, selector)) {
            moving.parts.pressFloor(moving.context, moving.console, selector);
            return;
        }
        boolean worked = moving.console.clickElement(elementId, zone,
                Math.max(0, Math.min(element.width(), localX)), Math.max(0, Math.min(element.depth(), localY)), sneaking);
        if (worked && element instanceof FloorButtonElement button)
            RidingParts.callFloor(moving.context, button.floor());
    }

    /**
     * A knob, slider or analog lever set by hand: a drag brings it to
     * {@code value}; with {@code relative} it is a sneak-scroll's one step up
     * or down. The part's own limits apply either way.
     */
    public static void setValue(Player player, int entityId, BlockPos localPos, UUID elementId, int value, boolean relative) {
        MovingConsole moving = reached(player, entityId, localPos);
        if (moving == null)
            return;
        PanelElement element = moving.console.getLayout().elementById(elementId).orElse(null);
        if (relative && element instanceof ActorSwitchElement selector && RidingParts.selectsFloors(moving.context, selector)) {
            moving.parts.scrollFloor(moving.context, moving.console, selector, value);
            return;
        }
        if (!(element instanceof AnalogElement analog))
            return;
        if (relative)
            moving.console.setAnalogValue(elementId, analog.value() + Integer.signum(value));
        else
            moving.console.setAnalogValue(elementId, value, player); // they hear each step from their own client
    }

    /** The running console a player says they worked, if they could have: it exists and is within reach. */
    @Nullable
    private static MovingConsole reached(Player player, int entityId, BlockPos localPos) {
        if (player.isSpectator()
                || !(player.level().getEntity(entityId) instanceof AbstractContraptionEntity entity)
                || entity.getContraption() == null)
            return null;
        Vec3 at = entity.toGlobalVector(Vec3.atCenterOf(localPos), 1);
        double reach = McCompat.blockReach(player) + REACH_SLACK;
        return player.getEyePosition().distanceToSqr(at) > reach * reach ? null : at(entity, localPos);
    }

    /** The contraption's own copy of the console's data: what it saves, and what it places on disassembly. */
    @Nullable
    private CompoundTag data() {
        StructureBlockInfo info = context.contraption.getBlocks().get(context.localPos);
        return info != null && info.nbt() != null ? info.nbt() : context.blockEntityData;
    }

    // --- ConsoleHost ---------------------------------------------------------

    @Override
    public BlockPos position() {
        Vec3 at = context.position;
        AbstractContraptionEntity entity = context.contraption.entity;
        if (at == null && entity != null)
            at = entity.toGlobalVector(Vec3.atCenterOf(context.localPos), 1);
        // before the contraption has an entity or a first tick: where the console stood when it was picked up
        return at == null ? context.contraption.anchor.offset(context.localPos) : BlockPos.containing(at);
    }

    @Override
    public void sync() {
        syncDue = true;
    }

    @Override
    public void changed() {
        CompoundTag data = data();
        if (data == null)
            return;
        //? if >=1.21 {
        CompoundTag fresh = console.saveWithFullMetadata(context.world.registryAccess());
        //?} else {
        /*CompoundTag fresh = console.saveWithFullMetadata();
        *///?}
        // Create keeps a block's data without its position
        fresh.remove("x");
        fresh.remove("y");
        fresh.remove("z");
        for (String key : List.copyOf(data.getAllKeys()))
            data.remove(key);
        data.merge(fresh);
    }

    @Override
    public boolean alive() {
        AbstractContraptionEntity entity = context.contraption.entity;
        return !closed && (entity == null || !entity.isRemoved());
    }
}
