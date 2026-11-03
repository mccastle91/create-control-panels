package dev.mccastle91.createcontrolling.panel.element;

/** Two nixie tubes showing its frequency's strength as a number, 0-15. */
public class NixieElement extends ReceiverElement {

    public NixieElement(PanelElementType<? extends NixieElement> type) {
        super(type);
    }

    /** The two digits shown, most significant first; a leading zero is blank (-1). */
    public int[] digits() {
        int value = receivedStrength();
        return new int[] { value >= 10 ? value / 10 : -1, value % 10 };
    }
}
