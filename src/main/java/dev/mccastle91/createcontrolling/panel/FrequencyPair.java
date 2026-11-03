package dev.mccastle91.createcontrolling.panel;

import dev.mccastle91.createcontrolling.util.McCompat;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * A Redstone Link frequency: two item stacks, either optional — the exact
 * metaphor Create players know. This is our loader-agnostic representation;
 * mapping onto Create's {@code RedstoneLinkNetworkHandler.Frequency} happens
 * in the loader module (Phase 4).
 */
public final class FrequencyPair {

    public static final FrequencyPair EMPTY = new FrequencyPair(ItemStack.EMPTY, ItemStack.EMPTY);

    private static final String TAG_FIRST = "First";
    private static final String TAG_SECOND = "Second";

    private final ItemStack first;
    private final ItemStack second;

    private FrequencyPair(ItemStack first, ItemStack second) {
        this.first = first;
        this.second = second;
    }

    public static FrequencyPair of(ItemStack first, ItemStack second) {
        if (first.isEmpty() && second.isEmpty())
            return EMPTY;
        // Frequencies are identities, not inventory: normalize to count 1
        return new FrequencyPair(
                first.isEmpty() ? ItemStack.EMPTY : first.copyWithCount(1),
                second.isEmpty() ? ItemStack.EMPTY : second.copyWithCount(1));
    }

    public ItemStack first() {
        return first;
    }

    public ItemStack second() {
        return second;
    }

    public boolean isEmpty() {
        return first.isEmpty() && second.isEmpty();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (!first.isEmpty())
            tag.put(TAG_FIRST, McCompat.saveStack(first, registries));
        if (!second.isEmpty())
            tag.put(TAG_SECOND, McCompat.saveStack(second, registries));
        return tag;
    }

    public static FrequencyPair load(CompoundTag tag, HolderLookup.Provider registries) {
        return of(
                tag.contains(TAG_FIRST) ? McCompat.loadStack(tag.getCompound(TAG_FIRST), registries) : ItemStack.EMPTY,
                tag.contains(TAG_SECOND) ? McCompat.loadStack(tag.getCompound(TAG_SECOND), registries) : ItemStack.EMPTY);
    }
}
