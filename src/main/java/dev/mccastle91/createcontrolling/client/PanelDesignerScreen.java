package dev.mccastle91.createcontrolling.client;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import dev.mccastle91.createcontrolling.item.PanelBlueprintItem;
import dev.mccastle91.createcontrolling.panel.ComponentCosts;
import dev.mccastle91.createcontrolling.panel.ConsoleCasing;
import dev.mccastle91.createcontrolling.panel.ConsoleShape;
import dev.mccastle91.createcontrolling.panel.MaterialList;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.PanelEditOps;
import dev.mccastle91.createcontrolling.panel.PanelLayout;
import dev.mccastle91.createcontrolling.panel.element.AnalogElement;
import dev.mccastle91.createcontrolling.panel.element.BarMeterElement;
import dev.mccastle91.createcontrolling.panel.element.ButtonElement;
import dev.mccastle91.createcontrolling.panel.element.ColorableElement;
import dev.mccastle91.createcontrolling.panel.element.CoveredSwitchElement;
import dev.mccastle91.createcontrolling.panel.element.DisplayBoardElement;
import dev.mccastle91.createcontrolling.panel.element.LabelElement;
import dev.mccastle91.createcontrolling.panel.element.LatchingButtonElement;
import dev.mccastle91.createcontrolling.panel.element.NixieStripElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElementType;
import dev.mccastle91.createcontrolling.panel.element.PanelElements;
import dev.mccastle91.createcontrolling.panel.element.ReadoutElement;
import dev.mccastle91.createcontrolling.panel.element.ReceiverElement;
import dev.mccastle91.createcontrolling.panel.element.SwitchElement;
import dev.mccastle91.createcontrolling.util.McCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
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
 * Layout: one window in the shape of Create's menus (see {@link ConsoleGui}),
 * coloured after the console's casing. Palette (with costs) on the left, the
 * face in the middle, the selection on the right; the console's own shape in
 * the footer bar, and the console itself beside the window where there is room.
 *
 * Opened on a Console Blueprint instead ({@link #PanelDesignerScreen(InteractionHand)})
 * it is the same screen with no console behind it: the design is read from
 * the held item, edits go to that item, nothing is paid for, and what belongs
 * to a console alone (its shape, its name, its copied block) is left out.
 */
public class PanelDesignerScreen extends Screen {

    /** Screen pixels per face pixel; a 0.5 px snap step is 5 screen pixels. */
    private static final int SCALE = 10;
    private static final int CANVAS = 16 * SCALE;
    private static final int COLUMN = 120;
    private static final int GAP = 8;
    /** Between the window's edge and what is in it. */
    private static final int PAD = 5;
    private static final int WINDOW_WIDTH = PAD + COLUMN + GAP + CANVAS + GAP + COLUMN + PAD;
    private static final int WINDOW_HEIGHT = ConsoleGui.TITLE_HEIGHT + PAD + CANVAS + PAD + ConsoleGui.FOOTER_HEIGHT;
    /** The console drawn beside the window: screen pixels to the block, and the room it needs. */
    private static final int PREVIEW_SCALE = 40;
    private static final int PREVIEW_ROOM = 72;

    /** The face plate's texture, as on the console itself. */
    private static final String PLATE = "create_controlling:block/console_plate";
    /**
     * Frequency slot colours, from the two markers on Create's Redstone Link
     * texture: red for the first slot, blue for the second.
     */
    private static final int[] FREQ_COLORS = { 0xFF97333B, 0xFF5371C6 };
    private static final int[] FREQ_COLORS_HOVER = { 0xFFC4505A, 0xFF7E98E0 };

    /** The console being designed, or null when the screen is open on a blueprint. */
    private final BlockPos panelPos;
    /** The hand holding the blueprint being drawn on, or null when the screen is open on a console. */
    private final InteractionHand blueprintHand;
    /** The blueprint's design, as last read off the held item (see {@link #tick}). */
    private PanelLayout blueprintLayout = new PanelLayout();
    private UUID selectedId;

    private int windowX;
    private int windowY;
    /** Whether the console itself is drawn to the right of the window. */
    private boolean previewShown;
    private int canvasX;
    private int canvasY;
    private int footerY;
    private int sideX;
    private int infoY;
    private int freqLabelY;
    private int freqSlotY;
    private int optionsY;
    private int lineRowY;
    /** Top of the typed-text field: a label's text, or the console's name. */
    private int fieldY;
    private int frontRowY;
    /** The frequency chooser is open; the designer hides behind it. */
    private boolean choosing;
    /** An inventory item being dragged in the chooser towards a frequency slot. */
    private ItemStack carried = ItemStack.EMPTY;

    /** What the colour picker sets: a component's colour or a label's text. */
    private enum ColorTarget { COMPONENT, TEXT }

    /** The open colour picker's target, or null when it is closed. */
    private ColorTarget colorTarget;
    private int colorPickerX;
    private int colorPickerY;

    // Drag-to-move: the grab offset keeps the component under the cursor
    private UUID draggingId;
    private float dragOffsetX;
    private float dragOffsetY;
    private float previewX;
    private float previewY;
    private boolean dragMoved;

    private final List<Button> palette = new ArrayList<>();
    /** The component each palette button adds, in the same order. */
    private final List<PanelElementType<?>> paletteTypes = new ArrayList<>();
    /** What the palette's cost colours were last built from; they are rebuilt when it changes. */
    private int costSignature;
    /** On a blueprint: wipes the drawing. Null on a console. */
    private Button clearButton;
    private final List<Button> selectionOnly = new ArrayList<>();
    private final List<Button> colorOnly = new ArrayList<>();
    private final List<Button> pulseOnly = new ArrayList<>();
    private ThemedButton guardButton;
    /** Knobs and sliders: - and + either side of the lowest value, then of the highest. */
    private final List<Button> analogOnly = new ArrayList<>();
    private int limitRowY;
    private final List<Button> labelOnly = new ArrayList<>();
    private final List<Button> barOnly = new ArrayList<>();
    private final List<Button> readoutOnly = new ArrayList<>();
    private final List<Button> boardOnly = new ArrayList<>();
    private final List<Button> stripOnly = new ArrayList<>();
    private EditBox labelTextBox;
    /** The console's own name, shown while no component is selected. */
    private EditBox nameBox;
    private final List<Button> nameOnly = new ArrayList<>();
    private ThemedButton wrapButton;
    /** Set while the text box is filled from a label, which may hold more than now fits. */
    private boolean loadingLabelText;
    private AngleSlider angleSlider;
    /** Wall consoles only: which edge stands out from the wall. */
    private Button slopeButton;
    /** On a copycat console: hands the copied block back. Null on any other console. */
    private Button clearMaterialButton;
    private final List<Button> shapeOnly = new ArrayList<>();

    public PanelDesignerScreen(BlockPos panelPos) {
        super(Component.translatable("gui.create_controlling.designer"));
        this.panelPos = panelPos;
        this.blueprintHand = null;
    }

    /** The designer on the blueprint held in {@code hand}. */
    public PanelDesignerScreen(InteractionHand hand) {
        super(Component.translatable("gui.create_controlling.blueprint_designer"));
        this.panelPos = null;
        this.blueprintHand = hand;
    }

    private boolean onBlueprint() {
        return blueprintHand != null;
    }

    /** Reads the design off the held blueprint; false if that hand no longer holds one. */
    private boolean readBlueprint() {
        if (minecraft == null || minecraft.player == null)
            return false;
        ItemStack stack = minecraft.player.getItemInHand(blueprintHand);
        if (!(stack.getItem() instanceof PanelBlueprintItem))
            return false;
        blueprintLayout = PanelBlueprintItem.design(stack, minecraft.player.level().registryAccess());
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        // the server answers each edit with a new stack in the hand: the drawing is whatever it holds now
        if (onBlueprint() && !readBlueprint()) {
            onClose();
            return;
        }
        refreshCosts();
    }

    private PanelBlockEntity panel() {
        if (panelPos != null && minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(panelPos) instanceof PanelBlockEntity panel)
            return panel;
        return null;
    }

    private CasingTheme theme() {
        if (onBlueprint())
            return CasingTheme.BLUEPRINT;
        PanelBlockEntity panel = panel();
        return CasingTheme.of(panel == null ? ConsoleCasing.DEFAULT : panel.casing());
    }

    private ThemedButton button(Component text, Button.OnPress onPress, int x, int y, int width) {
        return addRenderableWidget(new ThemedButton(x, y, width, 14, text, onPress));
    }

    private PanelLayout layout() {
        if (onBlueprint())
            return blueprintLayout;
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
        paletteTypes.clear();
        if (onBlueprint())
            readBlueprint();
        selectionOnly.clear();
        colorOnly.clear();
        pulseOnly.clear();
        analogOnly.clear();
        labelOnly.clear();
        barOnly.clear();
        readoutOnly.clear();
        boardOnly.clear();
        stripOnly.clear();
        shapeOnly.clear();
        nameOnly.clear();

        // the console is shown beside the window, which moves over to make room for it
        previewShown = !onBlueprint() && width - WINDOW_WIDTH >= PREVIEW_ROOM + 8;
        windowX = (width - WINDOW_WIDTH - (previewShown ? PREVIEW_ROOM : 0)) / 2;
        windowY = Math.max(2, (height - WINDOW_HEIGHT) / 2);
        int paletteX = windowX + PAD;
        canvasX = paletteX + COLUMN + GAP;
        canvasY = windowY + ConsoleGui.TITLE_HEIGHT + PAD;
        sideX = canvasX + CANVAS + GAP;
        footerY = canvasY + CANVAS + PAD;

        // Palette: one add-button per component, cost in the tooltip
        int index = 0;
        for (PanelElementType<?> type : PanelElementType.all()) {
            String id = type.id();
            // a board is nearly as wide as the face: it starts centred, with even margins
            float addX = type == PanelElements.DISPLAY_BOARD ? (PanelElement.FACE_SIZE - type.width()) / 2 : 6;
            palette.add(button(Component.translatable("gui.create_controlling.add." + id), b -> sendAdd(id, addX),
                    paletteX + (index % 2) * 60, canvasY + (index / 2) * 16, 58).tooltip(costTooltip(type)));
            paletteTypes.add(type);
            index++;
        }
        costSignature = costSignatureNow();

        // Console shape, in the footer: front height under the palette, angle under the face
        int shapeY = footerY + (ConsoleGui.FOOTER_HEIGHT - 14) / 2;
        frontRowY = shapeY;
        angleSlider = null;
        slopeButton = null;
        clearButton = null;
        if (onBlueprint()) {
            // a drawing has no shape of its own: its footer wipes it instead
            clearButton = button(Component.translatable("gui.create_controlling.blueprint_clear"), b -> sendClear(),
                    paletteX, shapeY, 58).tooltip(Component.translatable("gui.create_controlling.blueprint_clear.tooltip"));
        } else {
            shapeOnly.add(button(Component.literal("-"), b -> sendFront(-0.5f), paletteX, shapeY, 14));
            shapeOnly.add(button(Component.literal("+"), b -> sendFront(0.5f), paletteX + COLUMN - 16, shapeY, 14));
            angleSlider = addRenderableWidget(new AngleSlider(canvasX, shapeY, CANVAS, 14));
            // a wall console can raise either edge: one more button swaps which
            if (isWall())
                slopeButton = button(Component.empty(), b -> sendSlopeSwap(), sideX, shapeY, COLUMN - 2 - 24)
                        .tooltip(Component.translatable("gui.create_controlling.raised_edge.tooltip"));
        }
        // edits apply as they are made, so the tick only closes, as on Create's menus
        addRenderableWidget(new ThemedButton(windowX + WINDOW_WIDTH - 6 - ThemedButton.ICON_SIZE,
                footerY + (ConsoleGui.FOOTER_HEIGHT - ThemedButton.ICON_SIZE) / 2, AllIcons.I_CONFIRM,
                Component.translatable("gui.create_controlling.done"), b -> onClose()));

        // Selection column: its name with the bin beside it, frequency, then its options
        int y = canvasY;
        infoY = y;
        selectionOnly.add(addRenderableWidget(new ThemedButton(sideX + COLUMN - 2 - ThemedButton.ICON_SIZE, y,
                AllIcons.I_TRASH, Component.translatable("gui.create_controlling.delete"), b -> sendDelete())));
        y += ThemedButton.ICON_SIZE + 2;
        freqLabelY = y;
        y += 11;
        freqSlotY = y;
        y += 18 + 4;
        optionsY = y;
        y += 11;

        colorOnly.add(button(Component.translatable("gui.create_controlling.color"), b -> openColorPicker(ColorTarget.COMPONENT, b), sideX, y, 58));
        guardButton = button(Component.empty(), b -> toggleGuardKeepsState(), sideX, y + 16, COLUMN - 2)
                .tooltip(Component.translatable("gui.create_controlling.guard.tooltip"));
        limitRowY = y + 16;
        limitButtons(limitRowY, "min");
        limitButtons(limitRowY + 16, "max");
        pulseOnly.add(button(Component.literal("Pulse -"), b -> adjustPulse(-5), sideX, y + 16, 58));
        pulseOnly.add(button(Component.literal("Pulse +"), b -> adjustPulse(5), sideX + 60, y + 16, 58));
        barOnly.add(button(Component.literal("Length -"), b -> adjustBarLength(-1), sideX, y + 16, 58));
        barOnly.add(button(Component.literal("Length +"), b -> adjustBarLength(1), sideX + 60, y + 16, 58));

        // Readouts: which line each row shows, between its - and + buttons, then the size
        lineRowY = y;
        lineButtons(readoutOnly, 0, y);
        lineButtons(boardOnly, 1, y + 16);
        boardOnly.add(button(Component.literal("Width -"), b -> adjustReadoutSize(-1), sideX, y + 32, 58));
        boardOnly.add(button(Component.literal("Width +"), b -> adjustReadoutSize(1), sideX + 60, y + 32, 58));
        stripOnly.add(button(Component.literal("Tubes -"), b -> adjustReadoutSize(-1), sideX, y + 16, 58));
        stripOnly.add(button(Component.literal("Tubes +"), b -> adjustReadoutSize(1), sideX + 60, y + 16, 58));

        fieldY = y;
        labelTextBox = new EditBox(font, sideX + 4, y + 3, COLUMN - 2 - 8, 10, Component.empty());
        labelTextBox.setBordered(false);
        labelTextBox.setMaxLength(LabelElement.MAX_TEXT_LENGTH);
        // typing stops when the label is full; shortening is always allowed
        labelTextBox.setFilter(text -> loadingLabelText || text.length() < labelTextBox.getValue().length()
                || labelTextFits(text));
        addRenderableWidget(labelTextBox);
        labelOnly.add(button(Component.translatable("gui.create_controlling.apply"), b -> sendLabelText(), sideX, y + 16, 58));
        labelOnly.add(button(Component.translatable("gui.create_controlling.align"), b -> cycleAlignment(), sideX + 60, y + 16, 58));
        labelOnly.add(button(Component.literal("Size -"), b -> adjustScale(-1), sideX, y + 32, 58));
        labelOnly.add(button(Component.literal("Size +"), b -> adjustScale(1), sideX + 60, y + 32, 58));
        labelOnly.add(button(Component.literal("Width -"), b -> adjustLabelWidth(-1), sideX, y + 48, 58));
        labelOnly.add(button(Component.literal("Width +"), b -> adjustLabelWidth(1), sideX + 60, y + 48, 58));
        labelOnly.add(button(Component.translatable("gui.create_controlling.text_color"), b -> openColorPicker(ColorTarget.TEXT, b), sideX, y + 64, COLUMN - 2));
        wrapButton = button(Component.empty(), b -> toggleWrap(), sideX, y + 80, COLUMN - 2)
                .tooltip(Component.translatable("gui.create_controlling.wrap.tooltip"));
        labelOnly.add(wrapButton);

        // With nothing selected, the same spot names the console itself; a drawing has no name
        nameBox = null;
        clearMaterialButton = null;
        if (onBlueprint())
            return;
        nameBox = new EditBox(font, sideX + 4, y + 3, COLUMN - 2 - 8, 10, Component.translatable("gui.create_controlling.console_name"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(PanelBlockEntity.MAX_NAME_LENGTH);
        PanelBlockEntity current = panel();
        if (current != null)
            nameBox.setValue(current.name());
        addRenderableWidget(nameBox);
        nameOnly.add(button(Component.translatable("gui.create_controlling.set_name"), b -> sendName(), sideX, y + 16, 58)
                .tooltip(Component.translatable("gui.create_controlling.set_name.tooltip")));
        // ...and, on a copycat console, takes the copied block off again
        if (current != null && current.casing() == ConsoleCasing.COPYCAT) {
            clearMaterialButton = button(Component.translatable("gui.create_controlling.clear_material"),
                    b -> send(op(PanelEditOps.OP_CLEAR_MATERIAL)), sideX + 60, y + 16, 58)
                    .tooltip(Component.translatable("gui.create_controlling.clear_material.tooltip"));
            nameOnly.add(clearMaterialButton);
        }
    }

    /** One limit's row: - and + at its ends, the value drawn between them (see render). */
    private void limitButtons(int y, String key) {
        analogOnly.add(button(Component.literal("-"), b -> adjustLimit(key, -1), sideX, y, 14));
        analogOnly.add(button(Component.literal("+"), b -> adjustLimit(key, 1), sideX + COLUMN - 16, y, 14));
    }

    private void lineButtons(List<Button> group, int row, int y) {
        group.add(button(Component.literal("-"), b -> adjustLine(row, -1), sideX, y, 14));
        group.add(button(Component.literal("+"), b -> adjustLine(row, 1), sideX + COLUMN - 16, y, 14));
    }

    /**
     * A palette button's tooltip: the part's name, then each item it costs
     * as "have/need", red with none, yellow with some, green with enough.
     */
    private Component costTooltip(PanelElementType<?> type) {
        MutableComponent text = Component.translatable("gui.create_controlling.element." + type.id()).copy();
        List<ComponentCosts.Cost> cost = ComponentCosts.costOf(type);
        text.append("\n").append(Component.translatable("gui.create_controlling.cost"));
        if (cost.isEmpty())
            text.append(" ").append(Component.translatable("gui.create_controlling.free"));
        for (ComponentCosts.Cost line : cost)
            text.append("\n  ").append(MaterialList.line(line, minecraft == null ? null : minecraft.player));
        if (onBlueprint())
            text.append("\n").append(Component.translatable("gui.create_controlling.cost_on_paste")
                    .withStyle(ChatFormatting.GRAY));
        return text;
    }

    private int costSignatureNow() {
        List<ComponentCosts.Cost> all = new ArrayList<>();
        for (PanelElementType<?> type : paletteTypes)
            all.addAll(ComponentCosts.costOf(type));
        return MaterialList.signature(all, minecraft == null ? null : minecraft.player);
    }

    /** Rebuilds the palette's tooltips when what the player holds has changed (a part was just paid for). */
    private void refreshCosts() {
        int now = costSignatureNow();
        if (now == costSignature)
            return;
        costSignature = now;
        for (int i = 0; i < palette.size() && i < paletteTypes.size(); i++)
            if (palette.get(i) instanceof ThemedButton button)
                button.tooltip(costTooltip(paletteTypes.get(i)));
    }

    // --- Edit ops ------------------------------------------------------------------

    private void send(CompoundTag op) {
        if (onBlueprint()) {
            if (CCClientHooks.SEND_BLUEPRINT_EDIT != null)
                CCClientHooks.SEND_BLUEPRINT_EDIT.accept(blueprintHand, op);
            return;
        }
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

    private void sendAdd(String typeId, float x) {
        CompoundTag tag = new CompoundTag();
        tag.putString(PanelEditOps.KEY_OP, PanelEditOps.OP_ADD);
        tag.putString("Type", typeId);
        tag.putFloat("X", x);
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

    /** A blueprint only: wipes the drawing. */
    private void sendClear() {
        CompoundTag tag = new CompoundTag();
        tag.putString(PanelEditOps.KEY_OP, PanelEditOps.OP_CLEAR);
        send(tag);
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

    private void sendName() {
        CompoundTag tag = new CompoundTag();
        tag.putString(PanelEditOps.KEY_OP, PanelEditOps.OP_SET_NAME);
        tag.putString("Name", nameBox.getValue());
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
        return new Rect2i(windowX, windowY, WINDOW_WIDTH + (previewShown ? PREVIEW_ROOM : 0), WINDOW_HEIGHT);
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

    private void toggleGuardKeepsState() {
        if (selected() instanceof CoveredSwitchElement guarded)
            sendOption("keeps_state", tag -> tag.putBoolean("Value", !guarded.keepsState()));
    }

    /**
     * Steps a knob's or slider's lowest or highest value. A step that would
     * leave 0-15 or carry one limit past the other isn't sent at all (and the
     * server refuses it anyway), so the button simply does nothing.
     */
    private void adjustLimit(String key, int delta) {
        if (!(selected() instanceof AnalogElement analog))
            return;
        boolean lowest = key.equals("min");
        int next = (lowest ? analog.min() : analog.max()) + delta;
        boolean allowed = lowest ? next >= 0 && next <= analog.max()
                : next <= AnalogElement.MAX_VALUE && next >= analog.min();
        if (allowed)
            sendOption(key, tag -> tag.putInt("Value", next));
    }

    private void adjustPulse(int delta) {
        if (selected() instanceof ButtonElement button)
            sendOption("pulse_ticks", tag -> tag.putInt("Value", button.pulseTicks() + delta));
    }

    private void adjustBarLength(float delta) {
        if (selected() instanceof BarMeterElement bar)
            sendOption("length", tag -> tag.putFloat("Value", bar.length() + delta));
    }

    private void adjustLine(int row, int delta) {
        if (selected() instanceof ReadoutElement readout)
            sendOption("line", tag -> {
                tag.putInt("Row", row);
                tag.putInt("Value", readout.line(row) + delta);
            });
    }

    private void adjustReadoutSize(int delta) {
        if (selected() instanceof ReadoutElement readout)
            sendOption("size", tag -> tag.putInt("Value", readout.size() + delta));
    }

    // --- Colour picker: a 4x4 grid of the dye colours under the button that opened it ---

    private static final int SWATCH = 16;
    private static final int SWATCH_STEP = 18;
    private static final int PICKER_PAD = 5;
    private static final int PICKER_SIZE = PICKER_PAD * 2 + 4 * SWATCH_STEP - (SWATCH_STEP - SWATCH);

    private void openColorPicker(ColorTarget target, Button button) {
        colorTarget = target;
        colorPickerX = button.getX();
        colorPickerY = button.getY() + button.getHeight() + 2;
        if (colorPickerY + PICKER_SIZE > height) // no room below: open above the button
            colorPickerY = button.getY() - 2 - PICKER_SIZE;
    }

    /** The colour picker still applies to the selection: right target, still selected. */
    private boolean colorPickerApplies(PanelElement element) {
        return colorTarget == ColorTarget.COMPONENT ? element instanceof ColorableElement
                : colorTarget == ColorTarget.TEXT && element instanceof LabelElement;
    }

    /** The dye under the mouse in the colour picker, or null. */
    private DyeColor swatchAt(double mouseX, double mouseY) {
        double gx = mouseX - colorPickerX - PICKER_PAD, gy = mouseY - colorPickerY - PICKER_PAD;
        if (gx < 0 || gy < 0)
            return null;
        int column = (int) (gx / SWATCH_STEP), row = (int) (gy / SWATCH_STEP);
        if (column > 3 || row > 3 || gx - column * SWATCH_STEP >= SWATCH || gy - row * SWATCH_STEP >= SWATCH)
            return null;
        return DyeColor.byId(row * 4 + column);
    }

    /** The swatch colour: dye colour for components, the dye's text colour for labels. */
    private int swatchRgb(DyeColor dye) {
        return colorTarget == ColorTarget.TEXT ? dye.getTextColor() & 0xFFFFFF : McCompat.dyeRgb(dye);
    }

    private boolean isCurrentColor(PanelElement element, DyeColor dye) {
        if (colorTarget == ColorTarget.TEXT && element instanceof LabelElement label)
            return (dye.getTextColor() & 0xFFFFFF) == label.color();
        return element instanceof ColorableElement colorable && colorable.color() == dye;
    }

    /** A click in the picker: a swatch sets the colour and closes it; anywhere else just closes it. */
    private void handleColorPickerClick(double mouseX, double mouseY) {
        DyeColor dye = swatchAt(mouseX, mouseY);
        if (dye != null) {
            if (colorTarget == ColorTarget.TEXT) {
                int rgb = dye.getTextColor() & 0xFFFFFF;
                sendOption("text_color", tag -> tag.putInt("Value", rgb));
            } else {
                sendOption("color", tag -> tag.putString("Value", dye.getName()));
            }
        }
        colorTarget = null;
    }

    // Labels: only the client can measure text, so every change that moves
    // the line breaks carries the line count the label will then need

    private static float labelLineHeight(int scale) {
        return Mth.clamp(scale, 1, LabelElement.MAX_SCALE) * LabelElement.DEPTH_PER_SCALE;
    }

    private static float labelTextWidth(float width) {
        float clamped = Mth.clamp(PanelElement.snap(width), 1f, PanelElement.AREA_MAX - PanelElement.AREA_MIN);
        return clamped - 2 * LabelElement.TEXT_MARGIN;
    }

    /** Lines the label needs for this text and shape, capped at what it may show. */
    private int labelLines(String text, float width, int scale, boolean wrap) {
        if (!wrap)
            return 1;
        return Math.min(LabelElement.MAX_LINES,
                LabelText.linesNeeded(font, text, labelTextWidth(width), labelLineHeight(scale)));
    }

    /** Whether the selected label can show all of this text. */
    private boolean labelTextFits(String text) {
        if (!(selected() instanceof LabelElement label))
            return true;
        return LabelText.fits(font, text, label.textWidth(), label.lineHeight(), label.maxLines());
    }

    private void sendLabelText() {
        if (!(selected() instanceof LabelElement label))
            return;
        String text = labelTextBox.getValue();
        sendOption("text", tag -> {
            tag.putString("Value", text);
            tag.putInt("Lines", labelLines(text, label.width(), label.scale(), label.wraps()));
        });
    }

    private void adjustScale(int delta) {
        if (selected() instanceof LabelElement label)
            sendOption("scale", tag -> {
                tag.putInt("Value", label.scale() + delta);
                tag.putInt("Lines", labelLines(label.text(), label.width(), label.scale() + delta, label.wraps()));
            });
    }

    private void adjustLabelWidth(float delta) {
        if (selected() instanceof LabelElement label)
            sendOption("width", tag -> {
                tag.putFloat("Value", label.width() + delta);
                tag.putInt("Lines", labelLines(label.text(), label.width() + delta, label.scale(), label.wraps()));
            });
    }

    private void toggleWrap() {
        if (selected() instanceof LabelElement label)
            sendOption("wrap", tag -> {
                tag.putBoolean("Value", !label.wraps());
                tag.putInt("Lines", labelLines(label.text(), label.width(), label.scale(), !label.wraps()));
            });
    }

    /** Fills the text box from a label, even one holding more than now fits. */
    private void loadLabelText(LabelElement label) {
        loadingLabelText = true;
        labelTextBox.setValue(label.text());
        loadingLabelText = false;
    }

    private void cycleAlignment() {
        if (selected() instanceof LabelElement label) {
            var values = LabelElement.Alignment.values();
            var next = values[(label.alignment().ordinal() + 1) % values.length];
            sendOption("alignment", tag -> tag.putString("Value", next.name()));
        }
    }

    // --- Input ---------------------------------------------------------------------

    // Frequency chooser: a small window holding the two slots, over Create's inventory panel
    // (offsets from the chooser's corner)
    private static final int CHOOSER_WIDTH = 176;
    private static final int CHOOSER_WINDOW_HEIGHT = ConsoleGui.TITLE_HEIGHT + 30 + ConsoleGui.FOOTER_HEIGHT;
    private static final int CHOOSER_INVENTORY_Y = CHOOSER_WINDOW_HEIGHT + 2;
    private static final int CHOOSER_HEIGHT = CHOOSER_INVENTORY_Y + 100;
    private static final int CHOOSER_SLOTS_Y = ConsoleGui.TITLE_HEIGHT + 6;
    /** Where Create's inventory panel has its slots: left edge, the three rows, then the hotbar. */
    private static final int INVENTORY_CELLS_X = 7;
    private static final int INVENTORY_ROWS_Y = 17;
    private static final int INVENTORY_HOTBAR_Y = 75;

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

    private int chooserDoneX() {
        return chooserX() + CHOOSER_WIDTH - 6 - ThemedButton.ICON_SIZE;
    }

    private int chooserDoneY() {
        return chooserY() + CHOOSER_WINDOW_HEIGHT - ConsoleGui.FOOTER_HEIGHT
                + (ConsoleGui.FOOTER_HEIGHT - ThemedButton.ICON_SIZE) / 2;
    }

    private boolean overDoneButton(double mouseX, double mouseY) {
        int x = chooserDoneX(), y = chooserDoneY();
        return mouseX >= x && mouseX < x + ThemedButton.ICON_SIZE && mouseY >= y && mouseY < y + ThemedButton.ICON_SIZE;
    }

    private int chooserCellX(int column) {
        return chooserX() + INVENTORY_CELLS_X + column * 18;
    }

    /** Display rows 0-2 = main inventory, row 3 = hotbar, set a little apart. */
    private int chooserCellY(int row) {
        return chooserY() + CHOOSER_INVENTORY_Y + (row == 3 ? INVENTORY_HOTBAR_Y : INVENTORY_ROWS_Y + row * 18);
    }

    /** The player inventory index of the chooser cell under the mouse, or -1. */
    private int chooserCellAt(double mouseX, double mouseY) {
        if (mouseX < chooserCellX(0) || mouseX >= chooserCellX(9))
            return -1;
        int column = (int) ((mouseX - chooserCellX(0)) / 18);
        for (int row = 0; row < 4; row++)
            if (mouseY >= chooserCellY(row) && mouseY < chooserCellY(row) + 18)
                return inventoryIndex(row, column);
        return -1;
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
        if (colorTarget != null) {
            handleColorPickerClick(mouseX, mouseY);
            return true;
        }
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
                    loadLabelText(label);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (choosing || colorTarget != null)
            return true; // a carried item just follows the mouse; the picker takes no drags
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
        int cell = chooserCellAt(mouseX, mouseY);
        if (cell >= 0) {
            ItemStack stack = minecraft.player.getInventory().getItem(cell);
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
        if (colorTarget != null && keyCode == 256) { // ESC closes the colour picker first
            colorTarget = null;
            return true;
        }
        if (choosing && keyCode == 256) { // ESC closes the chooser first
            closeChooser();
            return true;
        }
        if (nameBox != null && nameBox.visible && nameBox.isFocused() && (keyCode == 257 || keyCode == 335)) {
            sendName(); // Enter sets the name
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
        if (element instanceof DisplayBoardElement)
            return 0xFF2B2B2D;
        if (element instanceof NixieStripElement)
            return 0xFF6E5A3C;
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
        CasingTheme theme = theme();

        // not super.render: the window goes between the dimmed world and the widgets
        //? if >=1.21 {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        //?} else {
        /*renderBackground(graphics);
        *///?}
        // the chooser stands alone: the designer hides while it is open
        if (choosing) {
            if (element == null || !hasFrequency(element))
                closeChooser();
            else
                renderChooser(graphics, theme, element, mouseX, mouseY);
            return;
        }
        ConsoleGui.window(graphics, font, theme, title, windowX, windowY, WINDOW_WIDTH, WINDOW_HEIGHT);
        if ((labelTextBox != null && labelTextBox.visible) || (nameBox != null && nameBox.visible))
            ConsoleGui.field(graphics, sideX, fieldY, COLUMN - 2, 14);
        for (Renderable widget : renderables)
            widget.render(graphics, mouseX, mouseY, partialTick);
        PanelLayout layout = layout();
        if (layout == null)
            return;

        renderFace(graphics, panel, layout);

        // Front height, between its - and + in the footer; the back follows from the angle
        int frontX = windowX + PAD, frontWidth = COLUMN - 2;
        if (panel != null) {
            Component front = Component.translatable("gui.create_controlling.front",
                    formatPx(panel.shape().frontHeight()));
            graphics.drawString(font, front, frontX + (frontWidth - font.width(front)) / 2, frontRowY + 3,
                    ConsoleGui.PANEL_TEXT, false);
        }

        if (element != null) {
            String header = Component.translatable("gui.create_controlling.element." + element.type().id()).getString();
            if (element instanceof ButtonElement button)
                header += "  " + button.pulseTicks() + "t";
            drawFitted(graphics, header, sideX, infoY + 5, COLUMN - 2 - ThemedButton.ICON_SIZE - 3, 0xFFFFFF);
            if (hasFrequency(element)) {
                graphics.drawString(font, Component.translatable("gui.create_controlling.frequency_slots"),
                        sideX, freqLabelY, theme.accent);
                renderFrequencySlots(graphics, element, mouseX, mouseY);
            }
            if (element instanceof ColorableElement || element instanceof LabelElement
                    || element instanceof ReadoutElement)
                graphics.drawString(font, Component.translatable("gui.create_controlling.options"),
                        sideX, optionsY, theme.accent);
            if (element instanceof AnalogElement analog) {
                int middle = sideX + (COLUMN - 2) / 2;
                graphics.drawCenteredString(font, Component.translatable("gui.create_controlling.limit.min", analog.min()),
                        middle, limitRowY + 3, 0xFFFFFF);
                graphics.drawCenteredString(font, Component.translatable("gui.create_controlling.limit.max", analog.max()),
                        middle, limitRowY + 16 + 3, 0xFFFFFF);
            }
            if (element instanceof ReadoutElement readout)
                for (int row = 0; row < readout.rows(); row++)
                    graphics.drawCenteredString(font, rowLabel(readout, row), sideX + (COLUMN - 2) / 2,
                            lineRowY + row * 16 + 3, 0xFFFFFF);
        } else if (onBlueprint()) {
            renderMaterials(graphics, layout, theme);
        } else {
            graphics.drawWordWrap(font, Component.translatable("gui.create_controlling.designer_hint"),
                    sideX, infoY + 1, COLUMN - 2, 0xDDDDDD);
            graphics.drawString(font, Component.translatable("gui.create_controlling.console_name"),
                    sideX, optionsY, theme.accent);
        }

        if (previewShown && panel != null)
            ConsolePreview.draw(graphics, panel, windowX + WINDOW_WIDTH + 4 + PREVIEW_ROOM / 2,
                    windowY + WINDOW_HEIGHT - PREVIEW_SCALE, PREVIEW_SCALE, partialTick);

        if (colorTarget != null) {
            if (colorPickerApplies(element))
                renderColorPicker(graphics, element, mouseX, mouseY);
            else
                colorTarget = null;
        } else if (panel != null && mouseX >= frontX + 14 && mouseX < frontX + frontWidth - 14
                && mouseY >= frontRowY && mouseY < frontRowY + 14) {
            graphics.renderTooltip(font, Component.translatable("gui.create_controlling.front_height",
                    formatPx(panel.shape().frontHeight()), formatPx(panel.shape().backHeight())), mouseX, mouseY);
        }
    }

    /** On a blueprint with nothing selected: how it is used, then what the drawing would cost to build. */
    private void renderMaterials(GuiGraphics graphics, PanelLayout layout, CasingTheme theme) {
        Component hint = Component.translatable("gui.create_controlling.blueprint_hint");
        graphics.drawWordWrap(font, hint, sideX, infoY + 1, COLUMN - 2, 0xDDDDDD);
        int y = infoY + 1 + font.wordWrapHeight(hint, COLUMN - 2) + 5;
        graphics.drawString(font, Component.translatable("gui.create_controlling.materials"), sideX, y, theme.accent);
        y += 11;
        List<ComponentCosts.Cost> total = ComponentCosts.totalOf(layout);
        if (total.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.create_controlling.materials_none"), sideX, y, 0xA0A0A0);
            return;
        }
        for (Component line : MaterialList.lines(total, minecraft == null ? null : minecraft.player)) {
            drawFitted(graphics, line, sideX, y, COLUMN - 2);
            y += 9;
        }
    }

    /** A line of styled text at its normal size if it fits {@code maxWidth}, else squeezed to it. */
    private void drawFitted(GuiGraphics graphics, Component text, int x, int y, int maxWidth) {
        int textWidth = font.width(text);
        float k = textWidth <= maxWidth ? 1f : maxWidth / (float) textWidth;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y + (1 - k) * 4, 0);
        graphics.pose().scale(k, k, 1f);
        graphics.drawString(font, text, 0, 0, 0xFFFFFF);
        graphics.pose().popPose();
    }

    /**
     * The face as it is on the console: the casing's own texture for the frame
     * ring, the plate inside it, at the face's scale so one texture pixel is
     * one face pixel. Then a faint 1 px grid, the components and the drag ghost.
     * A blueprint has no console ({@code panel} is null): its face is drafting
     * paper, a shade lighter where parts can go.
     */
    private void renderFace(GuiGraphics graphics, PanelBlockEntity panel, PanelLayout layout) {
        frame(graphics, canvasX - 1, canvasY - 1, canvasX + CANVAS + 1, canvasY + CANVAS + 1, 0xFF000000);
        if (panel == null) {
            graphics.fill(canvasX, canvasY, canvasX + CANVAS, canvasY + CANVAS, 0xFF284B85);
        } else if (panel.material() == null) {
            graphics.blit(canvasX, canvasY, 0, CANVAS, CANVAS, ComponentPainter.sprite(panel.casing().texture()));
        } else {
            // a copycat console: the top of the block it copies, in the colour that block has in the hand
            var colors = net.minecraft.client.Minecraft.getInstance().getBlockColors();
            for (var layer : dev.mccastle91.createcontrolling.client.model.ConsoleBakedModel.layers(
                    panel.material(), net.minecraft.core.Direction.UP, null)) {
                int tint = layer.tintIndex() < 0 ? -1 : colors.getColor(panel.material(), null, null, layer.tintIndex());
                float r = tint == -1 ? 1f : (tint >> 16 & 0xFF) / 255f;
                float g = tint == -1 ? 1f : (tint >> 8 & 0xFF) / 255f;
                float b = tint == -1 ? 1f : (tint & 0xFF) / 255f;
                graphics.blit(canvasX, canvasY, 0, CANVAS, CANVAS, layer.sprite(), r, g, b, 1f);
            }
        }
        int inner = (int) (PanelElement.AREA_MIN * SCALE);
        int x0 = canvasX + inner, y0 = canvasY + inner, x1 = canvasX + CANVAS - inner, y1 = canvasY + CANVAS - inner;
        if (panel == null) {
            graphics.fill(x0, y0, x1, y1, 0xFF335F9E);
        } else {
            graphics.enableScissor(x0, y0, x1, y1);
            graphics.blit(canvasX, canvasY, 0, CANVAS, CANVAS, ComponentPainter.sprite(PLATE));
            graphics.disableScissor();
            // the plate sits a little below its frame
            graphics.fill(x0, y0, x1, y0 + 1, 0x70000000);
            graphics.fill(x0, y0, x0 + 1, y1, 0x70000000);
        }
        for (int i = 2; i < 15; i++) {
            int line = i % 4 == 0 ? 0x30FFFFFF : 0x14FFFFFF;
            graphics.fill(canvasX + i * SCALE, y0, canvasX + i * SCALE + 1, y1, line);
            graphics.fill(x0, canvasY + i * SCALE, x1, canvasY + i * SCALE + 1, line);
        }

        for (PanelElement e : layout.elements()) {
            int ex0 = canvasX + Math.round(e.x() * SCALE);
            int ey0 = canvasY + Math.round(e.y() * SCALE);
            int ex1 = canvasX + Math.round((e.x() + e.width()) * SCALE);
            int ey1 = canvasY + Math.round((e.y() + e.depth()) * SCALE);
            if (e instanceof LabelElement label) {
                frame(graphics, ex0, ey0, ex1, ey1, 0x30FFFFFF);
                drawLabel(graphics, label, ex0, ey0, ex1, ey1);
            } else {
                graphics.fill(ex0, ey0, ex1, ey1, 0xFF2A2724);
                graphics.fill(ex0 + 1, ey0 + 1, ex1 - 1, ey1 - 1, fillOf(e));
                drawGlyph(graphics, e, ex0, ey0, ex1, ey1);
            }
            if (e.id().equals(selectedId))
                frame(graphics, ex0 - 1, ey0 - 1, ex1 + 1, ey1 + 1, 0xFFFFFFFF);
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
    }

    /** Text drawn at its normal size if it fits {@code maxWidth}, else squeezed to it. */
    private void drawFitted(GuiGraphics graphics, String text, int x, int y, int maxWidth, int color) {
        int textWidth = font.width(text);
        if (textWidth <= maxWidth) {
            graphics.drawString(font, text, x, y, color);
            return;
        }
        float k = maxWidth / (float) textWidth;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y + (1 - k) * 4, 0);
        graphics.pose().scale(k, k, 1f);
        graphics.drawString(font, text, 0, 0, color);
        graphics.pose().popPose();
    }

    /** "Line 2", or on a board "Top: Line 2" / "Bottom: Off". */
    private static Component rowLabel(ReadoutElement readout, int row) {
        Component line = lineName(readout.line(row));
        if (readout.rows() == 1)
            return line;
        return Component.translatable(row == 0 ? "gui.create_controlling.row_top" : "gui.create_controlling.row_bottom", line);
    }

    private static Component lineName(int line) {
        return line == ReadoutElement.NO_LINE ? Component.translatable("gui.create_controlling.line_off")
                : Component.translatable("gui.create_controlling.line", line);
    }

    /** The 16 dye colours as swatches on a grey panel, drawn above everything else on the screen. */
    private void renderColorPicker(GuiGraphics graphics, PanelElement element, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        int x0 = colorPickerX, y0 = colorPickerY;
        ConsoleGui.panel(graphics, x0, y0, PICKER_SIZE, PICKER_SIZE);
        DyeColor hovered = swatchAt(mouseX, mouseY);
        for (DyeColor dye : DyeColor.values()) {
            int x = x0 + PICKER_PAD + (dye.getId() % 4) * SWATCH_STEP;
            int y = y0 + PICKER_PAD + (dye.getId() / 4) * SWATCH_STEP;
            boolean current = isCurrentColor(element, dye);
            ConsoleGui.slot(graphics, x, y, SWATCH);
            if (current || dye == hovered)
                graphics.fill(x, y, x + SWATCH, y + SWATCH, 0xFFFFFFFF);
            graphics.fill(x + 1, y + 1, x + SWATCH - 1, y + SWATCH - 1, 0xFF000000 | swatchRgb(dye));
            if (current) // a second ring so the current colour stands out from a hovered one
                frame(graphics, x + 1, y + 1, x + SWATCH - 1, y + SWATCH - 1, 0xFF15110C);
        }
        graphics.pose().popPose();
    }

    private void updateVisibility(PanelBlockEntity panel, PanelElement element) {
        palette.forEach(w -> w.visible = true);
        boolean isLabel = element instanceof LabelElement;
        selectionOnly.forEach(w -> w.visible = element != null);
        colorOnly.forEach(w -> w.visible = element instanceof ColorableElement);
        pulseOnly.forEach(w -> w.visible = element instanceof ButtonElement);
        analogOnly.forEach(w -> w.visible = element instanceof AnalogElement);
        if (guardButton != null) {
            guardButton.visible = element instanceof CoveredSwitchElement;
            if (element instanceof CoveredSwitchElement guarded) {
                guardButton.setMessage(Component.translatable(guarded.keepsState()
                        ? "gui.create_controlling.guard.keeps" : "gui.create_controlling.guard.switches_off"));
                guardButton.setGreen(guarded.keepsState());
            }
        }
        labelOnly.forEach(w -> w.visible = isLabel);
        barOnly.forEach(w -> w.visible = element instanceof BarMeterElement);
        readoutOnly.forEach(w -> w.visible = element instanceof ReadoutElement);
        boardOnly.forEach(w -> w.visible = element instanceof DisplayBoardElement);
        stripOnly.forEach(w -> w.visible = element instanceof NixieStripElement);
        if (labelTextBox != null)
            labelTextBox.visible = isLabel;
        nameOnly.forEach(w -> w.visible = element == null);
        if (clearMaterialButton != null)
            clearMaterialButton.active = panel != null && panel.material() != null;
        if (clearButton != null)
            clearButton.active = !blueprintLayout.isEmpty();
        if (nameBox != null)
            nameBox.visible = element == null;
        if (wrapButton != null && element instanceof LabelElement label) {
            wrapButton.setMessage(Component.translatable(label.wraps()
                    ? "gui.create_controlling.wrap.on" : "gui.create_controlling.wrap.off"));
            wrapButton.setGreen(label.wraps());
        }
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
            // the lever as on a toggle switch, inside a ring while the guard is down
            graphics.fill(cx - 1, covered.isOn() ? y0 + 4 : cy, cx + 1, covered.isOn() ? cy : y1 - 4, 0xFF202020);
            if (!covered.isGuardOpen())
                frame(graphics, x0 + 2, y0 + 2, x1 - 2, y1 - 2, 0xFF202020);
        } else if (e instanceof LatchingButtonElement latching && latching.isOn()) {
            graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFFFFFFFF);
        } else if (e instanceof ReceiverElement receiver) {
            graphics.drawCenteredString(font, Integer.toString(receiver.displayStrength()), cx, cy - 4, 0xFFFFFF);
        } else if (e instanceof ReadoutElement readout) {
            int rowHeight = (y1 - y0) / readout.rows();
            for (int row = 0; row < readout.rows(); row++)
                graphics.drawCenteredString(font, lineName(readout.line(row)), cx,
                        y0 + row * rowHeight + (rowHeight - 8) / 2, 0xFFFFFF);
        }
    }

    private void drawLabel(GuiGraphics graphics, LabelElement label, int x0, int y0, int x1, int y1) {
        // Mirrors the face renderer: one font line per text line of the footprint
        float rowHeight = (y1 - y0) / (float) label.lines();
        float k = rowHeight / font.lineHeight;
        float margin = LabelElement.TEXT_MARGIN * SCALE;
        int row = 0;
        for (String line : LabelText.lines(font, label)) {
            float textWidth = font.width(line) * k;
            float textX = switch (label.alignment()) {
                case LEFT -> x0 + margin;
                case RIGHT -> x1 - textWidth - margin;
                case CENTER -> x0 + (x1 - x0 - textWidth) / 2f;
            };
            graphics.pose().pushPose();
            graphics.pose().translate(textX, y0 + (row + 0.12f) * rowHeight, 0);
            graphics.pose().scale(k, k, 1f);
            graphics.drawString(font, line, 0, 0, 0xFF000000 | label.color(), false);
            graphics.pose().popPose();
            row++;
        }
    }

    /** A frequency slot: an item slot ringed in its link colour, brighter while lit. */
    private void frequencySlot(GuiGraphics graphics, int x, int y, int slot, boolean lit, ItemStack stack) {
        graphics.fill(x, y, x + 18, y + 18, (lit ? FREQ_COLORS_HOVER : FREQ_COLORS)[slot]);
        ConsoleGui.slot(graphics, x + 1, y + 1, 16);
        if (!stack.isEmpty())
            graphics.renderItem(stack, x + 1, y + 1);
    }

    private void renderFrequencySlots(GuiGraphics graphics, PanelElement element, int mouseX, int mouseY) {
        for (int slot = 0; slot < 2; slot++) {
            int x = sideX + slot * 22;
            boolean hovered = mouseX >= x && mouseX < x + 18 && mouseY >= freqSlotY && mouseY < freqSlotY + 18;
            ItemStack stack = slot == 0 ? element.frequency().first() : element.frequency().second();
            frequencySlot(graphics, x, freqSlotY, slot, hovered, stack);
            if (hovered && !stack.isEmpty() && colorTarget == null)
                graphics.renderTooltip(font, stack, mouseX, mouseY);
        }
    }

    /**
     * The frequency chooser: the element's two slots, in their link colours,
     * in a small window over Create's inventory panel. Items are dragged from
     * the inventory (or from JEI) onto a slot; only the slots take them.
     */
    private void renderChooser(GuiGraphics graphics, CasingTheme theme, PanelElement element, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null)
            return;
        int ox = chooserX(), oy = chooserY();
        ConsoleGui.window(graphics, font, theme, Component.translatable("gui.create_controlling.pick_frequency"),
                ox, oy, CHOOSER_WIDTH, CHOOSER_WINDOW_HEIGHT);
        (overDoneButton(mouseX, mouseY) ? AllGuiTextures.BUTTON_HOVER : AllGuiTextures.BUTTON)
                .render(graphics, chooserDoneX(), chooserDoneY());
        AllIcons.I_CONFIRM.render(graphics, chooserDoneX() + 1, chooserDoneY() + 1);

        boolean dragging = !carried.isEmpty();
        int hoveredSlot = chooserSlotAt(mouseX, mouseY);
        for (int slot = 0; slot < 2; slot++) {
            int x = chooserSlotX(slot), y = oy + CHOOSER_SLOTS_Y;
            ItemStack stack = slot == 0 ? element.frequency().first() : element.frequency().second();
            frequencySlot(graphics, x, y, slot, slot == hoveredSlot || dragging, stack);
            if (dragging && slot == hoveredSlot)
                graphics.fill(x + 1, y + 1, x + 17, y + 17, 0x60FFFFFF);
        }

        int inventoryY = oy + CHOOSER_INVENTORY_Y;
        AllGuiTextures.PLAYER_INVENTORY.render(graphics, ox, inventoryY);
        graphics.drawString(font, Component.translatable("container.inventory"), ox + 8, inventoryY + 6,
                ConsoleGui.PANEL_TEXT, false);
        ItemStack hoveredItem = ItemStack.EMPTY;
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 9; column++) {
                int x = chooserCellX(column), y = chooserCellY(row);
                boolean hovered = mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
                ItemStack stack = minecraft.player.getInventory().getItem(inventoryIndex(row, column));
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, x + 1, y + 1);
                    if (hovered)
                        hoveredItem = stack;
                }
                if (hovered && !dragging)
                    graphics.fill(x + 1, y + 1, x + 17, y + 17, 0x80FFFFFF);
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
        } else if (overDoneButton(mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("gui.create_controlling.done"), mouseX, mouseY);
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

        /**
         * Create's value bar (the one a wrench-held scroll opens): a dark
         * track notched at the presets, and a brass cursor carrying the value.
         */
        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x0 = getX(), x1 = x0 + width, y = getY(), top = y + 3;
            graphics.fill(x0, top, x1, top + 8, 0xFF0E0000);
            graphics.fill(x0 + 1, top + 1, x1 - 1, top + 7, 0xFF392A25);
            graphics.fill(x0 + 1, top + 2, x1 - 1, top + 6, 0xFF2B1A14);
            ConsoleGui.checker(graphics, 0x23120C, x0 + 1, top + 3, width - 2, 2);
            // the handle's centre runs 4 px inside each end, as the vanilla slider's does
            for (float preset : ConsoleShape.PRESETS) {
                int notch = x0 + 4 + Math.round(preset / ConsoleShape.MAX_ANGLE * (width - 8));
                graphics.fill(notch, top + 1, notch + 1, top + 7, 0xFF8A6A3F);
            }
            Component text = getMessage();
            int cursorWidth = font.width(text) + 10;
            int center = x0 + 4 + (int) Math.round(value * (width - 8));
            int cx = Mth.clamp(center - cursorWidth / 2, x0, x1 - cursorWidth);
            graphics.fill(cx, y, cx + cursorWidth, y + 14, 0xFF704630);
            graphics.fill(cx + 1, y + 1, cx + cursorWidth - 1, y + 13, isHovered() || dragging ? 0xFFFFEB8C : 0xFFFBDC7D);
            ConsoleGui.checker(graphics, 0xCEA05A, cx + 1, y + 2, cursorWidth - 2, 10);
            graphics.fill(cx + 1, y + 12, cx + cursorWidth - 1, y + 13, 0xFF9C6846);
            graphics.drawString(font, text, cx + 5, y + 3, 0x442B15, false);
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
