package dev.mccastle91.createcontrolling.panel.element;

/**
 * A small Create display board: always two rows of text, each showing its own
 * line. It spans the design area, from 1 px short of it on each side up to
 * its full width; the size is that width in px.
 */
public class DisplayBoardElement extends ReadoutElement {

    public static final int ROWS = 2;
    public static final int MAX_WIDTH = (int) (AREA_MAX - AREA_MIN);
    public static final int MIN_WIDTH = MAX_WIDTH - 2;

    public DisplayBoardElement(PanelElementType<? extends DisplayBoardElement> type) {
        super(type, ROWS, MIN_WIDTH);
    }

    @Override
    public int minSize() {
        return MIN_WIDTH;
    }

    @Override
    public int maxSize() {
        return MAX_WIDTH;
    }

    @Override
    public float widthFor(int size) {
        return size;
    }

    @Override
    public int columns() {
        return size();
    }
}
