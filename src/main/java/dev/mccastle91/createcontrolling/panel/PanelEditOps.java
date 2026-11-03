package dev.mccastle91.createcontrolling.panel;

import dev.mccastle91.createcontrolling.panel.element.BarMeterElement;
import dev.mccastle91.createcontrolling.panel.element.ButtonElement;
import dev.mccastle91.createcontrolling.panel.element.ColorableElement;
import dev.mccastle91.createcontrolling.panel.element.LabelElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElementType;
import dev.mccastle91.createcontrolling.util.McCompat;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
    public static final String OP_SET_SHAPE = "set_shape";

    private PanelEditOps() {
    }

    public static boolean apply(PanelBlockEntity panel, CompoundTag op, HolderLookup.Provider registries, CostPayer payer) {
        PanelLayout layout = panel.getLayout();
        boolean changed = switch (op.getString(KEY_OP)) {
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
            case OP_SET_OPTION -> elementFor(layout, op) instanceof BarMeterElement bar && op.getString("Key").equals("length")
                    ? resizeBar(layout, bar, op.getFloat("Value"))
                    : withElement(layout, op, element -> applyOption(element, op));
            case OP_ADJUST -> {
                PanelElement element = elementFor(layout, op);
                yield element != null && element.scroll(op.getInt("Delta"));
            }
            case OP_SET_SHAPE -> panel.setShape(
                    ConsoleShape.of(op.getFloat("Angle"), op.getFloat("Front"), op.getBoolean("Reversed")));
            default -> false;
        };
        if (changed)
            panel.markLayoutChanged();
        return changed;
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

    /**
     * Lengthens or shortens a bar meter from its left end, sliding it left
     * if it would run off the face; refused if it would overlap a neighbour.
     */
    private static boolean resizeBar(PanelLayout layout, BarMeterElement bar, float requested) {
        float length = BarMeterElement.clampLength(requested);
        if (length == bar.width())
            return false;
        float x = Math.min(bar.x(), PanelElement.AREA_MAX - length);
        if (!layout.isAreaFree(x, bar.y(), length, bar.depth(), bar.id(), false))
            return false;
        bar.setLength(length);
        bar.setPos(x, bar.y());
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
        else if (element instanceof LabelElement label)
            switch (key) {
                case "text" -> label.setText(op.getString("Value"));
                case "text_color" -> label.setColor(op.getInt("Value"));
                case "scale" -> label.setScale(op.getInt("Value"));
                case "alignment" -> label.setAlignment(LabelElement.Alignment.byName(op.getString("Value")));
                case "width" -> label.setLabelWidth(op.getFloat("Value"));
                default -> {
                }
            }
    }
}
