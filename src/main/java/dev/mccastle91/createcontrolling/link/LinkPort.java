package dev.mccastle91.createcontrolling.link;

/**
 * One panel element's live connection to the wireless redstone network.
 * Created via {@link LinkPlatform}; must be {@link #remove()}d when the
 * element is deleted, re-keyed, or its panel unloads.
 */
public interface LinkPort {

    /** Re-push the transmitter's current strength to the network. */
    void notifyStrengthChanged();

    /**
     * The element has moved to another block position: a receiver reads the
     * network again from there, a transmitter that is sending tells the
     * receivers now in its range.
     */
    void positionChanged();

    void remove();
}
