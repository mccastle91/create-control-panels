package dev.mccastle91.createcontrolling.panel.element;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * Displays its frequency's current strength. The strength is runtime state:
 * never saved, resynced from the link network on load.
 */
public abstract class ReceiverElement extends PanelElement {

    private int receivedStrength;

    protected ReceiverElement(PanelElementType<?> type) {
        super(type);
    }

    @Override
    public boolean isTransmitter() {
        return false;
    }

    @Override
    public boolean isReceiver() {
        return true;
    }

    public int receivedStrength() {
        return receivedStrength;
    }

    public void setReceivedStrength(int strength) {
        this.receivedStrength = Mth.clamp(strength, 0, 15);
    }

    @Override
    public boolean receiveStrength(int strength) {
        int previous = receivedStrength;
        setReceivedStrength(strength);
        return previous != receivedStrength;
    }

    @Override
    public int displayStrength() {
        return receivedStrength;
    }

    @Override
    public void applyDisplayStrength(int strength) {
        setReceivedStrength(strength);
    }

    @Override
    protected void saveOptions(CompoundTag tag, HolderLookup.Provider registries) {
    }

    @Override
    protected void loadOptions(CompoundTag tag, HolderLookup.Provider registries) {
    }
}
