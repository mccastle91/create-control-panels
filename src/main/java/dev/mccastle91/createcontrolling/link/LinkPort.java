package dev.mccastle91.createcontrolling.link;

/**
 * One panel element's live connection to the wireless redstone network.
 * Created via {@link LinkPlatform}; must be {@link #remove()}d when the
 * element is deleted, re-keyed, or its panel unloads.
 */
public interface LinkPort {

    /** Re-push the transmitter's current strength to the network. */
    void notifyStrengthChanged();

    void remove();
}
