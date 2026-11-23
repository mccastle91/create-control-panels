package dev.mccastle91.createcontrolling.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The shapes Create's menus are built from, drawn to any size: a coloured
 * title bar, a dark checkered body in a thin frame, and a light grey footer.
 * Create's own menus are fixed-size pictures, so each shape here is a small
 * texture cut in nine: its corners are drawn as they are, its edges and
 * middle stretched (or repeated) to fit. The textures are in
 * {@code textures/gui/console/}, so a resource pack can redraw any of them,
 * for every theme or for one (see {@link ConsoleStyle#texture}); the borders
 * that stay unstretched are the constants below.
 *
 * A texture may be any resolution: the sizes here are in screen pixels, and
 * the file is read as that many cells whatever its real size.
 */
public final class ConsoleGui {

    /** Title bar height, including the black line beneath it. */
    public static final int TITLE_HEIGHT = 15;
    public static final int FOOTER_HEIGHT = 24;

    /**
     * One nine-cut texture: its size in screen pixels, and how much of each
     * side keeps its size. {@code repeat} tiles the rest instead of stretching it.
     */
    private record Cut(String name, int width, int height, int left, int top, int right, int bottom, boolean repeat) {
        Cut(String name, int width, int height, int left, int top, int right, int bottom) {
            this(name, width, height, left, top, right, bottom, false);
        }
    }

    private static final Cut TITLE = new Cut("title", 16, 15, 3, 2, 3, 2);
    private static final Cut WINDOW_FRAME = new Cut("window_frame", 16, 16, 2, 1, 2, 0);
    private static final Cut PLAQUE = new Cut("plaque", 16, 16, 2, 2, 2, 2);
    private static final Cut PANEL = new Cut("panel", 16, 16, 3, 2, 3, 2);
    private static final Cut SLOT = new Cut("slot", 16, 16, 1, 1, 1, 1);
    private static final Cut FIELD = new Cut("field", 16, 14, 1, 3, 1, 1);
    private static final Cut SLIDER_TRACK = new Cut("slider_track", 16, 8, 1, 0, 1, 0, true);
    private static final Cut SLIDER_HANDLE = new Cut("slider_handle", 16, 14, 1, 2, 1, 2, true);
    private static final Cut SLIDER_HANDLE_HOVER = new Cut("slider_handle_hover", 16, 14, 1, 2, 1, 2, true);
    private static final Cut BUTTON = button("button");
    private static final Cut BUTTON_HOVER = button("button_hover");
    private static final Cut BUTTON_GREEN = button("button_green");
    private static final Cut BUTTON_GREEN_HOVER = button("button_green_hover");
    private static final Cut BUTTON_DISABLED = button("button_disabled");

    /** Side of the body tile, which repeats across the body. */
    private static final int BODY_TILE = 16;

    private ConsoleGui() {
    }

    private static Cut button(String name) {
        return new Cut(name, 16, 16, 1, 1, 1, 1);
    }

    /**
     * A whole window: title bar, body and footer, {@code height} tall in all.
     * The body is one pixel narrower than the bars on each side, as Create's is.
     */
    public static void window(GuiGraphics graphics, Font font, CasingTheme theme, Component title,
                              int x, int y, int width, int height) {
        titleBar(graphics, font, theme, title, x, y, width);
        int footerY = y + height - FOOTER_HEIGHT;
        body(graphics, theme, x + 1, y + TITLE_HEIGHT, width - 2, footerY - y - TITLE_HEIGHT);
        panel(graphics, theme, x, footerY, width, FOOTER_HEIGHT);
    }

    public static void titleBar(GuiGraphics graphics, Font font, CasingTheme theme, Component title, int x, int y, int width) {
        draw(graphics, theme, TITLE, x, y, width, TITLE_HEIGHT);
        graphics.drawString(font, title, x + (width - font.width(title)) / 2, y + 4, theme.titleText, false);
    }

    /** The dark checkered area behind a window's controls, in its thin grey frame. */
    public static void body(GuiGraphics graphics, CasingTheme theme, int x, int y, int width, int height) {
        bodyTile(graphics, theme, x + 2, y + 1, width - 4, height - 1);
        draw(graphics, theme, WINDOW_FRAME, x, y, width, height);
    }

    /** A small window with no bars: the body alone, closed off all round. */
    public static void plaque(GuiGraphics graphics, CasingTheme theme, int x, int y, int width, int height) {
        bodyTile(graphics, theme, x + 2, y + 2, width - 4, height - 4);
        draw(graphics, theme, PLAQUE, x, y, width, height);
    }

    /** The theme's body texture, repeated from the area's top-left corner. */
    private static void bodyTile(GuiGraphics graphics, CasingTheme theme, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0)
            return;
        graphics.blit(ConsoleStyle.texture(theme, "body"), x, y, 0f, 0f, width, height, BODY_TILE, BODY_TILE);
    }

    /** A raised light grey panel: Create's footer bar and its inventory window. */
    public static void panel(GuiGraphics graphics, @Nullable CasingTheme theme, int x, int y, int width, int height) {
        draw(graphics, theme, PANEL, x, y, width, height);
    }

    /** An item slot, sunk into whatever is behind it. */
    public static void slot(GuiGraphics graphics, @Nullable CasingTheme theme, int x, int y, int size) {
        draw(graphics, theme, SLOT, x, y, size, size);
    }

    /** A sunken grey field for a value or typed text, as in Create's Display Link menu. */
    public static void field(GuiGraphics graphics, @Nullable CasingTheme theme, int x, int y, int width, int height) {
        draw(graphics, theme, FIELD, x, y, width, height);
    }

    /**
     * A button of any size in the look of Create's: grey, blue under the
     * mouse, green while switched on.
     */
    public static void button(GuiGraphics graphics, @Nullable CasingTheme theme, int x, int y, int width, int height,
                              boolean hovered, boolean green, boolean active) {
        Cut cut = !active ? BUTTON_DISABLED
                : green ? (hovered ? BUTTON_GREEN_HOVER : BUTTON_GREEN)
                : hovered ? BUTTON_HOVER : BUTTON;
        draw(graphics, theme, cut, x, y, width, height);
    }

    /** The slider's track, 8 px tall. */
    public static void sliderTrack(GuiGraphics graphics, @Nullable CasingTheme theme, int x, int y, int width) {
        draw(graphics, theme, SLIDER_TRACK, x, y, width, SLIDER_TRACK.height);
    }

    /** The slider's handle, 14 px tall and as wide as the value it carries. */
    public static void sliderHandle(GuiGraphics graphics, @Nullable CasingTheme theme, int x, int y, int width, boolean lit) {
        draw(graphics, theme, lit ? SLIDER_HANDLE_HOVER : SLIDER_HANDLE, x, y, width, SLIDER_HANDLE.height);
    }

    // --- Nine-cut drawing -------------------------------------------------------

    private static void draw(GuiGraphics graphics, @Nullable CasingTheme theme, Cut cut, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0)
            return;
        ResourceLocation texture = ConsoleStyle.texture(theme, cut.name);
        // borders give way before they overlap, on something smaller than the texture's own borders
        int left = Math.min(cut.left, width / 2), right = Math.min(cut.right, width - left);
        int top = Math.min(cut.top, height / 2), bottom = Math.min(cut.bottom, height - top);
        int midW = width - left - right, midH = height - top - bottom;
        int srcMidW = cut.width - cut.left - cut.right, srcMidH = cut.height - cut.top - cut.bottom;
        int[] xs = { x, x + left, x + width - right }, ws = { left, midW, right };
        int[] us = { 0, cut.left, cut.width - right }, uws = { left, srcMidW, right };
        int[] ys = { y, y + top, y + height - bottom }, hs = { top, midH, bottom };
        int[] vs = { 0, cut.top, cut.height - bottom }, vhs = { top, srcMidH, bottom };
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 3; column++)
                piece(graphics, texture, cut, xs[column], ys[row], ws[column], hs[row],
                        us[column], vs[row], uws[column], vhs[row], column == 1, row == 1);
    }

    /** One of the nine pieces; the middle ones stretch, or repeat, along the axes they span. */
    private static void piece(GuiGraphics graphics, ResourceLocation texture, Cut cut, int x, int y, int width, int height,
                              int u, int v, int srcW, int srcH, boolean spansX, boolean spansY) {
        if (width <= 0 || height <= 0 || srcW <= 0 || srcH <= 0)
            return;
        if (!cut.repeat) {
            graphics.blit(texture, x, y, width, height, u, v, srcW, srcH, cut.width, cut.height);
            return;
        }
        int stepX = spansX ? srcW : width, stepY = spansY ? srcH : height;
        for (int dy = 0; dy < height; dy += stepY) {
            int h = Math.min(stepY, height - dy);
            for (int dx = 0; dx < width; dx += stepX) {
                int w = Math.min(stepX, width - dx);
                graphics.blit(texture, x + dx, y + dy, w, h, u, v, spansX ? w : srcW, spansY ? h : srcH,
                        cut.width, cut.height);
            }
        }
    }
}
