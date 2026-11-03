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
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
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

    private final BlockPos panelPos;
    private UUID selectedId;

    private int canvasX;
    private int canvasY;
    private int sideX;
    private int infoY;
    private int freqLabelY;
    private int freqSlotY;
    private int optionsY;
    /** Which frequency slot the inventory picker is open for; -1 = closed. */
    private int pickingSlot = -1;

    // Drag-to-move: the grab offset keeps the component under the cursor
    private UUID draggingId;
    private float dragOffsetX;
    private float dragOffsetY;
    private float previewX;
    private float previewY;
    private boolean dragMoved;

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
            addRenderableWidget(Button.builder(Component.translatable("gui.create_controlling.add." + id), b -> sendAdd(id))
                    .bounds(paletteX + (index % 2) * 60, canvasY + (index / 2) * 16, 58, 14)
                    .tooltip(Tooltip.create(costTooltip(type)))
                    .build());
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

    private int pickerX() {
        return width / 2 - 87;
    }

    private int pickerY() {
        return height / 2 - 60;
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
        if (pickingSlot >= 0) {
            handlePickerClick(mouseX, mouseY);
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
                        pickingSlot = slot;
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
        if (button == 0 && draggingId != null && pickingSlot < 0) {
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

    private void handlePickerClick(double mouseX, double mouseY) {
        if (minecraft == null || minecraft.player == null) {
            pickingSlot = -1;
            return;
        }
        int ox = pickerX();
        int oy = pickerY();
        if (mouseX >= ox + 174 - 42 && mouseX < ox + 174 - 4 && mouseY >= oy + 4 && mouseY < oy + 18) {
            sendFrequency(pickingSlot, ItemStack.EMPTY);
            pickingSlot = -1;
            return;
        }
        int cellsX = ox + 6;
        int cellsY = oy + 22;
        if (mouseX >= cellsX && mouseX < cellsX + 9 * 18 && mouseY >= cellsY && mouseY < cellsY + 4 * 18) {
            int column = (int) ((mouseX - cellsX) / 18);
            int row = (int) ((mouseY - cellsY) / 18);
            ItemStack stack = minecraft.player.getInventory().getItem(inventoryIndex(row, column));
            if (!stack.isEmpty()) {
                sendFrequency(pickingSlot, stack);
                pickingSlot = -1;
            }
            return;
        }
        pickingSlot = -1; // click outside closes
    }

    /** Display rows 0-2 = main inventory, row 3 = hotbar (standard layout). */
    private static int inventoryIndex(int row, int column) {
        return row == 3 ? column : 9 + row * 9 + column;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pickingSlot >= 0 && keyCode == 256) { // ESC closes the picker first
            pickingSlot = -1;
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

        if (pickingSlot >= 0)
            renderPicker(graphics, mouseX, mouseY);
    }

    private void updateVisibility(PanelBlockEntity panel, PanelElement element) {
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
            graphics.fill(x, freqSlotY, x + 18, freqSlotY + 18, hovered ? 0xFF8A6A3F : 0xFF3A332A);
            graphics.fill(x + 1, freqSlotY + 1, x + 17, freqSlotY + 17, 0xFF1E1913);
            ItemStack stack = slot == 0 ? element.frequency().first() : element.frequency().second();
            if (!stack.isEmpty())
                graphics.renderItem(stack, x + 1, freqSlotY + 1);
            if (hovered && !stack.isEmpty())
                graphics.renderTooltip(font, stack, mouseX, mouseY);
        }
    }

    private void renderPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null)
            return;
        graphics.fill(0, 0, width, height, 0xA0000000);
        int ox = pickerX();
        int oy = pickerY();
        graphics.fill(ox, oy, ox + 174, oy + 98, 0xF0201A12);
        graphics.fill(ox + 1, oy + 1, ox + 173, oy + 97, 0xFF33291C);
        graphics.drawString(font, Component.translatable("gui.create_controlling.pick_frequency"), ox + 6, oy + 7, GOLD_TEXT);
        int clearX = ox + 174 - 42;
        boolean clearHover = mouseX >= clearX && mouseX < ox + 170 && mouseY >= oy + 4 && mouseY < oy + 18;
        graphics.fill(clearX, oy + 4, ox + 170, oy + 18, clearHover ? 0xFF8A6A3F : 0xFF4A3E2C);
        graphics.drawCenteredString(font, Component.translatable("gui.create_controlling.clear"), clearX + 19, oy + 7, 0xFFFFFF);

        int cellsX = ox + 6;
        int cellsY = oy + 22;
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 9; column++) {
                int x = cellsX + column * 18;
                int y = cellsY + row * 18;
                boolean hovered = mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
                graphics.fill(x, y, x + 18, y + 18, hovered ? 0xFF8A6A3F : 0xFF221C14);
                graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF15110C);
                ItemStack stack = minecraft.player.getInventory().getItem(inventoryIndex(row, column));
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, x + 1, y + 1);
                    if (hovered)
                        graphics.renderTooltip(font, stack, mouseX, mouseY);
                }
            }
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
