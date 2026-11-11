package dev.mccastle91.createcontrolling.panel.element;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;

/**
 * An analog output (knob or slider): a 0-15 value sent continuously on its
 * frequency. Sneak + scroll nudges it by one. The value persists.
 */
public abstract class AnalogElement extends PanelElement implements ColorableElement {

    public static final String TAG_VALUE = "Value";
    public static final String TAG_COLOR = "Color";
    public static final int MAX_VALUE = 15;

    private int value;
    private DyeColor color;

    protected AnalogElement(PanelElementType<?> type, DyeColor defaultColor) {
        super(type);
        this.color = defaultColor;
    }

    protected abstract DyeColor defaultColor();

    @Override
    public boolean isTransmitter() {
        return true;
    }

    @Override
    public boolean isReceiver() {
        return false;
    }

    @Override
    public boolean scroll(int delta) {
        return setValue(value + delta);
    }

    @Override
    public boolean operate(int strength) {
        return setValue(strength);
    }

    @Override
    public boolean hasPersistentState() {
        return true;
    }

    @Override
    public int transmittedStrength() {
        return value;
    }

    public int value() {
        return value;
    }

    /** Returns true if the value changed. */
    public boolean setValue(int value) {
        int clamped = Mth.clamp(value, 0, MAX_VALUE);
        boolean changed = clamped != this.value;
        this.value = clamped;
        return changed;
    }

    /** Click steps one forward; sneak-click steps one back. */
    @Override
    public boolean click(float localX, float localY, boolean sneaking) {
        return setValue(value + (sneaking ? -1 : 1));
    }

    /** Sneak-clicking an analog control steps it back rather than opening the designer. */
    @Override
    public boolean takesSneakClicks() {
        return true;
    }

    @Override
    public DyeColor color() {
        return color;
    }

    @Override
    public void setColor(DyeColor color) {
        this.color = color == null ? defaultColor() : color;
    }

    @Override
    protected void saveOptions(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(TAG_VALUE, value);
        tag.putString(TAG_COLOR, color.getName());
    }

    @Override
    protected void loadOptions(CompoundTag tag, HolderLookup.Provider registries) {
        setValue(tag.getInt(TAG_VALUE));
        setColor(DyeColor.byName(tag.getString(TAG_COLOR), defaultColor()));
    }
}
