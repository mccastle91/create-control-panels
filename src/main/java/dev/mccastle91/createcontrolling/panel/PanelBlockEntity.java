package dev.mccastle91.createcontrolling.panel;

import dev.mccastle91.createcontrolling.link.CCLink;
import dev.mccastle91.createcontrolling.link.LinkPort;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.util.McCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A console: its sloped shape, its casing, and the components on its face.
 * All state is server-authoritative and synced to clients whole.
 */
public class PanelBlockEntity extends BlockEntity {

    public static final String TAG_LAYOUT = "Layout";
    public static final String TAG_SHAPE = "Shape";
    public static final String TAG_CASING = "Casing";
    public static final String TAG_RUNTIME = "Runtime";
    public static final String TAG_RUNTIME_ID = "Id";
    public static final String TAG_RUNTIME_STRENGTH = "S";
    public static final String TAG_DISPLAY_LINES = "DisplayLines";
    public static final String TAG_DISPLAY_LINE = "L";
    public static final String TAG_DISPLAY_TEXT = "T";

    private PanelLayout layout = new PanelLayout();
    private ConsoleShape shape = ConsoleShape.DEFAULT;
    private ConsoleCasing casing = ConsoleCasing.DEFAULT;

    /** Live network connections, one per linkable element; server only. */
    private final Map<UUID, LinkPort> linkPorts = new HashMap<>();

    /** Text Display Links last sent, by line number. Never saved: links resend it within seconds of a load. */
    private final Map<Integer, Component> displayLines = new HashMap<>();

    public PanelBlockEntity(BlockEntityType<? extends PanelBlockEntity> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        shape = ConsoleShape.defaultFor(attachFace(state));
    }

    private static AttachFace attachFace(BlockState state) {
        return state.hasProperty(FaceAttachedHorizontalDirectionalBlock.FACE)
                ? state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE) : AttachFace.FLOOR;
    }

    public PanelLayout getLayout() {
        return layout;
    }

    public void setLayout(PanelLayout layout) {
        this.layout = layout;
        markLayoutChanged();
    }

    public ConsoleShape shape() {
        return shape;
    }

    /**
     * Sets the shape as this console's placement allows it (see
     * {@link ConsoleShape#forFace}). Returns true if the shape changed (caller syncs).
     */
    public boolean setShape(ConsoleShape newShape) {
        ConsoleShape target = newShape.forFace(attachFace(getBlockState()));
        if (target.equals(shape))
            return false;
        shape = target;
        invalidateShapes();
        refreshNeighbourConnections();
        return true;
    }

    public ConsoleCasing casing() {
        return casing;
    }

    public void setCasing(ConsoleCasing casing) {
        this.casing = casing;
        setChanged();
        syncToClients();
        refreshNeighbourConnections();
    }

    /**
     * Layout state is server-authoritative: call after any server-side
     * mutation to persist, resync clients, and re-key network connections
     * (element frequencies may have changed).
     */
    public void markLayoutChanged() {
        invalidateShapes();
        setChanged();
        syncToClients();
        rebuildLinkPorts();
    }

    /** The text on a line, or nothing if no Display Link has sent it any. */
    public Component displayLine(int line) {
        return displayLines.getOrDefault(line, Component.empty());
    }

    /** Stores a line's text; true if that changed it (the caller syncs once all are set). */
    public boolean setDisplayLine(int line, Component text) {
        if (text.getString().isEmpty())
            return displayLines.remove(line) != null;
        return !text.equals(displayLines.put(line, text));
    }

    public void syncDisplayLines() {
        syncToClients();
    }

    private void refreshNeighbourConnections() {
        if (level != null && !level.isClientSide)
            PanelBlock.refreshConnections(level, worldPosition);
    }

    // --- Shapes (cached; rebuilt when shape or layout change) ---------------

    private volatile net.minecraft.world.phys.shapes.VoxelShape selectionShape;
    private volatile net.minecraft.world.phys.shapes.VoxelShape collisionShape;

    private void invalidateShapes() {
        selectionShape = null;
        collisionShape = null;
    }

    public net.minecraft.world.phys.shapes.VoxelShape selectionShape() {
        var cached = selectionShape;
        if (cached == null)
            selectionShape = cached = ConsoleShapes.selection(getBlockState(), shape, layout);
        return cached;
    }

    public net.minecraft.world.phys.shapes.VoxelShape collisionShape() {
        var cached = collisionShape;
        if (cached == null)
            collisionShape = cached = ConsoleShapes.collision(getBlockState(), shape);
        return cached;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        invalidateShapes();
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // --- Link network lifecycle -------------------------------------------
    // Loader block entities call rebuild/unload from their load and
    // chunk-unload hooks; the first server tick doubles as a lazy fallback.

    private boolean portsInitialized;

    @Override
    public void setRemoved() {
        unloadLinkPorts();
        super.setRemoved();
    }

    public void unloadLinkPorts() {
        linkPorts.values().forEach(LinkPort::remove);
        linkPorts.clear();
        portsInitialized = false;
    }

    public void rebuildLinkPorts() {
        linkPorts.values().forEach(LinkPort::remove);
        linkPorts.clear();
        if (level == null || level.isClientSide || CCLink.PLATFORM == null)
            return;
        portsInitialized = true;
        for (PanelElement element : layout.elements()) {
            if (element.frequency().isEmpty())
                continue;
            if (!element.isTransmitter() && !element.isReceiver())
                continue;
            LinkPort port = CCLink.PLATFORM.createPort(level, worldPosition,
                    element.frequency(), element.isReceiver(),
                    element::transmittedStrength,
                    strength -> onStrengthReceived(element, strength),
                    () -> !isRemoved());
            linkPorts.put(element.id(), port);
        }
    }

    private void onStrengthReceived(PanelElement element, int strength) {
        if (element.receiveStrength(strength))
            syncToClients();
    }

    private void notifyStrengthChanged(UUID elementId) {
        LinkPort port = linkPorts.get(elementId);
        if (port != null)
            port.notifyStrengthChanged();
    }

    // --- Server-side interaction --------------------------------------------

    /**
     * A play-mode click at ({@code localX}, {@code localY}) px within the
     * element's footprint. Returns true if the element reacted.
     */
    public boolean clickElement(UUID elementId, float localX, float localY, boolean sneaking) {
        PanelElement element = layout.elementById(elementId).orElse(null);
        if (element == null || !element.takesClicks() || !element.click(localX, localY, sneaking))
            return false;
        onElementStateChanged(element);
        return true;
    }

    private void onElementStateChanged(PanelElement element) {
        notifyStrengthChanged(element.id());
        if (element.hasPersistentState())
            setChanged();
        syncToClients();
    }

    /** Lets elements run timers (button pulses); registered by PanelBlock server-side only. */
    public void serverTick() {
        if (!portsInitialized)
            rebuildLinkPorts();
        boolean anyChanged = false;
        for (PanelElement element : layout.elements()) {
            if (element.tick()) {
                notifyStrengthChanged(element.id());
                anyChanged = true;
            }
        }
        if (anyChanged)
            syncToClients();
    }

    // --- Persistence & sync ------------------------------------------------

    //? if >=1.21 {
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeData(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readData(tag, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return buildUpdateTag(saveWithoutMetadata(registries), registries);
    }

    /** Stores this panel's design on a dropped or picked item (restored on placement). */
    public void writeToItem(net.minecraft.world.item.ItemStack stack) {
        if (level != null)
            saveToItem(stack, level.registryAccess());
    }
    //?} else {
    /*@Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeData(tag, null);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        readData(tag, null);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return buildUpdateTag(saveWithoutMetadata(), null);
    }

    public void writeToItem(net.minecraft.world.item.ItemStack stack) {
        saveToItem(stack);
    }
    *///?}

    private void writeData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(TAG_LAYOUT, layout.save(registries));
        tag.put(TAG_SHAPE, shape.save());
        tag.putString(TAG_CASING, casing.id());
    }

    private void readData(CompoundTag tag, HolderLookup.Provider registries) {
        ConsoleShape previousShape = shape;
        ConsoleCasing previousCasing = casing;
        if (tag.contains(TAG_SHAPE))
            shape = ConsoleShape.load(tag.getCompound(TAG_SHAPE)).forFace(attachFace(getBlockState()));
        if (tag.contains(TAG_CASING))
            casing = ConsoleCasing.byId(tag.getString(TAG_CASING));
        if (tag.contains(TAG_LAYOUT))
            layout = PanelLayout.load(tag.getCompound(TAG_LAYOUT), registries);
        invalidateShapes();
        if (level != null && !level.isClientSide) {
            // Placing a console item with stored data loads after onLoad ran
            // with an empty layout — re-key the network, and neighbours can
            // now see this console's real shape and casing
            rebuildLinkPorts();
            refreshNeighbourConnections();
        } else if (level != null && (!shape.equals(previousShape) || casing != previousCasing)) {
            // The body is generated from this data: refresh it and re-mesh
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
        }
        // Transient element state piggybacks on update tags (never on disk)
        if (tag.contains(TAG_RUNTIME)) {
            ListTag runtime = tag.getList(TAG_RUNTIME, Tag.TAG_COMPOUND);
            for (int i = 0; i < runtime.size(); i++) {
                CompoundTag entry = runtime.getCompound(i);
                layout.elementById(entry.getUUID(TAG_RUNTIME_ID))
                        .ifPresent(element -> element.applyDisplayStrength(entry.getInt(TAG_RUNTIME_STRENGTH)));
            }
        }
        displayLines.clear();
        ListTag lines = tag.getList(TAG_DISPLAY_LINES, Tag.TAG_COMPOUND);
        for (int i = 0; i < lines.size(); i++) {
            CompoundTag entry = lines.getCompound(i);
            displayLines.put(entry.getInt(TAG_DISPLAY_LINE),
                    McCompat.componentFromJson(entry.getString(TAG_DISPLAY_TEXT), registries));
        }
    }

    // Full layout goes to clients with chunk data / block updates. Panels are
    // capped at MAX_ELEMENTS, so whole-layout sync stays small; revisit with
    // targeted packets only if profiling ever says otherwise.
    private CompoundTag buildUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag runtime = new ListTag();
        for (PanelElement element : layout.elements()) {
            int strength = element.displayStrength();
            if (strength == 0)
                continue;
            CompoundTag entry = new CompoundTag();
            entry.putUUID(TAG_RUNTIME_ID, element.id());
            entry.putInt(TAG_RUNTIME_STRENGTH, strength);
            runtime.add(entry);
        }
        if (!runtime.isEmpty())
            tag.put(TAG_RUNTIME, runtime);
        ListTag lines = new ListTag();
        displayLines.forEach((line, text) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt(TAG_DISPLAY_LINE, line);
            entry.putString(TAG_DISPLAY_TEXT, McCompat.componentToJson(text, registries));
            lines.add(entry);
        });
        if (!lines.isEmpty())
            tag.put(TAG_DISPLAY_LINES, lines);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
