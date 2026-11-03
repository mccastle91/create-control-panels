package dev.mccastle91.createcontrolling.panel.element;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;

/** Toggle switch: a lever rod that flips between 0 and 15. The state persists. */
public class SwitchElement extends PanelElement implements ColorableElement {

    public static final String TAG_ON = "On";
    public static final String TAG_COLOR = "Color";
    public static final DyeColor DEFAULT_COLOR = DyeColor.RED;

    private boolean on;
    private DyeColor color = DEFAULT_COLOR;

    public SwitchElement(PanelElementType<? extends SwitchElement> type) {
        super(type);
    }

    @Override
    public boolean isTransmitter() {
        return true;
    }

    @Override
    public boolean isReceiver() {
        return false;
    }

    @Override
    public boolean click(float localX, float localY, boolean sneaking) {
        on = !on;
        return true;
    }

    @Override
    public boolean hasPersistentState() {
        return true;
    }

    @Override
    public int transmittedStrength() {
        return on ? 15 : 0;
    }

    public boolean isOn() {
        return on;
    }

    public void setOn(boolean on) {
        this.on = on;
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
        tag.putBoolean(TAG_ON, on);
        tag.putString(TAG_COLOR, color.getName());
    }

    @Override
    protected void loadOptions(CompoundTag tag, HolderLookup.Provider registries) {
        on = tag.getBoolean(TAG_ON);
        setColor(DyeColor.byName(tag.getString(TAG_COLOR), DEFAULT_COLOR));
    }
}
