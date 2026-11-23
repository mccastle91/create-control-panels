package dev.mccastle91.createcontrolling.panel;

import dev.mccastle91.createcontrolling.panel.element.AnalogElement;
import dev.mccastle91.createcontrolling.panel.element.BarMeterElement;
import dev.mccastle91.createcontrolling.panel.element.ButtonElement;
import dev.mccastle91.createcontrolling.panel.element.ColorableElement;
import dev.mccastle91.createcontrolling.panel.element.CoveredSwitchElement;
import dev.mccastle91.createcontrolling.panel.element.LabelElement;
import dev.mccastle91.createcontrolling.panel.element.LampElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElementType;
import dev.mccastle91.createcontrolling.panel.element.ReadoutElement;
import dev.mccastle91.createcontrolling.util.McCompat;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/**
 * Server-side application of designer edits, described as NBT so the
 * networking layer stays a dumb pipe. Every value goes through the elements'
 * own clamping; unknown ops and ids are ignored. Mounting a component charges
 * its cost through the {@link CostPayer}; removing one refunds it.
 */
public final class PanelEditOps {

    public static final String KEY_OP = "Op";
    public static final String OP_ADD = "add";
    public static final String OP_MOVE = "move";
    public static final String OP_DELETE = "delete";
    public static final String OP_SET_FREQUENCY = "set_frequency";
    public static final String OP_SET_OPTION = "set_option";
    /** Play mode: sneak + scroll nudges a knob or slider by a signed delta. */
    public static final String OP_ADJUST = "adjust";
    /** Play mode: a held drag sets a knob or slider straight to a value. */
    public static final String OP_SET_VALUE = "set_value";
    public static final String OP_SET_SHAPE = "set_shape";
    /** The console's own name, which computers can find it by. */
    public static final String OP_SET_NAME = "set_name";

    /** A blueprint only: wipes the design it holds. */
    public static final String OP_CLEAR = "clear";

    /** A copycat console gives its copied block back and goes bare again. */
    public static final String OP_CLEAR_MATERIAL = "clear_material";

    private PanelEditOps() {
    }

    public static boolean apply(PanelBlockEntity panel, CompoundTag op, HolderLookup.Provider registries, CostPayer payer) {
        return apply(panel, op, registries, payer, null);
    }

    /** @param actor the player making the edit, if any: they aren't sent the sound of their own drag */
    public static boolean apply(PanelBlockEntity panel, CompoundTag op, HolderLookup.Provider registries, CostPayer payer,
                                @org.jetbrains.annotations.Nullable net.minecraft.world.entity.player.Player actor) {
        // a knob turning is no layout change: it syncs like a click does
        if (OP_SET_VALUE.equals(op.getString(KEY_OP)))
            return op.hasUUID("Id") && panel.setAnalogValue(op.getUUID("Id"), op.getInt("Value"), actor);
        // nor is a name: it saves and syncs on its own
        if (OP_SET_NAME.equals(op.getString(KEY_OP)))
            return panel.setName(op.getString("Name"));
        // nor is the copied block
        if (OP_CLEAR_MATERIAL.equals(op.getString(KEY_OP))) {
            if (panel.material() == null)
                return false;
            ItemStack returned = panel.clearMaterial();
            if (!returned.isEmpty())
                payer.refund(List.of(returned));
            return true;
        }
        PanelLayout layout = panel.getLayout();
        boolean changed = switch (op.getString(KEY_OP)) {
            case OP_ADJUST -> {
                PanelElement element = elementFor(layout, op);
                yield element != null && element.scroll(op.getInt("Delta"));
            }
            case OP_SET_SHAPE -> panel.setShape(
                    ConsoleShape.of(op.getFloat("Angle"), op.getFloat("Front"), op.getBoolean("Reversed")));
            default -> applyToLayout(layout, op, registries, payer);
        };
        if (changed)
            panel.markLayoutChanged();
        return changed;
    }

    /**
     * The edits that only touch the design: add, move, delete, set a
     * frequency, set an option. They work on any layout, so a blueprint is
     * drawn with the same ops as a console. Returns true if the layout changed.
     */
    public static boolean applyToLayout(PanelLayout layout, CompoundTag op, HolderLookup.Provider registries, CostPayer payer) {
        return switch (op.getString(KEY_OP)) {
            case OP_ADD -> add(layout, op, payer);
            case OP_MOVE -> move(layout, op);
            case OP_DELETE -> op.hasUUID("Id") && layout.removeElement(op.getUUID("Id")).map(removed -> {
                payer.refund(removed.paid());
                return true;
            }).orElse(false);
            case OP_SET_FREQUENCY -> withElement(layout, op, element -> {
                ItemStack stack = op.contains("Item")
                        ? McCompat.loadStack(op.getCompound("Item"), registries)
                        : ItemStack.EMPTY;
                FrequencyPair current = element.frequency();
                element.setFrequency(op.getInt("Slot") == 0
                        ? FrequencyPair.of(stack, current.second())
                        : FrequencyPair.of(current.first(), stack));
            });
            case OP_SET_OPTION -> setOption(layout, op);
            default -> false;
        };
    }

    private static boolean add(PanelLayout layout, CompoundTag op, CostPayer payer) {
        PanelElementType<?> type = PanelElementType.byId(op.getString("Type"));
        if (type == null || layout.elementCount() >= PanelLayout.maxElements())
            return false;
        PanelElement element = type.create();
        boolean isLabel = element instanceof LabelElement;
        float[] spot = findFreeSpot(layout, element, op.getFloat("X"), op.getFloat("Y"), isLabel);
        if (spot == null)
            return false; // no room for this footprint anywhere
        List<ItemStack> paid = payer.tryPay(ComponentCosts.costOf(type));
        if (paid == null)
            return false;
        element.setPaid(paid);
        element.setPos(spot[0], spot[1]);
        if (element instanceof ReadoutElement readout)
            readout.takeFreeLines(layout.readoutLines());
        if (!layout.addElement(element)) {
            payer.refund(paid);
            return false;
        }
        return true;
    }

    private static boolean move(PanelLayout layout, CompoundTag op) {
        PanelElement element = elementFor(layout, op);
        if (element == null)
            return false;
        float x = element.clampX(op.getFloat("X"));
        float y = element.clampY(op.getFloat("Y"));
        if (!layout.isAreaFree(x, y, element.width(), element.depth(), element.id(), element instanceof LabelElement))
            return false;
        element.setPos(x, y);
        return true;
    }

    private static boolean setOption(PanelLayout layout, CompoundTag op) {
        PanelElement element = elementFor(layout, op);
        String key = op.getString("Key");
        if (element instanceof BarMeterElement bar && key.equals("length"))
            return resizeBar(layout, bar, op.getFloat("Value"));
        // a limit that would pass the other one is refused: nothing changes
        if (element instanceof AnalogElement analog && key.equals("min"))
            return analog.setMin(op.getInt("Value"));
        if (element instanceof AnalogElement analog && key.equals("max"))
            return analog.setMax(op.getInt("Value"));
        if (element instanceof ReadoutElement readout && key.equals("size"))
            return resizeReadout(layout, readout, op.getInt("Value"));
        return withElement(layout, op, target -> applyOption(target, op));
    }

    /**
     * Lengthens or shortens a bar meter, keeping its top-left corner and
     * sliding it back onto the face if it would run off; refused if it would
     * overlap a neighbour.
     */
    private static boolean resizeBar(PanelLayout layout, BarMeterElement bar, float requested) {
        float length = BarMeterElement.clampLength(requested);
        if (length == bar.length())
            return false;
        float width = bar.isVertical() ? bar.thickness() : length;
        float depth = bar.isVertical() ? length : bar.thickness();
        float x = Math.min(bar.x(), PanelElement.AREA_MAX - width);
        float y = Math.min(bar.y(), PanelElement.AREA_MAX - depth);
        if (!layout.isAreaFree(x, y, width, depth, bar.id(), false))
            return false;
        bar.setLength(length);
        bar.setPos(x, y);
        return true;
    }

    /** Resizes a readout about its centre, keeping it on the face; refused if it would overlap a neighbour. */
    private static boolean resizeReadout(PanelLayout layout, ReadoutElement readout, int requested) {
        int size = readout.clampSize(requested);
        if (size == readout.size())
            return false;
        float width = readout.widthFor(size);
        float x = Mth.clamp(PanelElement.snap(readout.x() - (width - readout.width()) / 2),
                PanelElement.AREA_MIN, PanelElement.AREA_MAX - width);
        if (!layout.isAreaFree(x, readout.y(), width, readout.depth(), readout.id(), false))
            return false;
        readout.setSize(size);
        readout.setPos(x, readout.y());
        return true;
    }

    /** The requested spot if free, else the first free spot scanning rows; null if none. */
    private static float[] findFreeSpot(PanelLayout layout, PanelElement element, float preferX, float preferY, boolean isLabel) {
        float x = element.clampX(preferX);
        float y = element.clampY(preferY);
        if (layout.isAreaFree(x, y, element.width(), element.depth(), null, isLabel))
            return new float[] { x, y };
        float maxX = PanelElement.AREA_MAX - element.width();
        float maxY = PanelElement.AREA_MAX - element.depth();
        for (float sy = PanelElement.AREA_MIN; sy <= maxY + 1e-3f; sy += PanelElement.SNAP)
            for (float sx = PanelElement.AREA_MIN; sx <= maxX + 1e-3f; sx += PanelElement.SNAP)
                if (layout.isAreaFree(sx, sy, element.width(), element.depth(), null, isLabel))
                    return new float[] { sx, sy };
        return null;
    }

    private static PanelElement elementFor(PanelLayout layout, CompoundTag op) {
        if (!op.hasUUID("Id"))
            return null;
        return layout.elementById(op.getUUID("Id")).orElse(null);
    }

    private static boolean withElement(PanelLayout layout, CompoundTag op, Consumer<PanelElement> action) {
        PanelElement element = elementFor(layout, op);
        if (element == null)
            return false;
        action.accept(element);
        return true;
    }

    private static void applyOption(PanelElement element, CompoundTag op) {
        String key = op.getString("Key");
        if (element instanceof ColorableElement colorable && key.equals("color"))
            colorable.setColor(DyeColor.byName(op.getString("Value"), colorable.color()));
        else if (element instanceof ButtonElement button && key.equals("pulse_ticks"))
            button.setPulseTicks(op.getInt("Value"));
        else if (element instanceof CoveredSwitchElement guarded && key.equals("keeps_state"))
            guarded.setKeepsState(op.getBoolean("Value"));
        else if (element instanceof LampElement lamp && key.equals("inverted"))
            lamp.setInverted(op.getBoolean("Value"));
        else if (element instanceof ReadoutElement readout && key.equals("line"))
            readout.setLine(op.getInt("Row"), op.getInt("Value"));
        else if (element instanceof LabelElement label) {
            switch (key) {
                case "text" -> label.setText(op.getString("Value"));
                case "text_color" -> label.setColor(op.getInt("Value"));
                case "scale" -> label.setScale(op.getInt("Value"));
                case "alignment" -> label.setAlignment(LabelElement.Alignment.byName(op.getString("Value")));
                case "width" -> label.setLabelWidth(op.getFloat("Value"));
                case "wrap" -> label.setWrap(op.getBoolean("Value"));
                default -> {
                }
            }
            // the designer measured the text as it will be after this change
            if (op.contains("Lines"))
                label.setLines(op.getInt("Lines"));
        }
    }
}
