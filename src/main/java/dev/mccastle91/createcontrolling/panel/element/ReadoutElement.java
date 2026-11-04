package dev.mccastle91.createcontrolling.panel.element;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import java.util.Arrays;
import java.util.Set;

/**
 * Shows lines of text a Create Display Link sends to its console. The text
 * belongs to the console, by line number, so two readouts may mirror a line
 * and re-pointing one shows its new line at once; a readout only stores which
 * lines it shows, one per row, and a size that sets its width.
 */
public abstract class ReadoutElement extends PanelElement {

    public static final String TAG_LINES = "Lines";
    public static final String TAG_SIZE = "Size";
    /** The highest line number a console offers. */
    public static final int MAX_LINES = 8;
    /** A row left blank; only readouts with several rows may have one. */
    public static final int NO_LINE = 0;

    private final int[] lines;
    private int size;

    protected ReadoutElement(PanelElementType<?> type, int rows, int size) {
        super(type);
        this.lines = new int[rows];
        Arrays.fill(lines, 1);
        this.size = size;
    }

    public abstract int minSize();

    public abstract int maxSize();

    /** The footprint width at a size, px. */
    public abstract float widthFor(int size);

    /** Roughly how many characters fit on a row, for sources that lay out to the target. */
    public abstract int columns();

    @Override
    public boolean isTransmitter() {
        return false;
    }

    @Override
    public boolean isReceiver() {
        return false;
    }

    @Override
    public float width() {
        return widthFor(size);
    }

    public int size() {
        return size;
    }

    /** The size this readout may take, before any overlap check. */
    public int clampSize(int size) {
        return Mth.clamp(size, minSize(), maxSize());
    }

    /** Sets the size as given (see {@link #clampSize}); the caller checks it fits. */
    public void setSize(int size) {
        this.size = clampSize(size);
        reclamp();
    }

    public int rows() {
        return lines.length;
    }

    /** The line number row {@code row} shows, or {@link #NO_LINE}. */
    public int line(int row) {
        return lines[row];
    }

    public void setLine(int row, int line) {
        if (row >= 0 && row < lines.length)
            lines[row] = Mth.clamp(line, lines.length > 1 ? NO_LINE : 1, MAX_LINES);
    }

    /** Whether any row shows this line number. */
    public boolean shows(int line) {
        for (int shown : lines)
            if (shown == line)
                return true;
        return false;
    }

    /** Points each row at the lowest line no other readout shows, so a new part has its own. */
    public void takeFreeLines(Set<Integer> taken) {
        int line = 1;
        for (int row = 0; row < lines.length; row++) {
            while (line < MAX_LINES && taken.contains(line))
                line++;
            lines[row] = line;
            line = Math.min(line + 1, MAX_LINES);
        }
    }

    @Override
    protected void saveOptions(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putIntArray(TAG_LINES, lines);
        tag.putInt(TAG_SIZE, size);
    }

    @Override
    protected void loadOptions(CompoundTag tag, HolderLookup.Provider registries) {
        int[] saved = tag.getIntArray(TAG_LINES);
        for (int row = 0; row < Math.min(saved.length, lines.length); row++)
            setLine(row, saved[row]);
        if (tag.contains(TAG_SIZE))
            setSize(tag.getInt(TAG_SIZE));
    }
}
