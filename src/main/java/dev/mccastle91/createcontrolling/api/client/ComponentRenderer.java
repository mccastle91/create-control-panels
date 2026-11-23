package dev.mccastle91.createcontrolling.api.client;

import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import net.minecraft.client.gui.GuiGraphics;

/**
 * How one kind of component is drawn: as a 3D part on the console, and flat
 * on the designer's face. Registered per component type with
 * {@link ConsoleClientApi#registerRenderer}.
 */
public interface ComponentRenderer<T extends PanelElement> {

    /**
     * Draws the part on the console. The painter's origin is already at the
     * part's far-left corner: x runs right across its width, y down its depth
     * towards the player, z out of the face, all in face pixels (1/16 block).
     * Draw only from the element's own state: this runs every frame.
     */
    void render(T element, PartRenderContext context);

    /**
     * The colour (ARGB) of the part's rectangle on the designer's face;
     * 0 for the designer's plain grey.
     */
    default int designerColor(T element) {
        return 0;
    }

    /**
     * Draws over the part's rectangle on the designer's face — a mark that
     * tells it from other parts or shows its state. The rectangle is
     * ({@code x0}, {@code y0}) to ({@code x1}, {@code y1}) in screen pixels,
     * 10 per face pixel.
     */
    default void designerGlyph(T element, GuiGraphics graphics, int x0, int y0, int x1, int y1) {
    }
}
