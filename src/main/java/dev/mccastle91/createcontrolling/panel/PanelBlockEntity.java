package dev.mccastle91.createcontrolling.panel;

import dev.mccastle91.createcontrolling.link.CCLink;
import dev.mccastle91.createcontrolling.link.LinkPort;
import dev.mccastle91.createcontrolling.panel.element.AnalogElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.panel.element.PartSound;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A console: its sloped shape, its casing, and the components on its face.
 * All state is server-authoritative and synced to clients whole.
 */
public class PanelBlockEntity extends BlockEntity {

    public static final String TAG_LAYOUT = "Layout";
    public static final String TAG_SHAPE = "Shape";
    public static final String TAG_CASING = "Casing";
    public static final String TAG_PLATE = "Plate";
    public static final String TAG_MATERIAL = "Material";
    public static final String TAG_MATERIAL_ITEM = "MaterialItem";
    public static final String TAG_RUNTIME = "Runtime";
    public static final String TAG_RUNTIME_TEXT = "Text";
    public static final String TAG_RUNTIME_ID = "Id";
    public static final String TAG_RUNTIME_STRENGTH = "S";
    public static final String TAG_DISPLAY_LINES = "DisplayLines";
    public static final String TAG_DISPLAY_LINE = "L";
    public static final String TAG_DISPLAY_TEXT = "T";
    public static final String TAG_NAME = "Name";
    public static final int MAX_NAME_LENGTH = 32;

    private PanelLayout layout = new PanelLayout();
    /** What computers can call this console by; empty when it has none. Not copied by blueprints. */
    private String name = "";
    private ConsoleShape shape = ConsoleShape.DEFAULT;
    private ConsoleCasing casing = ConsoleCasing.DEFAULT;
    /** The dye the face plate is painted with, or null for the standard plate. */
    @org.jetbrains.annotations.Nullable
    private net.minecraft.world.item.DyeColor plateColor;
    /** The block a copycat console looks like, or null: only the copycat console block sets one. */
    @org.jetbrains.annotations.Nullable
    private BlockState material;
    /** The item that block was applied with; it comes back when the block is swapped or removed. */
    private net.minecraft.world.item.ItemStack materialItem = net.minecraft.world.item.ItemStack.EMPTY;

    /** Live network connections, one per linkable element; server only. */
    private final Map<UUID, LinkPort> linkPorts = new HashMap<>();

    /** What carries this console when it is not a block in the world (a contraption); null for a placed console. */
    @org.jetbrains.annotations.Nullable
    private ConsoleHost host;

    /** Text Display Links last sent, by line number. Saved with the console, but not on its item. */
    private final Map<Integer, Component> displayLines = new HashMap<>();

    public PanelBlockEntity(BlockEntityType<? extends PanelBlockEntity> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        shape = ConsoleShape.defaultFor(attachFace(state));
        ConsoleCasing fixed = fixedCasing();
        if (fixed != null)
            casing = fixed;
    }

    @org.jetbrains.annotations.Nullable
    private ConsoleCasing fixedCasing() {
        return getBlockState().getBlock() instanceof PanelBlock block ? block.fixedCasing() : null;
    }

    private static AttachFace attachFace(BlockState state) {
        return state.hasProperty(FaceAttachedHorizontalDirectionalBlock.FACE)
                ? state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE) : AttachFace.FLOOR;
    }

    public PanelLayout getLayout() {
        return layout;
    }

    /**
     * Hands this console to a host: from then on it never touches the world
     * at its own position (which is not a place in the world), and asks the
     * host instead. Set before the console is given its data and level.
     */
    public void setHost(ConsoleHost host) {
        this.host = host;
    }

    /** Where the console is in the world: its own position, or wherever its host has carried it. */
    private BlockPos placeInWorld() {
        return host != null ? host.position() : worldPosition;
    }

    private boolean linkAlive() {
        return !isRemoved() && (host == null || host.alive());
    }

    @Override
    public void setChanged() {
        if (host == null)
            super.setChanged();
        else if (level != null && !level.isClientSide)
            host.changed();
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

    public String name() {
        return name;
    }

    /** Sets the console's name (cleaned, see {@link #cleanName}); true if it changed. */
    public boolean setName(String requested) {
        String clean = cleanName(requested);
        if (clean.equals(name))
            return false;
        name = clean;
        setChanged();
        syncToClients();
        return true;
    }

    /** Printable characters only, no formatting codes, trimmed, at most {@link #MAX_NAME_LENGTH}. */
    public static String cleanName(String requested) {
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < requested.length(); i++) {
            char c = requested.charAt(i);
            if (c == '§')
                i++; // a formatting code: the mark and the letter after it
            else if (c >= ' ' && c != 0x7F)
                clean.append(c);
        }
        String trimmed = clean.toString().trim();
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH).trim() : trimmed;
    }

    /** The dye the face plate is painted with, or null for the standard plate. */
    @org.jetbrains.annotations.Nullable
    public net.minecraft.world.item.DyeColor plateColor() {
        return plateColor;
    }

    /**
     * Paints the face plate. Gray is the standard plate's own colour, so gray
     * dye (like null) puts the standard plate back. Returns true if the plate changed.
     */
    public boolean setPlateColor(@org.jetbrains.annotations.Nullable net.minecraft.world.item.DyeColor color) {
        net.minecraft.world.item.DyeColor plate = color == net.minecraft.world.item.DyeColor.GRAY ? null : color;
        if (plate == plateColor)
            return false;
        plateColor = plate;
        setChanged();
        syncToClients();
        return true;
    }

    public void setCasing(ConsoleCasing casing) {
        this.casing = casing;
        setChanged();
        syncToClients();
        refreshNeighbourConnections();
    }

    /** The block this console looks like, or null if it wears its casing. */
    @org.jetbrains.annotations.Nullable
    public BlockState material() {
        return material;
    }

    /** The item the copied block was applied with; empty if there is none. */
    public net.minecraft.world.item.ItemStack materialItem() {
        return materialItem;
    }

    /**
     * Makes the console look like {@code material}, keeping {@code item} (one
     * of it) to hand back later. Returns the item kept for the block before, which
     * the caller owes the player.
     */
    public net.minecraft.world.item.ItemStack setMaterial(BlockState material, net.minecraft.world.item.ItemStack item) {
        net.minecraft.world.item.ItemStack previous = materialItem;
        this.material = material;
        this.materialItem = item.copyWithCount(1);
        materialChanged();
        return previous;
    }

    /** Turns the copied block to another of its states (the axis of a log); the item stays. */
    public void turnMaterial(BlockState material) {
        this.material = material;
        materialChanged();
    }

    /** Back to the bare console. Returns the item kept for the block, which the caller owes the player. */
    public net.minecraft.world.item.ItemStack clearMaterial() {
        net.minecraft.world.item.ItemStack previous = materialItem;
        material = null;
        materialItem = net.minecraft.world.item.ItemStack.EMPTY;
        materialChanged();
        return previous;
    }

    private void materialChanged() {
        setChanged();
        syncToClients();
        refreshNeighbourConnections();
        updateMaterialLight();
    }

    /**
     * Has the world light this console as the block it copies is lit: a
     * console copying glowstone shines like glowstone. Called on both sides
     * whenever the copied block changes, and when the console loads. A
     * console that copies nothing gives off no light.
     */
    public void updateMaterialLight() {
        if (level == null || host != null || fixedCasing() != ConsoleCasing.COPYCAT)
            return;
        //? if neoforge {
        var lights = level.getAuxLightManager(worldPosition);
        if (lights != null)
            lights.setLightAt(worldPosition, material == null ? 0 : material.getLightEmission(level, worldPosition));
        //?} else {
        /*level.getChunkSource().getLightEngine().checkBlock(worldPosition);
        *///?}
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
        for (PanelElement element : layout.elements()) {
            playSound(element, null); // a part worked through an edit op (a scrolled knob)
            signalMayHaveChanged(element);
        }
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
        setChanged();
        syncToClients();
    }

    private void refreshNeighbourConnections() {
        if (level != null && !level.isClientSide && host == null)
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
        if (level == null || level.isClientSide)
            return;
        if (host != null)
            host.sync();
        else
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
        drivePorts.values().forEach(LinkPort::remove);
        drivePorts.clear();
        portsInitialized = false;
    }

    public void rebuildLinkPorts() {
        java.util.Set<UUID> wasLinked = new java.util.HashSet<>(linkPorts.keySet());
        linkPorts.values().forEach(LinkPort::remove);
        linkPorts.clear();
        drivePorts.values().forEach(LinkPort::remove);
        drivePorts.clear();
        if (level == null || level.isClientSide || CCLink.PLATFORM == null)
            return;
        portsInitialized = true;
        // Signals a computer is sending follow their component's frequency,
        // and end with a component that was removed or unlinked
        drivenStrengths.keySet().removeIf(id -> layout.elementById(id)
                .filter(element -> element.isReceiver() && !element.frequency().isEmpty()).isEmpty());
        drivenStrengths.keySet().forEach(id -> openDrivePort(layout.elementById(id).orElseThrow()));
        for (PanelElement element : layout.elements()) {
            if (element.frequency().isEmpty())
                continue;
            if (!element.isTransmitter() && !element.isReceiver())
                continue;
            LinkPort port = CCLink.PLATFORM.createPort(level, this::placeInWorld,
                    element.frequency(), element.isReceiver(),
                    element::transmittedStrength,
                    strength -> onStrengthReceived(element, strength),
                    this::linkAlive);
            linkPorts.put(element.id(), port);
        }
        // A receiver just unlinked forgets the network's last value: from now
        // on only a computer sets what it shows
        boolean anyReset = false;
        for (PanelElement element : layout.elements()) {
            if (element.isReceiver() && element.frequency().isEmpty() && wasLinked.contains(element.id())
                    && element.receiveStrength(0)) {
                signalMayHaveChanged(element);
                anyReset = true;
            }
        }
        if (anyReset)
            syncToClients();
        // listeners know consoles by their place in the world, which a hosted one doesn't have
        if (host == null)
            for (LayoutListener listener : layoutListeners)
                listener.layoutReady(this);
    }

    /**
     * The console has been carried to another block position: what its parts
     * hear, and who hears them, is measured from there.
     */
    public void linkPositionChanged() {
        linkPorts.values().forEach(LinkPort::positionChanged);
    }

    private void onStrengthReceived(PanelElement element, int strength) {
        if (element.receiveStrength(strength)) {
            syncToClients();
            signalMayHaveChanged(element);
        }
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
        return clickElement(elementId, 0, localX, localY, sneaking);
    }

    /** As above, on one of the element's click zones (see {@link PanelElement#pickBoxes}). */
    public boolean clickElement(UUID elementId, int zone, float localX, float localY, boolean sneaking) {
        PanelElement element = layout.elementById(elementId).orElse(null);
        if (element == null || !element.takesClicks() || !element.click(zone, localX, localY, sneaking))
            return false;
        onElementStateChanged(element);
        return true;
    }

    /**
     * A hand setting a knob or slider straight to a value. Returns true if it
     * moved. {@code actor}, if given, isn't sent the sound: a player dragging
     * the control hears each step from their own client as it happens.
     */
    public boolean setAnalogValue(UUID elementId, int value, @org.jetbrains.annotations.Nullable Player actor) {
        if (!(layout.elementById(elementId).orElse(null) instanceof AnalogElement analog) || !analog.turnTo(value))
            return false;
        playSound(analog, actor);
        onElementStateChanged(analog);
        return true;
    }

    public boolean setAnalogValue(UUID elementId, int value) {
        return setAnalogValue(elementId, value, null);
    }

    /** Plays the sound of the part's last change, if any, from the console to everyone near it but {@code except}. */
    private void playSound(PanelElement element, @org.jetbrains.annotations.Nullable Player except) {
        PartSound sound = element.takeSound();
        if (sound != null && level != null && !level.isClientSide)
            level.playSound(except, placeInWorld(), sound.event(), SoundSource.BLOCKS, sound.volume(), sound.pitch());
    }

    /**
     * A part's state was changed from outside the console, by what carries it
     * (a lever that follows the contraption): the same path a click's change takes.
     */
    public void elementChanged(PanelElement element) {
        onElementStateChanged(element);
    }

    /** Sends clients the parts' transient state after something outside the console changed it (a floor's lamp). */
    public void syncRuntime() {
        syncToClients();
    }

    private void onElementStateChanged(PanelElement element) {
        playSound(element, null);
        notifyStrengthChanged(element.id());
        if (element.hasPersistentState())
            setChanged();
        syncToClients();
        signalMayHaveChanged(element);
    }

    // --- Automation (computers) ----------------------------------------------
    // A computer works a console through the same link network as everything
    // else: it operates controls like a hand would, and "writes" to a lamp or
    // meter by sending on that component's frequency from the console. Parts
    // without a frequency are the computer's alone: it reads and sets them
    // directly, and nothing reaches the network.

    /**
     * Told when the signal on a component of any console changes; server only.
     * Listeners are global so that one computer can follow a whole group of
     * joined consoles, whichever of them it touches.
     */
    public interface SignalListener {
        void signalChanged(PanelBlockEntity console, PanelElement element, int strength);
    }

    /**
     * Told when a console's components are (re)connected: on load and after
     * every change to its layout; server only. For integrations that keep
     * their own record of a console's parts (see compat/drivebysable).
     */
    public interface LayoutListener {
        void layoutReady(PanelBlockEntity console);
    }

    private static final List<SignalListener> signalListeners = new CopyOnWriteArrayList<>();
    private static final List<LayoutListener> layoutListeners = new CopyOnWriteArrayList<>();
    private final Map<UUID, Integer> lastSignals = new HashMap<>();

    /** What a computer sends on receiver components' frequencies. Never saved: it ends with the program. */
    private final Map<UUID, Integer> drivenStrengths = new HashMap<>();
    private final Map<UUID, LinkPort> drivePorts = new HashMap<>();

    /** The strength (0-15) a component sends, or the one it reads off its frequency. */
    public static int signalOf(PanelElement element) {
        if (element instanceof dev.mccastle91.createcontrolling.panel.element.ReceiverElement receiver)
            return receiver.receivedStrength();
        return element.isTransmitter() ? element.transmittedStrength() : element.displayStrength();
    }

    public static void addSignalListener(SignalListener listener) {
        signalListeners.add(listener);
    }

    public static void removeSignalListener(SignalListener listener) {
        signalListeners.remove(listener);
    }

    public static void addLayoutListener(LayoutListener listener) {
        layoutListeners.add(listener);
    }

    private void signalMayHaveChanged(PanelElement element) {
        if (signalListeners.isEmpty() || level == null || level.isClientSide || host != null
                || !element.isTransmitter() && !element.isReceiver())
            return;
        int strength = signalOf(element);
        Integer previous = lastSignals.put(element.id(), strength);
        if (previous != null && previous == strength)
            return;
        for (SignalListener listener : signalListeners)
            listener.signalChanged(this, element, strength);
    }

    /**
     * Sets a component's signal from outside: a control is operated to send
     * {@code strength}; for a lamp, meter or other receiver with a frequency
     * the console itself sends {@code strength} on it (0 stops sending). A
     * receiver without a frequency has no network to listen to, so it shows
     * {@code strength} directly and nothing leaves the console.
     * Returns false if there is no such component or it has nothing to set.
     */
    public boolean setSignal(UUID elementId, int strength) {
        PanelElement element = layout.elementById(elementId).orElse(null);
        if (element == null || level == null || level.isClientSide)
            return false;
        strength = Math.max(0, Math.min(15, strength));
        if (element.isTransmitter()) {
            if (element.operate(strength))
                onElementStateChanged(element);
            return true;
        }
        if (!element.isReceiver())
            return false;
        if (element.frequency().isEmpty()) {
            if (element.receiveStrength(strength)) {
                syncToClients();
                signalMayHaveChanged(element);
            }
            return true;
        }
        if (strength == 0) {
            drivenStrengths.remove(elementId);
            closeDrivePort(drivePorts.remove(elementId));
            return true;
        }
        drivenStrengths.put(elementId, strength);
        LinkPort port = drivePorts.get(elementId);
        if (port != null)
            port.notifyStrengthChanged();
        else
            openDrivePort(element);
        return true;
    }

    /**
     * The signal of a cable wired to a lamp, meter or other receiver (another
     * mod's wiring, see compat/drivebysable); 0 when the cable is dead or gone.
     * The part shows the stronger of this and what its frequency or a computer
     * gives it. Never saved: the wiring mod resends it on load.
     * Returns false if there is no such receiver.
     */
    public boolean setWiredSignal(UUID elementId, int strength) {
        PanelElement element = layout.elementById(elementId).orElse(null);
        if (element == null || !element.isReceiver() || level == null || level.isClientSide)
            return false;
        if (element.receiveWired(strength)) {
            syncToClients();
            signalMayHaveChanged(element);
        }
        return true;
    }

    /**
     * Ends everything {@link #setSignal} did to receivers: stops sending on
     * their frequencies and darkens the unlinked ones, which only a computer drives.
     */
    public void clearDrivenSignals() {
        drivenStrengths.clear();
        drivePorts.values().forEach(this::closeDrivePort);
        drivePorts.clear();
        boolean anyChanged = false;
        for (PanelElement element : layout.elements()) {
            if (element.isReceiver() && element.frequency().isEmpty() && element.receiveStrength(0)) {
                signalMayHaveChanged(element);
                anyChanged = true;
            }
        }
        if (anyChanged)
            syncToClients();
    }

    /** Its strength must already read 0: listeners are told before the port leaves the network. */
    private void closeDrivePort(LinkPort port) {
        if (port == null)
            return;
        port.notifyStrengthChanged();
        port.remove();
    }

    private void openDrivePort(PanelElement element) {
        if (CCLink.PLATFORM == null)
            return;
        UUID id = element.id();
        drivePorts.put(id, CCLink.PLATFORM.createPort(level, this::placeInWorld, element.frequency(), false,
                () -> drivenStrengths.getOrDefault(id, 0), strength -> { }, this::linkAlive));
    }

    /** Optional integrations' per-console objects, one of each kind (see compat/computercraft). */
    private final Map<Class<?>, Object> integrationData = new HashMap<>();

    public <T> T integrationData(Class<T> kind, java.util.function.Function<PanelBlockEntity, T> factory) {
        return kind.cast(integrationData.computeIfAbsent(kind, ignored -> factory.apply(this)));
    }

    /** Lets elements run timers (button pulses); registered by PanelBlock server-side only. */
    public void serverTick() {
        if (!portsInitialized)
            rebuildLinkPorts();
        boolean anyChanged = false;
        for (PanelElement element : layout.elements()) {
            if (element.tick()) {
                playSound(element, null); // a button coming back up
                notifyStrengthChanged(element.id());
                signalMayHaveChanged(element);
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

    @Override
    public void saveToItem(net.minecraft.world.item.ItemStack stack, HolderLookup.Provider registries) {
        savingToItem = true;
        try {
            super.saveToItem(stack, registries);
        } finally {
            savingToItem = false;
        }
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

    @Override
    public void saveToItem(net.minecraft.world.item.ItemStack stack) {
        savingToItem = true;
        try {
            super.saveToItem(stack);
        } finally {
            savingToItem = false;
        }
    }
    *///?}

    /** An item carries the design, not what a Display Link last wrote on it. */
    private boolean savingToItem;

    private void writeData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(TAG_LAYOUT, layout.save(registries));
        tag.put(TAG_SHAPE, shape.save());
        tag.putString(TAG_CASING, casing.id());
        if (plateColor != null)
            tag.putString(TAG_PLATE, plateColor.getName());
        if (!name.isEmpty())
            tag.putString(TAG_NAME, name);
        if (material != null) {
            tag.put(TAG_MATERIAL, net.minecraft.nbt.NbtUtils.writeBlockState(material));
            if (!materialItem.isEmpty())
                tag.put(TAG_MATERIAL_ITEM, McCompat.saveStack(materialItem, registries));
        }
        // Saved like Create's own displays keep their text: a console taken along
        // by a contraption is rebuilt from this data, and no Display Link reaches it there
        if (savingToItem)
            return;
        ListTag lines = new ListTag();
        displayLines.forEach((line, text) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt(TAG_DISPLAY_LINE, line);
            entry.putString(TAG_DISPLAY_TEXT, McCompat.componentToJson(text, registries));
            lines.add(entry);
        });
        if (!lines.isEmpty())
            tag.put(TAG_DISPLAY_LINES, lines);
    }

    private void readData(CompoundTag tag, HolderLookup.Provider registries) {
        ConsoleShape previousShape = shape;
        ConsoleCasing previousCasing = casing;
        BlockState previousMaterial = material;
        net.minecraft.world.item.DyeColor previousPlate = plateColor;
        // every full save and update tag carries the plate's dye when it has one
        plateColor = tag.contains(TAG_PLATE) ? net.minecraft.world.item.DyeColor.byName(tag.getString(TAG_PLATE), null) : null;
        if (plateColor == net.minecraft.world.item.DyeColor.GRAY)
            plateColor = null;
        if (tag.contains(TAG_SHAPE))
            shape = ConsoleShape.load(tag.getCompound(TAG_SHAPE)).forFace(attachFace(getBlockState()));
        if (tag.contains(TAG_CASING))
            casing = ConsoleCasing.byId(tag.getString(TAG_CASING));
        // a block with a casing of its own keeps it, and no other block may wear that one
        ConsoleCasing fixed = fixedCasing();
        if (fixed != null)
            casing = fixed;
        else if (casing == ConsoleCasing.COPYCAT)
            casing = ConsoleCasing.DEFAULT;
        // every full save and update tag carries the copied block when there is one
        material = null;
        materialItem = net.minecraft.world.item.ItemStack.EMPTY;
        if (fixed == ConsoleCasing.COPYCAT && tag.contains(TAG_MATERIAL)) {
            BlockState read = net.minecraft.nbt.NbtUtils.readBlockState(
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), tag.getCompound(TAG_MATERIAL));
            if (!read.isAir()) {
                material = read;
                if (tag.contains(TAG_MATERIAL_ITEM))
                    materialItem = McCompat.loadStack(tag.getCompound(TAG_MATERIAL_ITEM), registries);
            }
        }
        if (tag.contains(TAG_LAYOUT))
            layout = PanelLayout.load(tag.getCompound(TAG_LAYOUT), registries);
        // every full save and update tag carries the name when there is one
        name = cleanName(tag.getString(TAG_NAME));
        invalidateShapes();
        if (level != null && !level.isClientSide) {
            // Placing a console item with stored data loads after onLoad ran
            // with an empty layout — re-key the network, and neighbours can
            // now see this console's real shape and casing
            rebuildLinkPorts();
            refreshNeighbourConnections();
        } else if (level != null && (!shape.equals(previousShape) || casing != previousCasing
                || material != previousMaterial || plateColor != previousPlate)) {
            // The body is generated from this data: refresh it and re-mesh
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
        }
        if (material != previousMaterial)
            updateMaterialLight();
        // Transient element state piggybacks on update tags (never on disk)
        if (tag.contains(TAG_RUNTIME)) {
            ListTag runtime = tag.getList(TAG_RUNTIME, Tag.TAG_COMPOUND);
            for (int i = 0; i < runtime.size(); i++) {
                CompoundTag entry = runtime.getCompound(i);
                layout.elementById(entry.getUUID(TAG_RUNTIME_ID)).ifPresent(element -> {
                    element.applyDisplayStrength(entry.getInt(TAG_RUNTIME_STRENGTH));
                    element.applyDisplayText(entry.getString(TAG_RUNTIME_TEXT));
                });
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
            String text = element.displayText();
            if (strength == 0 && text.isEmpty())
                continue;
            CompoundTag entry = new CompoundTag();
            entry.putUUID(TAG_RUNTIME_ID, element.id());
            entry.putInt(TAG_RUNTIME_STRENGTH, strength);
            if (!text.isEmpty())
                entry.putString(TAG_RUNTIME_TEXT, text);
            runtime.add(entry);
        }
        if (!runtime.isEmpty())
            tag.put(TAG_RUNTIME, runtime);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
