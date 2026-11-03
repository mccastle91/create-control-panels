package dev.mccastle91.createcontrolling.panel.element;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;

/** Indicator lamp (small or large): glows brighter the stronger its frequency. */
public class LampElement extends ReceiverElement implements ColorableElement {

    public static final String TAG_COLOR = "Color";
    public static final DyeColor DEFAULT_COLOR = DyeColor.LIME;

    private DyeColor color = DEFAULT_COLOR;

    public LampElement(PanelElementType<? extends LampElement> type) {
        super(type);
    }

    @Override
    public DyeColor color() {
        return color;
    }

    @Override
    public void setColor(DyeColor color) {
        this.color = color == null ? DEFAULT_COLOR : color;
    }

    @Override
    protected void saveOptions(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString(TAG_COLOR, color.getName());
    }

    @Override
    protected void loadOptions(CompoundTag tag, HolderLookup.Provider registries) {
        setColor(DyeColor.byName(tag.getString(TAG_COLOR), DEFAULT_COLOR));
    }
}
