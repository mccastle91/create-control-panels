package dev.mccastle91.createcontrolling.panel.element;

import dev.mccastle91.createcontrolling.api.PartOption;
import dev.mccastle91.createcontrolling.util.McCompat;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Create's Contraption Controls as a console part: a switch that lets the
 * actors of the contraption the console rides (drills, saws, deployers,
 * doors...) run, or stops them. Its filter names one kind of actor, as the
 * filter of a Contraption Controls block does; without one it switches them
 * all.
 *
 * <p>It is a switch and a receiver at once. The lever is thrown by hand. On
 * top of that a signal on its frequency holds the actors stopped for as long
 * as it lasts, whatever the lever says, the way redstone power holds a placed
 * Contraption Controls block off; that lets a Redstone Link on the ground
 * stop a contraption's actors.
 *
 * <p>The part only holds this state. What carries the console applies it (see
 * compat/create/contraption); on a placed console it waits for the next assembly.
 */
public class ActorSwitchElement extends ReceiverElement implements ColorableElement {

    public static final String TAG_ON = "On";
    public static final String TAG_FILTER = "Filter";
    public static final String TAG_COLOR = "Color";
    /** Whether a signal held the actors off when this was saved: read at assembly, before any link has spoken. */
    public static final String TAG_HELD = "Held";
    public static final String OPTION_FILTER = "filter";
    public static final DyeColor DEFAULT_COLOR = DyeColor.YELLOW;

    /** The items Create lets a Contraption Controls block be filtered by. */
    private static final TagKey<Item> CONTROLLED =
            TagKey.create(Registries.ITEM, McCompat.id("create", "contraption_controlled"));

    private boolean on = true;
    private ItemStack filter = ItemStack.EMPTY;
    private DyeColor color = DEFAULT_COLOR;
    private boolean heldWhenSaved;

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
        sound(PartSound.lever(on));
        return true;
    }

    @Override
    public boolean hasPersistentState() {
        return true;
    }

    /** Where the lever stands: on lets the actors run. */
    public boolean isOn() {
        return on;
    }

    /** Throws the lever without a hand (someone switched the actors another way); true if it moved. */
    public boolean setOn(boolean on) {
        if (this.on == on)
            return false;
        this.on = on;
        return true;
    }

    /** Whether a signal on the part's frequency (or a cable, or a computer) is holding the actors off. */
    public boolean held() {
        return receivedStrength() > 0;
    }

    /** What the part asks for: the actors run with the lever on and no signal holding them. */
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

    /** Every item a filter can be, in an order client and server agree on. */
    public static List<Item> filterChoices() {
        List<Item> items = new ArrayList<>();
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(CONTROLLED))
            items.add(holder.value());
        items.sort(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
        return items;
    }

    @Override
    public DyeColor color() {
        return color;
    }

    @Override
    public void setColor(DyeColor color) {
        this.color = color == null ? DEFAULT_COLOR : color;
    }

    // --- Designer and integrations ---------------------------------------------

    @Override
    public List<PartOption> options() {
        List<Item> choices = filterChoices();
        int index = filter.isEmpty() ? 0 : choices.indexOf(filter.getItem()) + 1;
        Component what = filter.isEmpty()
                ? Component.translatable("gui.create_controlling.actor_filter.all") : filter.getHoverName();
        return List.of(PartOption.stepper(OPTION_FILTER,
                Component.translatable("gui.create_controlling.actor_filter", what), index, 0, choices.size(), 1));
    }

    @Override
    public boolean setOption(String key, int value) {
        if (!OPTION_FILTER.equals(key))
            return false;
        List<Item> choices = filterChoices();
        setFilter(value <= 0 || choices.isEmpty()
                ? ItemStack.EMPTY : new ItemStack(choices.get(Math.min(value, choices.size()) - 1)));
        return true;
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
        tag.putString(TAG_COLOR, color.getName());
        if (!filter.isEmpty())
            tag.put(TAG_FILTER, McCompat.saveStack(filter, registries));
        if (held())
            tag.putBoolean(TAG_HELD, true);
    }

    @Override
    protected void loadOptions(CompoundTag tag, HolderLookup.Provider registries) {
        on = !tag.contains(TAG_ON) || tag.getBoolean(TAG_ON);
        setColor(DyeColor.byName(tag.getString(TAG_COLOR), DEFAULT_COLOR));
        setFilter(tag.contains(TAG_FILTER) ? McCompat.loadStack(tag.getCompound(TAG_FILTER), registries) : ItemStack.EMPTY);
        heldWhenSaved = tag.getBoolean(TAG_HELD);
    }
}
