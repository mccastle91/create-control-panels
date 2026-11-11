package dev.mccastle91.createcontrolling.panel.element;

import dev.mccastle91.createcontrolling.panel.FrequencyPair;
import dev.mccastle91.createcontrolling.util.McCompat;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One component on a console face. Positions are in pixels of the 16×16 px
 * design area, snapped to a 0.5 px grid; sizes are fixed by the
 * {@link PanelElementType}. Subclasses add their options and state through
 * {@link #saveOptions}/{@link #loadOptions} and react to clicks, scrolls and
 * ticks by overriding the hooks below.
 */
public abstract class PanelElement {

    public static final String TAG_TYPE = "Type";
    public static final String TAG_ID = "Id";
    public static final String TAG_X = "X";
    public static final String TAG_Y = "Y";
    public static final String TAG_FREQUENCY = "Frequency";
    public static final String TAG_PAID = "Paid";

    /** Side length of the design area, px. */
    public static final float FACE_SIZE = 16f;
    /** The outer 1 px ring is the casing frame; components stay inside it. */
    public static final float AREA_MIN = 1f;
    public static final float AREA_MAX = FACE_SIZE - 1f;
    public static final float SNAP = 0.5f;

    private final PanelElementType<?> type;
    private UUID id = UUID.randomUUID();
    private float x = AREA_MIN;
    private float y = AREA_MIN;
    private FrequencyPair frequency = FrequencyPair.EMPTY;
    /** The exact items paid to mount this part, given back when it's removed. */
    private List<ItemStack> paid = List.of();

    protected PanelElement(PanelElementType<?> type) {
        this.type = type;
    }

    public final PanelElementType<?> type() {
        return type;
    }

    /** Whether this element sends on its frequency. */
    public abstract boolean isTransmitter();

    /** Whether this element reads its frequency's current strength. */
    public abstract boolean isReceiver();

    protected abstract void saveOptions(CompoundTag tag, HolderLookup.Provider registries);

    protected abstract void loadOptions(CompoundTag tag, HolderLookup.Provider registries);

    public final CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_TYPE, type.id());
        tag.putUUID(TAG_ID, id);
        tag.putFloat(TAG_X, x);
        tag.putFloat(TAG_Y, y);
        if (!frequency.isEmpty())
            tag.put(TAG_FREQUENCY, frequency.save(registries));
        if (!paid.isEmpty()) {
            ListTag list = new ListTag();
            for (ItemStack stack : paid)
                list.add(McCompat.saveStack(stack, registries));
            tag.put(TAG_PAID, list);
        }
        saveOptions(tag, registries);
        return tag;
    }

    public final void load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.hasUUID(TAG_ID))
            id = tag.getUUID(TAG_ID);
        frequency = tag.contains(TAG_FREQUENCY)
                ? FrequencyPair.load(tag.getCompound(TAG_FREQUENCY), registries)
                : FrequencyPair.EMPTY;
        List<ItemStack> loaded = new ArrayList<>();
        for (Tag entry : tag.getList(TAG_PAID, Tag.TAG_COMPOUND)) {
            ItemStack stack = McCompat.loadStack((CompoundTag) entry, registries);
            if (!stack.isEmpty())
                loaded.add(stack);
        }
        paid = List.copyOf(loaded);
        loadOptions(tag, registries);
        // after options: a label's width depends on them
        setPos(tag.getFloat(TAG_X), tag.getFloat(TAG_Y));
    }

    // --- Placement --------------------------------------------------------

    public UUID id() {
        return id;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float width() {
        return type.width();
    }

    public float depth() {
        return type.depth();
    }

    /** How far the part stands off the face, px. */
    public float height() {
        return type.height();
    }

    public static float snap(float value) {
        return Math.round(value / SNAP) * SNAP;
    }

    /** Where {@link #setPos} would put the left edge for a requested x. */
    public float clampX(float x) {
        return Mth.clamp(snap(x), AREA_MIN, Math.max(AREA_MIN, AREA_MAX - width()));
    }

    /** Where {@link #setPos} would put the far edge for a requested y. */
    public float clampY(float y) {
        return Mth.clamp(snap(y), AREA_MIN, Math.max(AREA_MIN, AREA_MAX - depth()));
    }

    /** Snapped to the 0.5 px grid and clamped inside the frame. */
    public void setPos(float x, float y) {
        this.x = clampX(x);
        this.y = clampY(y);
    }

    /** Re-clamps after a size-affecting option change. */
    protected void reclamp() {
        setPos(x, y);
    }

    public boolean contains(float px, float py) {
        return px >= x && px < x + width() && py >= y && py < y + depth();
    }

    public boolean overlaps(float ox, float oy, float ow, float od) {
        return ox < x + width() && ox + ow > x && oy < y + depth() && oy + od > y;
    }

    public FrequencyPair frequency() {
        return frequency;
    }

    public void setFrequency(FrequencyPair frequency) {
        this.frequency = frequency;
    }

    public List<ItemStack> paid() {
        return paid;
    }

    public void setPaid(List<ItemStack> paid) {
        List<ItemStack> copy = new ArrayList<>();
        paid.forEach(stack -> copy.add(stack.copy()));
        this.paid = List.copyOf(copy);
    }

    // --- Behaviour hooks (server side) -------------------------------------

    /** Labels and other passive parts let clicks fall through to what's beneath. */
    public boolean takesClicks() {
        return isTransmitter();
    }

    /** Whether sneak-clicking acts on this element instead of opening the designer. */
    public boolean takesSneakClicks() {
        return false;
    }

    /**
     * A click at ({@code localX}, {@code localY}) px inside this element's
     * footprint. Returns true if the element's state changed.
     */
    public boolean click(float localX, float localY, boolean sneaking) {
        return false;
    }

    /** Scroll adjustment (sneak + mouse wheel). Returns true if state changed. */
    public boolean scroll(int delta) {
        return false;
    }

    /**
     * Sets a control the way a hand would so that it sends {@code strength}
     * (0-15); on/off controls treat anything above 0 as on. Used by
     * automation (computers). Returns true if the state changed.
     */
    public boolean operate(int strength) {
        return false;
    }

    /** Server tick; returns true if the transmitted strength changed (e.g. a pulse ended). */
    public boolean tick() {
        return false;
    }

    /** Whether a state change should be written to disk (latching parts) or is transient. */
    public boolean hasPersistentState() {
        return false;
    }

    // --- Signal --------------------------------------------------------------

    /** Strength (0-15) this element currently drives onto its frequency. */
    public int transmittedStrength() {
        return 0;
    }

    /** Pushes the network's strength into a receiver; true if its stored value changed. */
    public boolean receiveStrength(int strength) {
        return false;
    }

    /** Transient visual state synced to clients (pulse glow, received strength). */
    public int displayStrength() {
        return transmittedStrength();
    }

    /** Client-side application of a synced {@link #displayStrength()}. */
    public void applyDisplayStrength(int strength) {
    }
}
