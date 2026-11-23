package dev.mccastle91.createcontrolling.panel.element;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;

/**
 * Indicator lamp (small or large): glows brighter the stronger its frequency.
 * Inverted, it is the other way round: fully lit with no signal, dimmer as
 * the signal rises, dark at full strength. The signal it receives and
 * reports is the same either way; only what it shows is turned over.
 */
public class LampElement extends ReceiverElement implements ColorableElement {

    public static final String TAG_COLOR = "Color";
    public static final String TAG_INVERTED = "Inverted";
    public static final DyeColor DEFAULT_COLOR = DyeColor.LIME;

    private DyeColor color = DEFAULT_COLOR;
    private boolean inverted;

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

    public boolean inverted() {
        return inverted;
    }

    public void setInverted(boolean inverted) {
        this.inverted = inverted;
    }

    /** How brightly the lamp glows, 0-15: its signal's strength, or 15 less it when inverted. */
    public int brightness() {
        return inverted ? 15 - displayStrength() : displayStrength();
    }

    @Override
    protected void saveOptions(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString(TAG_COLOR, color.getName());
        if (inverted)
            tag.putBoolean(TAG_INVERTED, true);
    }

    @Override
    protected void loadOptions(CompoundTag tag, HolderLookup.Provider registries) {
        setColor(DyeColor.byName(tag.getString(TAG_COLOR), DEFAULT_COLOR));
        inverted = tag.getBoolean(TAG_INVERTED);
    }
}
