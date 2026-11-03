package dev.mccastle91.createcontrolling.panel.element;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * Static text annotating a console. Sends and receives nothing, never takes
 * clicks, and may sit over other components as a caption. Unlike every other
 * component its footprint is player-sized: a width, and a scale that sets
 * both the text height and the footprint's depth.
 */
public class LabelElement extends PanelElement {

    public static final String TAG_TEXT = "Text";
    public static final String TAG_COLOR = "TextColor";
    public static final String TAG_SCALE = "Scale";
    public static final String TAG_ALIGNMENT = "Align";
    public static final String TAG_WIDTH = "Width";

    public static final int MAX_TEXT_LENGTH = 64;
    public static final int DEFAULT_COLOR = 0xEDE4D4; // warm off-white, Create-ish
    public static final int MAX_SCALE = 4;
    public static final float DEFAULT_WIDTH = 6f;
    /** Footprint depth per scale step, px. */
    public static final float DEPTH_PER_SCALE = 1f;

    public enum Alignment {
        LEFT, CENTER, RIGHT;

        public static Alignment byName(String name) {
            for (Alignment value : values())
                if (value.name().equalsIgnoreCase(name))
                    return value;
            return CENTER;
        }
    }

    private String text = "Label";
    private int color = DEFAULT_COLOR;
    private int scale = 1;
    private Alignment alignment = Alignment.CENTER;
    private float labelWidth = DEFAULT_WIDTH;

    public LabelElement(PanelElementType<? extends LabelElement> type) {
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

    @Override
    public boolean takesClicks() {
        return false;
    }

    @Override
    public float width() {
        return labelWidth;
    }

    @Override
    public float depth() {
        return scale * DEPTH_PER_SCALE;
    }

    public void setLabelWidth(float width) {
        this.labelWidth = Mth.clamp(snap(width), 1f, AREA_MAX - AREA_MIN);
        reclamp();
    }

    public String text() {
        return text;
    }

    public void setText(String text) {
        String safe = text == null ? "" : text;
        this.text = safe.length() > MAX_TEXT_LENGTH ? safe.substring(0, MAX_TEXT_LENGTH) : safe;
    }

    public int color() {
        return color;
    }

    public void setColor(int color) {
        this.color = color & 0xFFFFFF;
    }

    public int scale() {
        return scale;
    }

    public void setScale(int scale) {
        this.scale = Mth.clamp(scale, 1, MAX_SCALE);
        reclamp();
    }

    public Alignment alignment() {
        return alignment;
    }

    public void setAlignment(Alignment alignment) {
        this.alignment = alignment == null ? Alignment.CENTER : alignment;
    }

    @Override
    protected void saveOptions(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString(TAG_TEXT, text);
        tag.putInt(TAG_COLOR, color);
        tag.putInt(TAG_SCALE, scale);
        tag.putString(TAG_ALIGNMENT, alignment.name());
        tag.putFloat(TAG_WIDTH, labelWidth);
    }

    @Override
    protected void loadOptions(CompoundTag tag, HolderLookup.Provider registries) {
        setText(tag.getString(TAG_TEXT));
        setColor(tag.contains(TAG_COLOR) ? tag.getInt(TAG_COLOR) : DEFAULT_COLOR);
        setScale(tag.contains(TAG_SCALE) ? tag.getInt(TAG_SCALE) : 1);
        setAlignment(Alignment.byName(tag.getString(TAG_ALIGNMENT)));
        setLabelWidth(tag.contains(TAG_WIDTH) ? tag.getFloat(TAG_WIDTH) : DEFAULT_WIDTH);
    }
}
