package dev.mccastle91.createcontrolling.client;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A button in the look of Create's menus. With an icon it is Create's own
 * 18 px button and icon; with text it is the same button drawn to size (see
 * {@link ConsoleGui#button}). Still a vanilla Button underneath, so focus,
 * narration, tooltips and the click sound are the game's.
 */
public class ThemedButton extends Button {

    /** Create's icon buttons are this size, whatever is asked for. */
    public static final int ICON_SIZE = 18;
    /** Room a text button keeps clear at its edges, both sides together. */
    public static final int TEXT_MARGIN = 4;

    @Nullable
    private final AllIcons icon;
    /** Shown green: the option this button stands for is switched on. */
    private boolean green;
    /** Whose textures the button wears; asked each frame, since a console's casing can change under an open screen. */
    private java.util.function.Supplier<CasingTheme> theme = () -> null;

    public ThemedButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.icon = null;
    }

    /** An icon button; {@code name} is what it is called in its tooltip and to the narrator. */
    public ThemedButton(int x, int y, AllIcons icon, Component name, OnPress onPress) {
        super(x, y, ICON_SIZE, ICON_SIZE, name, onPress, DEFAULT_NARRATION);
        this.icon = icon;
        setTooltip(Tooltip.create(name));
    }

    public ThemedButton tooltip(Component text) {
        setTooltip(Tooltip.create(text));
        return this;
    }

    public ThemedButton themed(java.util.function.Supplier<CasingTheme> theme) {
        this.theme = theme;
        return this;
    }

    public void setGreen(boolean green) {
        this.green = green;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHovered();
        if (icon != null) {
            AllGuiTextures background = !active ? AllGuiTextures.BUTTON_DISABLED
                    : green ? AllGuiTextures.BUTTON_GREEN
                    : hovered ? AllGuiTextures.BUTTON_HOVER : AllGuiTextures.BUTTON;
            background.render(graphics, getX(), getY());
            icon.render(graphics, getX() + 1, getY() + 1);
            return;
        }
        ConsoleGui.button(graphics, theme.get(), getX(), getY(), width, height, hovered, green, active);
        // a label longer than the button (in another language) is drawn smaller rather than spilling out
        ConsoleGui.centeredFitted(graphics, Minecraft.getInstance().font, getMessage(), getX() + width / 2,
                getY() + (height - 8) / 2, width - TEXT_MARGIN, (active ? StyleColor.BUTTON_TEXT : StyleColor.BUTTON_TEXT_DISABLED).argb());
    }
}
