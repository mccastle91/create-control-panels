package dev.mccastle91.createcontrolling.panel.element;

import dev.mccastle91.createcontrolling.util.McCompat;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * Create's Contraption Controls as a console part, and made to look like its
 * top plate: a square button between two red arrows. A press lets the actors
 * of the contraption the console rides (drills, saws, deployers, doors...)
 * run, or stops them; the arrows light while they are stopped. Its filter
 * names one kind of actor, as the filter of a Contraption Controls block
 * does, and shows on the button; without one it switches them all.
 *
 * <p>It is a control and a receiver at once. On top of the button, a signal
 * on its frequency holds the actors stopped for as long as it lasts, the way
 * redstone power holds a placed Contraption Controls block off; that lets a
 * Redstone Link on the ground stop a contraption's actors.
 *
 * <p>The part only holds this state. What carries the console applies it (see
 * compat/create/contraption); on a placed console it waits for the next assembly.
 */
public class ActorSwitchElement extends ReceiverElement {

    public static final String TAG_ON = "On";
    public static final String TAG_FILTER = "Filter";
    /** Whether a signal held the actors off when this was saved: read at assembly, before any link has spoken. */
    public static final String TAG_HELD = "Held";
    /** The filter is set like a frequency, in a third slot after the frequency's two. */
    public static final int FILTER_SLOT = 2;

    /** The items Create lets a Contraption Controls block be filtered by. */
    private static final TagKey<Item> CONTROLLED =
            TagKey.create(Registries.ITEM, McCompat.id("create", "contraption_controlled"));

    /** How long the button stays down after a press. */
    private static final int PRESS_TICKS = 6;
    /** Rides above the four bits of the received strength in what clients are sent. */
    private static final int PRESSED = 16;

    private boolean on = true;
    private ItemStack filter = ItemStack.EMPTY;
    private boolean heldWhenSaved;
    /** Runtime only; never saved. */
    private int pressTicks;

    public ActorSwitchElement(PanelElementType<? extends ActorSwitchElement> type) {
        super(type);
    }

    @Override
    public boolean takesClicks() {
        return true;
    }

    @Override
    public boolean click(float localX, float localY, boolean sneaking) {
        on = !on;
        if (pressTicks == 0)
            sound(PartSound.button(true, false));
        pressTicks = PRESS_TICKS;
        return true;
    }

    @Override
    public boolean tick() {
        if (pressTicks <= 0)
            return false;
        pressTicks--;
        if (pressTicks == 0)
            sound(PartSound.button(false, false));
        return pressTicks == 0;
    }

    @Override
    public boolean hasPersistentState() {
        return true;
    }

    /** What the button was last pressed to: on lets the actors run. */
    public boolean isOn() {
        return on;
    }

    /** Sets that without a press (someone switched the actors another way); true if it changed. */
    public boolean setOn(boolean on) {
        if (this.on == on)
            return false;
        this.on = on;
        return true;
    }

    public boolean isPressed() {
        return pressTicks > 0;
    }

    /** Whether a signal on the part's frequency (or a cable, or a computer) is holding the actors off. */
    public boolean held() {
        return receivedStrength() > 0;
    }

    /** What the part asks for: the actors run when switched on with no signal holding them. */
    public boolean running() {
        return on && !held();
    }

    /** Whether the actors start a ride stopped, going by the console's saved data alone. */
    public boolean stoppedWhenSaved() {
        return !on || heldWhenSaved;
    }

    /** The kind of actor switched, as one item; empty for all of them. */
    public ItemStack filter() {
        return filter;
    }

    public void setFilter(ItemStack filter) {
        this.filter = filter.isEmpty() ? ItemStack.EMPTY : filter.copyWithCount(1);
    }

    /** Whether an item can be the filter: nothing (all actors), or what Create's own controls take. */
    public static boolean acceptsFilter(ItemStack stack) {
        return stack.isEmpty() || stack.is(CONTROLLED);
    }

    @Override
    public int displayStrength() {
        return receivedStrength() | (pressTicks > 0 ? PRESSED : 0);
    }

    @Override
    public void applyDisplayStrength(int strength) {
        super.applyDisplayStrength(strength & 15);
        pressTicks = (strength & PRESSED) != 0 ? 1 : 0;
    }

    @Override
    public void describe(Map<String, Object> out) {
        out.put("on", on);
        out.put("running", running());
        if (!filter.isEmpty())
            out.put("filter", BuiltInRegistries.ITEM.getKey(filter.getItem()).toString());
    }

    @Override
    protected void saveOptions(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(TAG_ON, on);
        if (!filter.isEmpty())
            tag.put(TAG_FILTER, McCompat.saveStack(filter, registries));
        if (held())
            tag.putBoolean(TAG_HELD, true);
    }

    @Override
    protected void loadOptions(CompoundTag tag, HolderLookup.Provider registries) {
        on = !tag.contains(TAG_ON) || tag.getBoolean(TAG_ON);
        setFilter(tag.contains(TAG_FILTER) ? McCompat.loadStack(tag.getCompound(TAG_FILTER), registries) : ItemStack.EMPTY);
        heldWhenSaved = tag.getBoolean(TAG_HELD);
    }
}
