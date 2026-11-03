package dev.mccastle91.createcontrolling.panel.element;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;

/**
 * Safety-covered switch: the first click lifts the cover, the second arms the
 * switch (15 on its frequency), the third closes the cover and disarms.
 * Sneak-clicking always slams the cover shut. The state persists.
 */
public class CoveredSwitchElement extends PanelElement implements ColorableElement {

    public enum State {
        CLOSED, OPEN, ARMED;

        static State byName(String name) {
            for (State state : values())
                if (state.name().equalsIgnoreCase(name))
                    return state;
            return CLOSED;
        }
    }

    public static final String TAG_STATE = "State";
    public static final String TAG_COLOR = "Color";
    public static final DyeColor DEFAULT_COLOR = DyeColor.RED;

    private State state = State.CLOSED;
    private DyeColor color = DEFAULT_COLOR;

    public CoveredSwitchElement(PanelElementType<? extends CoveredSwitchElement> type) {
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
        State next = sneaking ? State.CLOSED : switch (state) {
            case CLOSED -> State.OPEN;
            case OPEN -> State.ARMED;
            case ARMED -> State.CLOSED;
        };
        boolean changed = next != state;
        state = next;
        return changed;
    }

    @Override
    public boolean hasPersistentState() {
        return true;
    }

    @Override
    public int transmittedStrength() {
        return state == State.ARMED ? 15 : 0;
    }

    public State state() {
        return state;
    }

    public void setState(State state) {
        this.state = state == null ? State.CLOSED : state;
    }

    /** The cover's colour. */
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
        tag.putString(TAG_STATE, state.name());
        tag.putString(TAG_COLOR, color.getName());
    }

    @Override
    protected void loadOptions(CompoundTag tag, HolderLookup.Provider registries) {
        state = State.byName(tag.getString(TAG_STATE));
        setColor(DyeColor.byName(tag.getString(TAG_COLOR), DEFAULT_COLOR));
    }
}
