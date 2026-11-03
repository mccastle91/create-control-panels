package dev.mccastle91.createcontrolling.compat.jei;

import dev.mccastle91.createcontrolling.client.PanelDesignerScreen;
import dev.mccastle91.createcontrolling.util.McCompat;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Optional JEI integration, found by JEI's annotation scan and never loaded
 * without it. The Console Designer is a plain screen, not a container, so JEI
 * is told where it sits (to show its item list beside it) and which areas take
 * dragged items: the selected component's two frequency slots, in the designer
 * or in the frequency chooser while it is open.
 */
@JeiPlugin
public class CCJeiPlugin implements IModPlugin {

    private static final ResourceLocation ID = McCompat.id("create_controlling", "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler(PanelDesignerScreen.class, CCJeiPlugin::properties);
        registration.addGhostIngredientHandler(PanelDesignerScreen.class, new FrequencyGhostHandler());
    }

    /**
     * A snapshot, not a live view: JEI compares the last properties with new
     * ones to notice the screen change (the chooser opening or closing). None
     * until the screen is sized: JEI asks as it opens, before init(), and logs
     * a zero-sized screen as an error.
     */
    @Nullable
    private static IGuiProperties properties(PanelDesignerScreen screen) {
        if (screen.width <= 1 || screen.height <= 1)
            return null;
        Rect2i area = screen.guiArea();
        int width = screen.width, height = screen.height;
        return new IGuiProperties() {
            //? if >=1.21 {
            @Override public Class<? extends Screen> screenClass() { return screen.getClass(); }
            @Override public int guiLeft() { return area.getX(); }
            @Override public int guiTop() { return area.getY(); }
            @Override public int guiXSize() { return area.getWidth(); }
            @Override public int guiYSize() { return area.getHeight(); }
            @Override public int screenWidth() { return width; }
            @Override public int screenHeight() { return height; }
            //?} else {
            /*@Override public Class<? extends Screen> getScreenClass() { return screen.getClass(); }
            @Override public int getGuiLeft() { return area.getX(); }
            @Override public int getGuiTop() { return area.getY(); }
            @Override public int getGuiXSize() { return area.getWidth(); }
            @Override public int getGuiYSize() { return area.getHeight(); }
            @Override public int getScreenWidth() { return width; }
            @Override public int getScreenHeight() { return height; }
            *///?}
        };
    }
}
