package dev.mccastle91.createcontrolling.panel.element;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * Three nixie tubes naming the floor an elevator's cabin is at, or the one it
 * is passing: the short name set on that floor's Elevator Contact. It needs
 * no frequency and has no options; what carries the console writes the name
 * (see compat/create/contraption). Anywhere but on an elevator it is dark.
 */
public class FloorReadoutElement extends PanelElement {

    /** As many characters as it has tubes. */
    public static final int TUBES = 3;

    /** Runtime only: never saved, sent to clients beside the parts' strengths. */
    private String text = "";

    public FloorReadoutElement(PanelElementType<? extends FloorReadoutElement> type) {
        super(type);
    }

    @Override
    public boolean isTransmitter() {
        return false;
    }

    @Override
    public boolean isReceiver() {
        return false;
    }

    /** Sets the floor's name; true if that changed what the tubes show (the caller syncs). */
    public boolean setText(String name) {
        String shown = name == null ? "" : name.trim();
        if (shown.length() > TUBES)
            shown = shown.substring(0, TUBES);
        if (shown.equals(text))
            return false;
        text = shown;
        return true;
    }

    /** One string per tube, the name set to the right as numbers are. */
    public String[] glyphs() {
        String[] glyphs = new String[TUBES];
        int blank = TUBES - text.length();
        for (int i = 0; i < TUBES; i++)
            glyphs[i] = i < blank ? " " : text.substring(i - blank, i - blank + 1);
        return glyphs;
    }

    @Override
    public String displayText() {
        return text;
    }

    @Override
    public void applyDisplayText(String text) {
        setText(text);
    }

    @Override
    protected void saveOptions(CompoundTag tag, HolderLookup.Provider registries) {
    }

    @Override
    protected void loadOptions(CompoundTag tag, HolderLookup.Provider registries) {
    }
}
