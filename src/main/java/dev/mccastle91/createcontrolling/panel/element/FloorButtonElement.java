package dev.mccastle91.createcontrolling.panel.element;

import dev.mccastle91.createcontrolling.api.PartOption;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;

import java.util.List;
import java.util.Map;

/**
 * An elevator's call button: on a console that rides a Create elevator, a
 * press sends the cabin to one floor, counted from the lowest (floor 1). The
 * button glows while its floor is where the cabin stands or is heading.
 *
 * <p>It has no frequency. The part only knows its floor and that it was
 * pressed; what carries the console calls the floor and lights the button
 * (see compat/create/contraption). Anywhere but on an elevator it is a button
 * that does nothing.
 */
public class FloorButtonElement extends PanelElement implements ColorableElement {

    public static final String TAG_FLOOR = "Floor";
    public static final String TAG_COLOR = "Color";
    public static final String OPTION_FLOOR = "floor";
    public static final int MAX_FLOOR = 64;
    public static final DyeColor DEFAULT_COLOR = DyeColor.WHITE;
    /** How long the cap stays down after a press. */
    private static final int PRESS_TICKS = 6;

    private static final int PRESSED = 1;
    private static final int LIT = 2;

    private int floor = 1;
    private DyeColor color = DEFAULT_COLOR;

    /** Runtime only; never saved. */
    private int pressTicks;
    private boolean lit;

    public FloorButtonElement(PanelElementType<? extends FloorButtonElement> type) {
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
        return true;
    }

    /** Every press counts, also one on a button still down: the floor is called again. */
    @Override
    public boolean click(float localX, float localY, boolean sneaking) {
        if (pressTicks == 0)
            sound(PartSound.button(true, false));
        pressTicks = PRESS_TICKS;
        return true;
    }

    @Override
    public boolean tick() {
        if (pressTicks <= 0)
            return false;
        pressTicks--;
        if (pressTicks == 0)
            sound(PartSound.button(false, false));
        return pressTicks == 0;
    }

    /** The floor this button calls, 1 for the lowest. */
    public int floor() {
        return floor;
    }

    public void setFloor(int floor) {
        this.floor = Mth.clamp(floor, 1, MAX_FLOOR);
    }

    public boolean isPressed() {
        return pressTicks > 0;
    }

    public boolean isLit() {
        return lit;
    }

    /** True if that changed it (the caller syncs). */
    public boolean setLit(boolean lit) {
        if (this.lit == lit)
            return false;
        this.lit = lit;
        return true;
    }

    @Override
    public int displayStrength() {
        return (pressTicks > 0 ? PRESSED : 0) | (lit ? LIT : 0);
    }

    @Override
    public void applyDisplayStrength(int strength) {
        pressTicks = (strength & PRESSED) != 0 ? 1 : 0;
        lit = (strength & LIT) != 0;
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
    public List<PartOption> options() {
        return List.of(PartOption.stepper(OPTION_FLOOR,
                Component.translatable("gui.create_controlling.floor", floor), floor, 1, MAX_FLOOR, 1));
    }

    @Override
    public boolean setOption(String key, int value) {
        if (!OPTION_FLOOR.equals(key))
            return false;
        setFloor(value);
        return true;
    }

    @Override
    public void describe(Map<String, Object> out) {
        out.put("floor", floor);
    }

    @Override
    protected void saveOptions(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(TAG_FLOOR, floor);
        tag.putString(TAG_COLOR, color.getName());
    }

    @Override
    protected void loadOptions(CompoundTag tag, HolderLookup.Provider registries) {
        setFloor(tag.contains(TAG_FLOOR) ? tag.getInt(TAG_FLOOR) : 1);
        setColor(DyeColor.byName(tag.getString(TAG_COLOR), DEFAULT_COLOR));
    }
}
