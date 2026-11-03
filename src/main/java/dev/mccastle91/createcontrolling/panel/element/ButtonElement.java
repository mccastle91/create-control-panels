package dev.mccastle91.createcontrolling.panel.element;

import dev.mccastle91.createcontrolling.config.CCConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;

/** Momentary push button (small or large): 15 on its frequency for a configurable pulse. */
public class ButtonElement extends PanelElement implements ColorableElement {

    public static final String TAG_PULSE_TICKS = "PulseTicks";
    public static final String TAG_COLOR = "Color";
    public static final int DEFAULT_PULSE_TICKS = 10;
    public static final int MAX_PULSE_TICKS = 200;
    public static final DyeColor DEFAULT_COLOR = DyeColor.ORANGE;

    private int pulseTicks = DEFAULT_PULSE_TICKS;
    private DyeColor color = DEFAULT_COLOR;

    /** Runtime only; never saved. */
    private int remainingPulse;

    public ButtonElement(PanelElementType<? extends ButtonElement> type) {
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
        boolean wasOff = remainingPulse == 0;
        remainingPulse = pulseTicks;
        return wasOff;
    }

    @Override
    public boolean tick() {
        if (remainingPulse <= 0)
            return false;
        remainingPulse--;
        return remainingPulse == 0;
    }

    @Override
    public int transmittedStrength() {
        return remainingPulse > 0 ? 15 : 0;
    }

    @Override
    public void applyDisplayStrength(int strength) {
        remainingPulse = strength > 0 ? 1 : 0;
    }

    public boolean isPressed() {
        return remainingPulse > 0;
    }

    public int pulseTicks() {
        return pulseTicks;
    }

    public void setPulseTicks(int pulseTicks) {
        this.pulseTicks = Mth.clamp(pulseTicks, 1, Math.min(MAX_PULSE_TICKS, CCConfig.maxPulseTicks));
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
        tag.putInt(TAG_PULSE_TICKS, pulseTicks);
        tag.putString(TAG_COLOR, color.getName());
    }

    @Override
    protected void loadOptions(CompoundTag tag, HolderLookup.Provider registries) {
        setPulseTicks(tag.contains(TAG_PULSE_TICKS) ? tag.getInt(TAG_PULSE_TICKS) : DEFAULT_PULSE_TICKS);
        setColor(DyeColor.byName(tag.getString(TAG_COLOR), DEFAULT_COLOR));
    }
}
