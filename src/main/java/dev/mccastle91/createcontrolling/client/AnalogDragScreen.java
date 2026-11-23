package dev.mccastle91.createcontrolling.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mccastle91.createcontrolling.network.CCNetwork;
import dev.mccastle91.createcontrolling.panel.ConsoleCasing;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.PanelEditOps;
import dev.mccastle91.createcontrolling.panel.element.AnalogElement;
import dev.mccastle91.createcontrolling.panel.element.PartSound;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/**
 * Open only while the use key is held on a knob or slider. Dragging the mouse
 * sets the control: right (up, for a vertical slider or a lever) raises it, in steps —
 * the mouse has to travel a set distance for each of the 16 values, and each
 * one clicks, like the detents of a real rotary switch. Letting go closes it.
 *
 * It draws nothing but the value under the crosshair, so the world stays in
 * view and the part on the console is what you watch move — unless the dial
 * window is switched on in the client config, in which case a copy of the
 * part (see {@link AnalogDial}) is shown to drag instead.
 *
 * A screen rather than a key handler because a screen is the one thing that
 * stops the camera turning with the mouse on both Minecraft versions.
 */
public class AnalogDragScreen extends Screen {

    /** Held no longer than this, without the value moving, is a tap: one step up. */
    private static final int TAP_TICKS = 5;
    /** The dial window waits this long, so a tap doesn't flash it. */
    private static final int POPUP_DELAY_TICKS = 3;
    /** The first frames' mouse movement is the cursor being captured, not the hand. */
    private static final int SETTLE_FRAMES = 2;

    /** Screen pixels per face pixel in the dial window. */
    private static final float DIAL_SCALE = 14f;
    private static final int PAD = 8;

    private final BlockPos pos;
    private final UUID elementId;
    private final boolean popup;
    private final boolean vertical;

    private int value;
    /** The control's own limits (see AnalogElement): the drag stops where the control does. */
    private int min;
    private int max = AnalogElement.MAX_VALUE;
    /** Mouse travel towards the next step, in window pixels. */
    private double travel;
    private double lastX = Double.NaN;
    private double lastY = Double.NaN;
    private int frames;
    private int ticks;
    private boolean changed;

    public AnalogDragScreen(BlockPos pos, AnalogElement element, boolean popup) {
        super(Component.empty());
        this.pos = pos;
        this.elementId = element.id();
        this.popup = popup;
        this.vertical = element.dragsVertically();
        this.value = element.value();
        this.min = element.min();
        this.max = element.max();
    }

    private AnalogElement element() {
        if (minecraft == null || minecraft.level == null
                || !(minecraft.level.getBlockEntity(pos) instanceof PanelBlockEntity panel))
            return null;
        return panel.getLayout().elementById(elementId).orElse(null) instanceof AnalogElement analog ? analog : null;
    }

    @Override
    protected void init() {
        // hidden and unbounded: the drag isn't stopped by the window's edge
        GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
    }

    /** Whether the use key (a mouse button unless rebound) is still down; key mappings don't track it under a screen. */
    private boolean useHeld() {
        InputConstants.Key key = minecraft.options.keyUse.getKey();
        long window = minecraft.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE)
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        if (key.getType() == InputConstants.Type.KEYSYM)
            return InputConstants.isKeyDown(window, key.getValue());
        return false;
    }

    /**
     * Window pixels of travel per step: a steady hand-width for the whole
     * range at any resolution, with steps wide enough to stop on.
     */
    private double stepDistance() {
        return Math.max(10, minecraft.getWindow().getScreenHeight() / 64.0);
    }

    @Override
    public void tick() {
        ticks++;
        if (element() == null) {
            minecraft.setScreen(null);
            return;
        }
        if (!useHeld()) {
            if (!changed && ticks <= TAP_TICKS)
                setValue(value + 1); // a tap steps up one, as a click always has
            minecraft.setScreen(null);
        }
    }

    private void trackMouse() {
        double x = minecraft.mouseHandler.xpos(), y = minecraft.mouseHandler.ypos();
        if (frames++ >= SETTLE_FRAMES && !Double.isNaN(lastX))
            travel += vertical ? lastY - y : x - lastX;
        lastX = x;
        lastY = y;

        double step = stepDistance();
        while (travel >= step && value < max) {
            travel -= step;
            setValue(value + 1);
        }
        while (travel <= -step && value > min) {
            travel += step;
            setValue(value - 1);
        }
        // pushing on past either end doesn't bank travel to be undone later
        if (value == max)
            travel = Math.min(travel, 0);
        if (value == min)
            travel = Math.max(travel, 0);
    }

    private void setValue(int requested) {
        int clamped = Mth.clamp(requested, min, max);
        if (clamped == value)
            return;
        value = clamped;
        changed = true;
        CompoundTag op = new CompoundTag();
        op.putString(PanelEditOps.KEY_OP, PanelEditOps.OP_SET_VALUE);
        op.putUUID("Id", elementId);
        op.putInt("Value", value);
        CCNetwork.sendEditToServer(pos, op);
        // a soft click per step, rising a little with the value; heard here at once,
        // while the console plays the same click to everyone else nearby
        minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(PartSound.Kind.DETENT.event(), PartSound.detentPitch(value), PartSound.detentVolume()));
    }

    //? if >=1.21 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scroll(scrollY);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return scroll(delta);
    }
    *///?}

    /** The wheel works too while held: one step a notch. */
    private boolean scroll(double delta) {
        int step = (int) Math.signum(delta);
        if (step != 0)
            setValue(value + step);
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // no super: nothing dims or blurs the world behind
        AnalogElement element = element();
        if (element == null)
            return;
        min = element.min();
        max = element.max();
        trackMouse();
        if (popup && (changed || ticks >= POPUP_DELAY_TICKS))
            renderDial(graphics, element);
        else if (changed)
            graphics.drawCenteredString(font, Integer.toString(value), width / 2, height / 2 + 10, StyleColor.TEXT.argb());
    }

    /** The dial window: the part itself, drawn large, with its value beneath. */
    private void renderDial(GuiGraphics graphics, AnalogElement element) {
        int dialWidth = Math.round(element.width() * DIAL_SCALE), dialHeight = Math.round(element.depth() * DIAL_SCALE);
        int boxWidth = dialWidth + 2 * PAD, boxHeight = dialHeight + 2 * PAD + 12;
        int x0 = width / 2 - boxWidth / 2;
        // just under the crosshair, or centred if the window is too short for that
        int y0 = Math.min(height / 2 + 14, height - boxHeight - 4);
        ConsoleCasing casing = minecraft.level.getBlockEntity(pos) instanceof PanelBlockEntity panel
                ? panel.casing() : ConsoleCasing.DEFAULT;
        CasingTheme theme = CasingTheme.of(casing);
        ConsoleGui.plaque(graphics, theme, x0, y0, boxWidth, boxHeight);
        AnalogDial.draw(graphics, element, casing, value, x0 + PAD, y0 + PAD, DIAL_SCALE);
        graphics.drawCenteredString(font, Integer.toString(value), width / 2, y0 + PAD + dialHeight + 5, theme.accent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
