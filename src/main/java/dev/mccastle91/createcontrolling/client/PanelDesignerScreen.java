package dev.mccastle91.createcontrolling.client;

import dev.mccastle91.createcontrolling.panel.ComponentCosts;
import dev.mccastle91.createcontrolling.panel.ConsoleShape;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.PanelEditOps;
import dev.mccastle91.createcontrolling.panel.PanelLayout;
import dev.mccastle91.createcontrolling.panel.element.AnalogElement;
import dev.mccastle91.createcontrolling.panel.element.BarMeterElement;
import dev.mccastle91.createcontrolling.panel.element.ButtonElement;
import dev.mccastle91.createcontrolling.panel.element.ColorableElement;
import dev.mccastle91.createcontrolling.panel.element.CoveredSwitchElement;
import dev.mccastle91.createcontrolling.panel.element.LabelElement;
import dev.mccastle91.createcontrolling.panel.element.LatchingButtonElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElementType;
import dev.mccastle91.createcontrolling.panel.element.ReceiverElement;
import dev.mccastle91.createcontrolling.panel.element.SwitchElement;
import dev.mccastle91.createcontrolling.util.McCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Console Designer: arranges components on the 16×16 px face (0.5 px
 * snap), configures them, and shapes the console itself. All mutations go to
 * the server as edit ops; the screen renders whatever the client block entity
 * holds, so server echoes are the single source of truth.
 *
 * Layout: palette (with costs) on the left, the face in the middle with the
 * console's angle and front height beneath it, the selection on the right.
 */
public class PanelDesignerScreen extends Screen {

    /** Screen pixels per face pixel; a 0.5 px snap step is 5 screen pixels. */
    private static final int SCALE = 10;
    private static final int CANVAS = 16 * SCALE;
    private static final int COLUMN = 120;
    private static final int GAP = 10;

    private static final int PLATE = 0xFF48443F;
    private static final int GOLD_TEXT = 0xE0B160;
    /**
     * Frequency slot colours, from the two markers on Create's Redstone Link
     * texture: red for the first slot, blue for the second.
     */
    private static final int[] FREQ_COLORS = { 0xFF97333B, 0xFF5371C6 };
    private static final int[] FREQ_COLORS_HOVER = { 0xFFC4505A, 0xFF7E98E0 };

    private final BlockPos panelPos;
    private UUID selectedId;

    private int canvasX;
    private int canvasY;
    private int sideX;
    private int infoY;
    private int freqLabelY;
    private int freqSlotY;
    private int optionsY;
    /** The frequency chooser is open; the designer hides behind it. */
    private boolean choosing;
    /** An inventory item being dragged in the chooser towards a frequency slot. */
    private ItemStack carried = ItemStack.EMPTY;

    // Drag-to-move: the grab offset keeps the component under the cursor
    private UUID draggingId;
    private float dragOffsetX;
    private float dragOffsetY;
    private float previewX;
    private float previewY;
    private boolean dragMoved;

    private final List<Button> palette = new ArrayList<>();
    private final List<Button> selectionOnly = new ArrayList<>();
    private final List<Button> colorOnly = new ArrayList<>();
    private final List<Button> pulseOnly = new ArrayList<>();
    private final List<Button> labelOnly = new ArrayList<>();
    private final List<Button> barOnly = new ArrayList<>();
    private EditBox labelTextBox;
    private AngleSlider angleSlider;
    /** Wall consoles only: which edge stands out from the wall. */
    private Button slopeButton;
    private final List<Button> shapeOnly = new ArrayList<>();

    public PanelDesignerScreen(BlockPos panelPos) {
        super(Component.translatable("gui.create_controlling.designer"));
        this.panelPos = panelPos;
    }

    private PanelBlockEntity panel() {
        if (minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(panelPos) instanceof PanelBlockEntity panel)
            return panel;
        return null;
    }

    private PanelLayout layout() {
        PanelBlockEntity panel = panel();
        return panel == null ? null : panel.getLayout();
    }

    private PanelElement selected() {
        PanelLayout layout = layout();
        if (layout == null || selectedId == null)
            return null;
        return layout.elementById(selectedId).orElse(null);
    }

    private boolean isWall() {
        PanelBlockEntity panel = panel();
        return panel != null && panel.getBlockState().hasProperty(FaceAttachedHorizontalDirectionalBlock.FACE)
                && panel.getBlockState().getValue(FaceAttachedHorizontalDirectionalBlock.FACE) == AttachFace.WALL;
    }

    @Override
    protected void init() {
        palette.clear();
        selectionOnly.clear();
        colorOnly.clear();
        pulseOnly.clear();
        labelOnly.clear();
        barOnly.clear();
        shapeOnly.clear();

        int total = COLUMN + GAP + CANVAS + GAP + COLUMN;
        int paletteX = width / 2 - total / 2;
        canvasX = paletteX + COLUMN + GAP;
        canvasY = Math.max(24, height / 2 - CANVAS / 2 - 20);
        sideX = canvasX + CANVAS + GAP;

        // Palette: one add-button per component, cost in the tooltip
        int index = 0;
        for (PanelElementType<?> type : PanelElementType.all()) {
            String id = type.id();
            palette.add(addRenderableWidget(Button.builder(Component.translatable("gui.create_controlling.add." + id), b -> sendAdd(id))
                    .bounds(paletteX + (index % 2) * 60, canvasY + (index / 2) * 16, 58, 14)
                    .tooltip(Tooltip.create(costTooltip(type)))
                    .build()));
            index++;
        }

        // Console shape, beneath the face
        int shapeY = canvasY + CANVAS + 6;
        angleSlider = addRenderableWidget(new AngleSlider(canvasX, shapeY, CANVAS, 14));
        // a wall console can raise either edge: a third button swaps which
        int buttons = isWall() ? 3 : 2;
        int buttonWidth = (CANVAS - 2 * (buttons - 1)) / buttons;
        shapeOnly.add(addRenderableWidget(Button.builder(Component.literal("Front -"), b -> sendFront(-0.5f))
                .bounds(canvasX, shapeY + 16, buttonWidth, 14).build()));
        shapeOnly.add(addRenderableWidget(Button.builder(Component.literal("Front +"), b -> sendFront(0.5f))
                .bounds(canvasX + buttonWidth + 2, shapeY + 16, buttonWidth, 14).build()));
        slopeButton = null;
        if (isWall())
            slopeButton = addRenderableWidget(Button.builder(Component.empty(), b -> sendSlopeSwap())
                    .bounds(canvasX + 2 * (buttonWidth + 2), shapeY + 16, CANVAS - 2 * (buttonWidth + 2), 14)
                    .tooltip(Tooltip.create(Component.translatable("gui.create_controlling.raised_edge.tooltip")))
                    .build());

        // Selection column
        int y = canvasY;
        infoY = y;
        y += 12;
        selectionOnly.add(addRenderableWidget(Button.builder(
                        Component.translatable("gui.create_controlling.delete"), b -> sendDelete())
                .bounds(sideX, y, COLUMN - 2, 14).build()));
        y += 16 + 6;
        freqLabelY = y;
        y += 11;
        freqSlotY = y;
        y += 18 + 6;
        optionsY = y;
        y += 11;

        colorOnly.add(addRenderableWidget(Button.builder(
                        Component.translatable("gui.create_controlling.color"), b -> cycleColor())
                .bounds(sideX, y, 58, 14).build()));
        pulseOnly.add(addRenderableWidget(Button.builder(Component.literal("Pulse -"), b -> adjustPulse(-5))
                .bounds(sideX, y + 16, 58, 14).build()));
        pulseOnly.add(addRenderableWidget(Button.builder(Component.literal("Pulse +"), b -> adjustPulse(5))
                .bounds(sideX + 60, y + 16, 58, 14).build()));
        barOnly.add(addRenderableWidget(Button.builder(Component.literal("Length -"), b -> adjustBarLength(-1))
                .bounds(sideX, y + 16, 58, 14).build()));
        barOnly.add(addRenderableWidget(Button.builder(Component.literal("Length +"), b -> adjustBarLength(1))
                .bounds(sideX + 60, y + 16, 58, 14).build()));

        labelTextBox = new EditBox(font, sideX, y, COLUMN - 2, 14, Component.empty());
        labelTextBox.setMaxLength(LabelElement.MAX_TEXT_LENGTH);
        addRenderableWidget(labelTextBox);
        labelOnly.add(addRenderableWidget(Button.builder(Component.translatable("gui.create_controlling.apply"),
                        b -> sendOption("text", tag -> tag.putString("Value", labelTextBox.getValue())))
                .bounds(sideX, y + 16, 58, 14).build()));
        labelOnly.add(addRenderableWidget(Button.builder(
                        Component.translatable("gui.create_controlling.align"), b -> cycleAlignment())
                .bounds(sideX + 60, y + 16, 58, 14).build()));
        labelOnly.add(addRenderableWidget(Button.builder(Component.literal("Size -"), b -> adjustScale(-1))
                .bounds(sideX, y + 32, 58, 14).build()));
        labelOnly.add(addRenderableWidget(Button.builder(Component.literal("Size +"), b -> adjustScale(1))
                .bounds(sideX + 60, y + 32, 58, 14).build()));
        labelOnly.add(addRenderableWidget(Button.builder(Component.literal("Width -"), b -> adjustLabelWidth(-1))
                .bounds(sideX, y + 48, 58, 14).build()));
        labelOnly.add(addRenderableWidget(Button.builder(Component.literal("Width +"), b -> adjustLabelWidth(1))
                .bounds(sideX + 60, y + 48, 58, 14).build()));
        labelOnly.add(addRenderableWidget(Button.builder(
                        Component.translatable("gui.create_controlling.text_color"), b -> cycleTextColor())
                .bounds(sideX, y + 64, COLUMN - 2, 14).build()));
    }

    private static Component costTooltip(PanelElementType<?> type) {
        MutableComponent text = Component.translatable("gui.create_controlling.element." + type.id()).copy();
        List<ComponentCosts.Cost> cost = ComponentCosts.costOf(type);
        text.append("\n").append(Component.translatable("gui.create_controlling.cost"));
        if (cost.isEmpty())
            text.append(" ").append(Component.translatable("gui.create_controlling.free"));
        for (ComponentCosts.Cost line : cost)
            text.append("\n  " + line.count() + "× ").append(line.name());
        return text;
    }

    // --- Edit ops ------------------------------------------------------------------

    private void send(CompoundTag op) {
        if (CCClientHooks.SEND_EDIT != null)
            CCClientHooks.SEND_EDIT.accept(panelPos, op);
    }

    private CompoundTag op(String kind) {
        CompoundTag tag = new CompoundTag();
        tag.putString(PanelEditOps.KEY_OP, kind);
        if (selectedId != null)
            tag.putUUID("Id", selectedId);
        return tag;
    }

    private void sendAdd(String typeId) {
        CompoundTag tag = new CompoundTag();
        tag.putString(PanelEditOps.KEY_OP, PanelEditOps.OP_ADD);
        tag.putString("Type", typeId);
        tag.putFloat("X", 6);
        tag.putFloat("Y", 6);
        send(tag);
    }

    private void sendMove(float x, float y) {
        CompoundTag tag = op(PanelEditOps.OP_MOVE);
        tag.putFloat("X", x);
        tag.putFloat("Y", y);
        send(tag);
    }

    private void sendDelete() {
        if (selected() == null)
            return;
        send(op(PanelEditOps.OP_DELETE));
        selectedId = null;
    }

    private void sendShape(float angle, float front, boolean reversed) {
        CompoundTag tag = new CompoundTag();
        tag.putString(PanelEditOps.KEY_OP, PanelEditOps.OP_SET_SHAPE);
        tag.putFloat("Angle", angle);
        tag.putFloat("Front", front);
        tag.putBoolean("Reversed", reversed);
        send(tag);
    }

    private void sendFront(float delta) {
        PanelBlockEntity panel = panel();
        if (panel != null)
            sendShape(panel.shape().angle(), panel.shape().frontHeight() + delta, wallReversed(panel));
    }

    /** Swaps a wall console's raised edge; a flat one tips to the first preset that way. */
    private void sendSlopeSwap() {
        PanelBlockEntity panel = panel();
        if (panel == null)
            return;
        ConsoleShape shape = panel.shape();
        float angle = shape.isFlat() ? ConsoleShape.PRESETS[1] : shape.angle();
        sendShape(angle, shape.frontHeight(), shape.isFlat() ? !wallReversed(panel) : !shape.reversed());
    }

    /**
     * The raised edge a wall console's next shape keeps. A flat face has none,
     * so the swap button remembers the last one asked for until it tips.
     */
    private boolean wallReversed(PanelBlockEntity panel) {
        if (!panel.shape().isFlat())
            flatWallReversed = panel.shape().reversed();
        return flatWallReversed;
    }

    private boolean flatWallReversed;

    /**
     * Frequencies are identities, not inventory — the picked item is cloned,
     * nothing is consumed, so no container menu is needed.
     */
    private void sendFrequency(int slot, ItemStack stack) {
        if (selected() == null || minecraft == null || minecraft.player == null)
            return;
        CompoundTag tag = op(PanelEditOps.OP_SET_FREQUENCY);
        tag.putInt("Slot", slot);
        if (!stack.isEmpty())
            tag.put("Item", McCompat.saveStack(stack.copyWithCount(1), minecraft.player.level().registryAccess()));
        send(tag);
    }

    // --- JEI (compat/jei calls these; the screen itself never touches JEI) -----

    /**
     * What the screen occupies, so JEI's overlay keeps clear of it: the whole
     * designer, or only the chooser while it is open. The designer hides then,
     * which leaves JEI room for its item list at any GUI scale.
     */
    public Rect2i guiArea() {
        if (choosing)
            return new Rect2i(chooserX(), chooserY(), CHOOSER_WIDTH, CHOOSER_HEIGHT);
        // the face and its shape controls, or the option buttons beside it if lower
        int height = Math.max(CANVAS + 6 + 30, optionsY + 11 + 78 - canvasY);
        return new Rect2i(canvasX - GAP - COLUMN, canvasY, COLUMN + GAP + CANVAS + GAP + COLUMN, height);
    }

    /**
     * Where an item dragged from JEI sets frequency slot 0 or 1, or null: the
     * slot in the chooser while it is open, else the slot in the designer.
     */
    public Rect2i frequencyDropArea(int slot) {
        if (choosing)
            return new Rect2i(chooserSlotX(slot), chooserY() + CHOOSER_SLOTS_Y, 18, 18);
        PanelElement element = selected();
        if (element == null || !hasFrequency(element))
            return null;
        return new Rect2i(sideX + slot * 22, freqSlotY, 18, 18);
    }

    /** Sets a frequency slot from an item dragged in from JEI. */
    public void dropFrequency(int slot, ItemStack stack) {
        sendFrequency(slot, stack);
    }

    private void sendOption(String key, java.util.function.Consumer<CompoundTag> valueWriter) {
        if (selected() == null)
            return;
        CompoundTag tag = op(PanelEditOps.OP_SET_OPTION);
        tag.putString("Key", key);
        valueWriter.accept(tag);
        send(tag);
    }

    private void adjustPulse(int delta) {
        if (selected() instanceof ButtonElement button)
            sendOption("pulse_ticks", tag -> tag.putInt("Value", button.pulseTicks() + delta));
    }

    private void adjustBarLength(float delta) {
        if (selected() instanceof BarMeterElement bar)
            sendOption("length", tag -> tag.putFloat("Value", bar.width() + delta));
    }

    private void cycleColor() {
        if (selected() instanceof ColorableElement colorable) {
            DyeColor next = DyeColor.byId((colorable.color().getId() + 1) % 16);
            sendOption("color", tag -> tag.putString("Value", next.getName()));
        }
    }

    private void adjustScale(int delta) {
        if (selected() instanceof LabelElement label)
            sendOption("scale", tag -> tag.putInt("Value", label.scale() + delta));
    }

    private void adjustLabelWidth(float delta) {
        if (selected() instanceof LabelElement label)
            sendOption("width", tag -> tag.putFloat("Value", label.width() + delta));
    }

    private void cycleTextColor() {
        if (selected() instanceof LabelElement label) {
            DyeColor[] values = DyeColor.values();
            int nextIndex = 0;
            for (int i = 0; i < values.length; i++)
                if ((values[i].getTextColor() & 0xFFFFFF) == label.color()) {
                    nextIndex = i + 1;
                    break;
                }
            int color = values[nextIndex % values.length].getTextColor() & 0xFFFFFF;
            sendOption("text_color", tag -> tag.putInt("Value", color));
        }
    }

    private void cycleAlignment() {
        if (selected() instanceof LabelElement label) {
            var values = LabelElement.Alignment.values();
            var next = values[(label.alignment().ordinal() + 1) % values.length];
            sendOption("alignment", tag -> tag.putString("Value", next.name()));
        }
    }

    // --- Input ---------------------------------------------------------------------

    // Frequency chooser: title row, the two slots, then the inventory (offsets from its corner)
    private static final int CHOOSER_WIDTH = 174;
    private static final int CHOOSER_HEIGHT = 124;
    private static final int CHOOSER_SLOTS_Y = 20;
    private static final int CHOOSER_CELLS_X = 6;
    private static final int CHOOSER_CELLS_Y = 46;

    private int chooserX() {
        return width / 2 - CHOOSER_WIDTH / 2;
    }

    private int chooserY() {
        return height / 2 - CHOOSER_HEIGHT / 2;
    }

    private int chooserSlotX(int slot) {
        return chooserX() + CHOOSER_WIDTH / 2 - 20 + slot * 22;
    }

    /** The chooser's frequency slot under the mouse, or -1. */
    private int chooserSlotAt(double mouseX, double mouseY) {
        int y = chooserY() + CHOOSER_SLOTS_Y;
        for (int slot = 0; slot < 2; slot++) {
            int x = chooserSlotX(slot);
            if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18)
                return slot;
        }
        return -1;
    }

    private boolean overDoneButton(double mouseX, double mouseY) {
        int ox = chooserX(), oy = chooserY();
        return mouseX >= ox + CHOOSER_WIDTH - 42 && mouseX < ox + CHOOSER_WIDTH - 4 && mouseY >= oy + 4 && mouseY < oy + 18;
    }

    private void closeChooser() {
        choosing = false;
        carried = ItemStack.EMPTY;
    }

    private float faceX(double mouseX) {
        return (float) ((mouseX - canvasX) / SCALE);
    }

    private float faceY(double mouseY) {
        return (float) ((mouseY - canvasY) / SCALE);
    }

    private static float snap(float px) {
        return Math.round(px / PanelElement.SNAP) * PanelElement.SNAP;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (choosing) {
            handleChooserClick(mouseX, mouseY, button);
            return true;
        }

        PanelElement element = selected();
        if (element != null && hasFrequency(element) && mouseY >= freqSlotY && mouseY < freqSlotY + 18) {
            for (int slot = 0; slot < 2; slot++) {
                int x = sideX + slot * 22;
                if (mouseX >= x && mouseX < x + 18) {
                    if (button == 1)
                        sendFrequency(slot, ItemStack.EMPTY); // right-click clears
                    else
                        choosing = true;
                    return true;
                }
            }
        }

        if (button == 0 && mouseX >= canvasX && mouseX < canvasX + CANVAS && mouseY >= canvasY && mouseY < canvasY + CANVAS) {
            PanelLayout layout = layout();
            float fx = faceX(mouseX), fy = faceY(mouseY);
            PanelElement hit = layout == null ? null : layout.elementAt(fx, fy).orElse(null);
            selectedId = hit == null ? null : hit.id();
            if (hit != null) {
                draggingId = hit.id();
                dragOffsetX = fx - hit.x();
                dragOffsetY = fy - hit.y();
                previewX = hit.x();
                previewY = hit.y();
                dragMoved = false;
                if (hit instanceof LabelElement label && labelTextBox != null)
                    labelTextBox.setValue(label.text());
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (choosing)
            return true; // a carried item just follows the mouse
        if (button == 0 && draggingId != null) {
            PanelLayout layout = layout();
            PanelElement element = layout == null ? null : layout.elementById(draggingId).orElse(null);
            if (element != null) {
                previewX = element.clampX(snap(faceX(mouseX) - dragOffsetX));
                previewY = element.clampY(snap(faceY(mouseY) - dragOffsetY));
                dragMoved = true;
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (choosing) {
            // dropping a carried item on a frequency slot sets it; anywhere else lets it go
            int slot = chooserSlotAt(mouseX, mouseY);
            if (button == 0 && slot >= 0 && !carried.isEmpty())
                sendFrequency(slot, carried);
            carried = ItemStack.EMPTY;
            return true;
        }
        if (button == 0 && draggingId != null) {
            PanelLayout layout = layout();
            PanelElement element = layout == null ? null : layout.elementById(draggingId).orElse(null);
            if (element != null && dragMoved && (previewX != element.x() || previewY != element.y())
                    && layout.isAreaFree(previewX, previewY, element.width(), element.depth(),
                            element.id(), element instanceof LabelElement))
                sendMove(previewX, previewY);
            draggingId = null;
            dragMoved = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * Chooser clicks: pick an inventory item up to drag it onto a slot,
     * right-click a slot to clear it, Done or a click outside closes.
     */
    private void handleChooserClick(double mouseX, double mouseY, int button) {
        if (minecraft == null || minecraft.player == null) {
            closeChooser();
            return;
        }
        int ox = chooserX(), oy = chooserY();
        if (overDoneButton(mouseX, mouseY)) {
            closeChooser();
            return;
        }
        int slot = chooserSlotAt(mouseX, mouseY);
        if (slot >= 0) {
            if (button == 1)
                sendFrequency(slot, ItemStack.EMPTY);
            return;
        }
        int cellsX = ox + CHOOSER_CELLS_X, cellsY = oy + CHOOSER_CELLS_Y;
        if (mouseX >= cellsX && mouseX < cellsX + 9 * 18 && mouseY >= cellsY && mouseY < cellsY + 4 * 18) {
            int column = (int) ((mouseX - cellsX) / 18);
            int row = (int) ((mouseY - cellsY) / 18);
            ItemStack stack = minecraft.player.getInventory().getItem(inventoryIndex(row, column));
            if (button == 0 && !stack.isEmpty())
                carried = stack.copyWithCount(1); // nothing leaves the inventory
            return;
        }
        if (mouseX < ox || mouseX >= ox + CHOOSER_WIDTH || mouseY < oy || mouseY >= oy + CHOOSER_HEIGHT)
            closeChooser();
    }

    /** Display rows 0-2 = main inventory, row 3 = hotbar (standard layout). */
    private static int inventoryIndex(int row, int column) {
        return row == 3 ? column : 9 + row * 9 + column;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (choosing && keyCode == 256) { // ESC closes the chooser first
            closeChooser();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static boolean hasFrequency(PanelElement element) {
        return element.isTransmitter() || element.isReceiver();
    }

    // --- Render --------------------------------------------------------------------

    private static int argb(DyeColor color, float factor) {
        int rgb = McCompat.dyeRgb(color);
        int r = (int) Math.min(255, ((rgb >> 16) & 0xFF) * factor);
        int g = (int) Math.min(255, ((rgb >> 8) & 0xFF) * factor);
        int b = (int) Math.min(255, (rgb & 0xFF) * factor);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** A flat swatch for each component: its colour, brightened when active. */
    private static int fillOf(PanelElement element) {
        boolean active = element.displayStrength() > 0 || element.transmittedStrength() > 0;
        if (element instanceof ColorableElement colorable)
            return argb(colorable.color(), active ? 1.0f : 0.6f);
        if (element instanceof ReceiverElement)
            return active ? 0xFFB08850 : 0xFF6E5A3C; // brass-housed gauges
        return 0xFF5A5A60;
    }

    private void frame(GuiGraphics graphics, int x0, int y0, int x1, int y1, int argb) {
        graphics.fill(x0, y0, x1, y0 + 1, argb);
        graphics.fill(x0, y1 - 1, x1, y1, argb);
        graphics.fill(x0, y0, x0 + 1, y1, argb);
        graphics.fill(x1 - 1, y0, x1, y1, argb);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        PanelBlockEntity panel = panel();
        PanelElement element = selected();
        updateVisibility(panel, element);

        super.render(graphics, mouseX, mouseY, partialTick);
        // the chooser stands alone: the designer hides while it is open
        if (choosing) {
            if (element == null || !hasFrequency(element))
                closeChooser();
            else
                renderChooser(graphics, element, mouseX, mouseY);
            return;
        }
        graphics.drawString(font, title, canvasX, canvasY - 11, GOLD_TEXT);
        if (panel == null)
            return;

        // The face: casing-coloured frame ring, plate inside, faint 1 px grid
        int frameColor = panel.casing().frameColor();
        graphics.fill(canvasX, canvasY, canvasX + CANVAS, canvasY + CANVAS, frameColor);
        int inner = (int) (PanelElement.AREA_MIN * SCALE);
        graphics.fill(canvasX + inner, canvasY + inner, canvasX + CANVAS - inner, canvasY + CANVAS - inner, PLATE);
        for (int i = 2; i < 15; i++) {
            int line = i % 4 == 0 ? 0x30FFFFFF : 0x14FFFFFF;
            graphics.fill(canvasX + i * SCALE, canvasY + inner, canvasX + i * SCALE + 1, canvasY + CANVAS - inner, line);
            graphics.fill(canvasX + inner, canvasY + i * SCALE, canvasX + CANVAS - inner, canvasY + i * SCALE + 1, line);
        }

        PanelLayout layout = panel.getLayout();
        for (PanelElement e : layout.elements()) {
            int x0 = canvasX + Math.round(e.x() * SCALE);
            int y0 = canvasY + Math.round(e.y() * SCALE);
            int x1 = canvasX + Math.round((e.x() + e.width()) * SCALE);
            int y1 = canvasY + Math.round((e.y() + e.depth()) * SCALE);
            if (e instanceof LabelElement label) {
                frame(graphics, x0, y0, x1, y1, 0x30FFFFFF);
                drawLabel(graphics, label, x0, y0, x1, y1);
            } else {
                graphics.fill(x0, y0, x1, y1, 0xFF2A2724);
                graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fillOf(e));
                drawGlyph(graphics, e, x0, y0, x1, y1);
            }
            if (e.id().equals(selectedId))
                frame(graphics, x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFFFFFFFF);
        }

        // Drag ghost: white where the component may land, red where it can't
        if (dragMoved && draggingId != null) {
            PanelElement dragged = layout.elementById(draggingId).orElse(null);
            if (dragged != null) {
                boolean valid = layout.isAreaFree(previewX, previewY, dragged.width(), dragged.depth(),
                        dragged.id(), dragged instanceof LabelElement);
                int gx = canvasX + Math.round(previewX * SCALE);
                int gy = canvasY + Math.round(previewY * SCALE);
                graphics.fill(gx, gy, gx + Math.round(dragged.width() * SCALE), gy + Math.round(dragged.depth() * SCALE),
                        valid ? 0x60FFFFFF : 0x70FF4040);
            }
        }

        // Shape readout next to the slider row
        ConsoleShape shape = panel.shape();
        graphics.drawString(font, Component.translatable("gui.create_controlling.front_height",
                        formatPx(shape.frontHeight()), formatPx(shape.backHeight())),
                canvasX, canvasY + CANVAS + 6 + 34, 0xBBBBBB);

        if (element != null) {
            String header = Component.translatable("gui.create_controlling.element." + element.type().id()).getString();
            if (element instanceof ButtonElement button)
                header += "  " + button.pulseTicks() + "t";
            graphics.drawString(font, header, sideX, infoY, 0xFFFFFF);
            if (hasFrequency(element)) {
                graphics.drawString(font, Component.translatable("gui.create_controlling.frequency_slots"),
                        sideX, freqLabelY, GOLD_TEXT);
                renderFrequencySlots(graphics, element, mouseX, mouseY);
            }
            if (element instanceof ColorableElement || element instanceof LabelElement)
                graphics.drawString(font, Component.translatable("gui.create_controlling.options"),
                        sideX, optionsY, GOLD_TEXT);
        } else {
            graphics.drawWordWrap(font, Component.translatable("gui.create_controlling.designer_hint"),
                    sideX, infoY, COLUMN - 2, 0x999999);
        }
    }

    private void updateVisibility(PanelBlockEntity panel, PanelElement element) {
        palette.forEach(w -> w.visible = true);
        boolean isLabel = element instanceof LabelElement;
        selectionOnly.forEach(w -> w.visible = element != null);
        colorOnly.forEach(w -> w.visible = element instanceof ColorableElement);
        pulseOnly.forEach(w -> w.visible = element instanceof ButtonElement);
        labelOnly.forEach(w -> w.visible = isLabel);
        barOnly.forEach(w -> w.visible = element instanceof BarMeterElement);
        if (labelTextBox != null)
            labelTextBox.visible = isLabel;
        boolean shapeable = panel != null;
        shapeOnly.forEach(w -> w.visible = shapeable);
        if (angleSlider != null) {
            angleSlider.visible = shapeable;
            if (panel != null && !angleSlider.isDragging())
                angleSlider.sync(panel.shape().angle());
        }
        if (slopeButton != null && panel != null)
            slopeButton.setMessage(Component.translatable(wallReversed(panel)
                    ? "gui.create_controlling.raised_edge.bottom" : "gui.create_controlling.raised_edge.top"));
        // while the chooser is open, nothing of the designer shows
        if (choosing)
            children().forEach(child -> {
                if (child instanceof AbstractWidget widget)
                    widget.visible = false;
            });
    }

    private static String formatPx(float px) {
        return px == Math.floor(px) ? Integer.toString((int) px) : String.format("%.1f", px);
    }

    /** A hint of what each swatch is: a dot, a bar, a needle line. */
    private void drawGlyph(GuiGraphics graphics, PanelElement e, int x0, int y0, int x1, int y1) {
        int cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
        if (e instanceof AnalogElement analog) {
            int fill = Math.round((x1 - x0 - 4) * analog.value() / 15f);
            graphics.fill(x0 + 2, y1 - 3, x0 + 2 + fill, y1 - 2, 0xFFFFFFFF);
        } else if (e instanceof SwitchElement toggle) {
            graphics.fill(cx - 1, toggle.isOn() ? y0 + 2 : cy, cx + 1, toggle.isOn() ? cy : y1 - 2, 0xFF202020);
        } else if (e instanceof CoveredSwitchElement covered) {
            if (covered.state() == CoveredSwitchElement.State.CLOSED)
                frame(graphics, x0 + 2, y0 + 2, x1 - 2, y1 - 2, 0xFF202020);
        } else if (e instanceof LatchingButtonElement latching && latching.isOn()) {
            graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFFFFFFFF);
        } else if (e instanceof ReceiverElement receiver) {
            graphics.drawCenteredString(font, Integer.toString(receiver.displayStrength()), cx, cy - 4, 0xFFFFFF);
        }
    }

    private void drawLabel(GuiGraphics graphics, LabelElement label, int x0, int y0, int x1, int y1) {
        if (label.text().isEmpty())
            return;
        // Mirrors the face renderer: one font line per footprint depth
        float k = (y1 - y0) / (float) font.lineHeight;
        float textWidth = font.width(label.text()) * k;
        float textX = switch (label.alignment()) {
            case LEFT -> x0 + 1;
            case RIGHT -> x1 - textWidth - 1;
            case CENTER -> x0 + (x1 - x0 - textWidth) / 2f;
        };
        graphics.pose().pushPose();
        graphics.pose().translate(textX, y0 + (y1 - y0) * 0.12f, 0);
        graphics.pose().scale(k, k, 1f);
        graphics.drawString(font, label.text(), 0, 0, 0xFF000000 | label.color(), false);
        graphics.pose().popPose();
    }

    private void renderFrequencySlots(GuiGraphics graphics, PanelElement element, int mouseX, int mouseY) {
        for (int slot = 0; slot < 2; slot++) {
            int x = sideX + slot * 22;
            boolean hovered = mouseX >= x && mouseX < x + 18 && mouseY >= freqSlotY && mouseY < freqSlotY + 18;
            graphics.fill(x, freqSlotY, x + 18, freqSlotY + 18, (hovered ? FREQ_COLORS_HOVER : FREQ_COLORS)[slot]);
            graphics.fill(x + 1, freqSlotY + 1, x + 17, freqSlotY + 17, 0xFF1E1913);
            ItemStack stack = slot == 0 ? element.frequency().first() : element.frequency().second();
            if (!stack.isEmpty())
                graphics.renderItem(stack, x + 1, freqSlotY + 1);
            if (hovered && !stack.isEmpty())
                graphics.renderTooltip(font, stack, mouseX, mouseY);
        }
    }

    /**
     * The frequency chooser: the element's two slots, in their link colours,
     * over the player's inventory. Items are dragged from the inventory (or
     * from JEI) onto a slot; only the slots take them.
     */
    private void renderChooser(GuiGraphics graphics, PanelElement element, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null)
            return;
        int ox = chooserX(), oy = chooserY();
        graphics.fill(ox, oy, ox + CHOOSER_WIDTH, oy + CHOOSER_HEIGHT, 0xF0201A12);
        graphics.fill(ox + 1, oy + 1, ox + CHOOSER_WIDTH - 1, oy + CHOOSER_HEIGHT - 1, 0xFF33291C);
        graphics.drawString(font, Component.translatable("gui.create_controlling.pick_frequency"), ox + 6, oy + 7, GOLD_TEXT);
        int doneX = ox + CHOOSER_WIDTH - 42;
        graphics.fill(doneX, oy + 4, ox + CHOOSER_WIDTH - 4, oy + 18,
                overDoneButton(mouseX, mouseY) ? 0xFF8A6A3F : 0xFF4A3E2C);
        graphics.drawCenteredString(font, Component.translatable("gui.create_controlling.done"), doneX + 19, oy + 7, 0xFFFFFF);

        boolean dragging = !carried.isEmpty();
        int hoveredSlot = chooserSlotAt(mouseX, mouseY);
        for (int slot = 0; slot < 2; slot++) {
            int x = chooserSlotX(slot), y = oy + CHOOSER_SLOTS_Y;
            boolean lit = slot == hoveredSlot || dragging;
            graphics.fill(x, y, x + 18, y + 18, (lit ? FREQ_COLORS_HOVER : FREQ_COLORS)[slot]);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF1E1913);
            if (dragging && slot == hoveredSlot)
                graphics.fill(x + 1, y + 1, x + 17, y + 17, 0x40FFFFFF);
            ItemStack stack = slot == 0 ? element.frequency().first() : element.frequency().second();
            if (!stack.isEmpty())
                graphics.renderItem(stack, x + 1, y + 1);
        }

        int cellsX = ox + CHOOSER_CELLS_X, cellsY = oy + CHOOSER_CELLS_Y;
        ItemStack hoveredItem = ItemStack.EMPTY;
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 9; column++) {
                int x = cellsX + column * 18;
                int y = cellsY + row * 18;
                boolean hovered = mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
                graphics.fill(x, y, x + 18, y + 18, hovered && !dragging ? 0xFF5A4A33 : 0xFF221C14);
                graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF15110C);
                ItemStack stack = minecraft.player.getInventory().getItem(inventoryIndex(row, column));
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, x + 1, y + 1);
                    if (hovered)
                        hoveredItem = stack;
                }
            }
        }

        if (dragging) {
            // the carried item rides on the cursor, above everything else
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.renderItem(carried, mouseX - 8, mouseY - 8);
            graphics.pose().popPose();
        } else if (hoveredSlot >= 0) {
            ItemStack stack = hoveredSlot == 0 ? element.frequency().first() : element.frequency().second();
            if (stack.isEmpty())
                graphics.renderTooltip(font, Component.translatable("gui.create_controlling.frequency_slot_hint"), mouseX, mouseY);
            else
                graphics.renderTooltip(font, stack, mouseX, mouseY);
        } else if (!hoveredItem.isEmpty()) {
            graphics.renderTooltip(font, hoveredItem, mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * The console's angle, 0-45°: free to set anywhere, but snaps to the
     * 0/15/30/45° presets when dragged near one. Sends on every whole-degree
     * change, so the console tilts live behind the screen.
     */
    private final class AngleSlider extends AbstractSliderButton {

        private static final float SNAP_TOLERANCE = 2f;
        private boolean dragging;
        private float sentAngle = Float.NaN;

        AngleSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), 0);
            updateMessage();
        }

        float angle() {
            float raw = (float) (value * ConsoleShape.MAX_ANGLE);
            return ConsoleShape.snapAngle(Math.round(raw), SNAP_TOLERANCE);
        }

        boolean isDragging() {
            return dragging;
        }

        void sync(float angle) {
            value = Mth.clamp(angle / ConsoleShape.MAX_ANGLE, 0, 1);
            sentAngle = angle;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("gui.create_controlling.angle", Math.round(angle())));
        }

        @Override
        protected void applyValue() {
            float angle = angle();
            if (angle == sentAngle)
                return;
            sentAngle = angle;
            PanelBlockEntity panel = panel();
            if (panel != null)
                sendShape(angle, panel.shape().frontHeight(), wallReversed(panel));
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            dragging = true;
            super.onClick(mouseX, mouseY);
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            dragging = false;
            // settle the handle onto the snapped angle
            value = angle() / ConsoleShape.MAX_ANGLE;
            updateMessage();
            super.onRelease(mouseX, mouseY);
        }
    }
}
