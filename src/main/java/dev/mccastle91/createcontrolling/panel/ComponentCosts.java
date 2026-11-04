package dev.mccastle91.createcontrolling.panel;

import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElementType;
import dev.mccastle91.createcontrolling.panel.element.PanelElements;
import dev.mccastle91.createcontrolling.util.McCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What each component costs to mount. Costs are what give an untiered
 * console its progression: simple parts take vanilla redstone parts, analog
 * and readout parts take finished Create items (an analog lever, a
 * speedometer, a nixie tube). A line names an item, or an item tag with a
 * leading {@code #} (any wooden button, any dye). Items are resolved lazily;
 * an item id that doesn't resolve makes that line free rather than making the
 * part unbuildable.
 *
 * Each component remembers the exact stacks paid for it, so removing it gives
 * back those items (the birch button you used, not an oak one), and a part
 * mounted for free in creative refunds nothing.
 */
public final class ComponentCosts {

    /** One line of a cost: {@code count} of an item, or of anything in an item tag ({@code #ns:path}). */
    public record Cost(String key, int count) {

        public boolean isTag() {
            return key.startsWith("#");
        }

        public boolean matches(ItemStack stack) {
            if (stack.isEmpty())
                return false;
            if (isTag())
                return stack.is(TagKey.create(Registries.ITEM, location(key.substring(1))));
            return stack.is(resolve(key));
        }

        /** "Lever", or for a tag its own translation ("Any Wooden Button"). */
        public Component name() {
            if (isTag())
                return Component.translatable("gui.create_controlling.cost_tag." + key.substring(1).replace(':', '.'));
            return resolve(key).getDescription();
        }

        boolean available() {
            return isTag() || resolve(key) != Items.AIR;
        }
    }

    // Dyes have no vanilla tag on 1.20.1/1.21.1; each loader keeps a common one
    //? if neoforge {
    private static final String ANY_DYE = "#c:dyes";
    //?} else {
    /*private static final String ANY_DYE = "#forge:dyes";
    *///?}

    private static final Map<String, List<Cost>> COSTS = new HashMap<>();

    static {
        cost(PanelElements.BUTTON, "#minecraft:wooden_buttons", 1, "minecraft:glowstone_dust", 1);
        cost(PanelElements.LATCHING_BUTTON, "minecraft:lever", 1, "minecraft:glowstone_dust", 1);
        cost(PanelElements.LARGE_BUTTON, "#minecraft:wooden_pressure_plates", 1, "minecraft:glowstone_dust", 1);
        cost(PanelElements.TOGGLE_SWITCH, "minecraft:lever", 1);
        cost(PanelElements.COVERED_SWITCH, "minecraft:lever", 1, "create:iron_sheet", 1);
        cost(PanelElements.KNOB, "create:analog_lever", 1);
        cost(PanelElements.SLIDER_H, "create:analog_lever", 1);
        cost(PanelElements.SLIDER_V, "create:analog_lever", 1);
        cost(PanelElements.LAMP, "minecraft:redstone_lamp", 1);
        cost(PanelElements.LARGE_LAMP, "minecraft:redstone_lamp", 2);
        cost(PanelElements.NEEDLE_METER, "create:speedometer", 1);
        cost(PanelElements.BAR_METER, "create:speedometer", 1);
        cost(PanelElements.NIXIE, "create:nixie_tube", 1);
        cost(PanelElements.NIXIE_STRIP, "create:nixie_tube", 2);
        cost(PanelElements.DISPLAY_BOARD, "create:display_board", 1);
        cost(PanelElements.LABEL, ANY_DYE, 1);
    }

    private ComponentCosts() {
    }

    private static void cost(PanelElementType<?> type, Object... keyCountPairs) {
        List<Cost> lines = new ArrayList<>();
        for (int i = 0; i < keyCountPairs.length; i += 2)
            lines.add(new Cost((String) keyCountPairs[i], (Integer) keyCountPairs[i + 1]));
        COSTS.put(type.id(), List.copyOf(lines));
    }

    /** The cost lines of a component type; empty means free. */
    public static List<Cost> costOf(PanelElementType<?> type) {
        List<Cost> lines = new ArrayList<>();
        for (Cost line : COSTS.getOrDefault(type.id(), List.of()))
            if (line.available())
                lines.add(line);
        return lines;
    }

    /**
     * Pastes {@code to} over {@code from}. Items paid for the old components
     * are reused line by line for the new ones; only the shortfall is charged,
     * and whatever is left over comes back. Every new component records what
     * now pays for it. Returns false, changing nothing, if the payer can't
     * cover the shortfall.
     */
    public static boolean settle(PanelLayout from, PanelLayout to, CostPayer payer) {
        List<ItemStack> pool = new ArrayList<>();
        for (PanelElement element : from.elements())
            for (ItemStack stack : element.paid())
                pool.add(stack.copy());

        Map<PanelElement, List<ItemStack>> covered = new LinkedHashMap<>();
        Map<PanelElement, List<Cost>> missing = new LinkedHashMap<>();
        Map<String, Integer> shortfall = new LinkedHashMap<>();
        for (PanelElement element : to.elements()) {
            List<ItemStack> got = new ArrayList<>();
            List<Cost> lacking = new ArrayList<>();
            for (Cost line : costOf(element.type())) {
                int need = line.count() - take(pool, line, line.count(), got);
                if (need > 0) {
                    lacking.add(new Cost(line.key(), need));
                    shortfall.merge(line.key(), need, Integer::sum);
                }
            }
            covered.put(element, got);
            missing.put(element, lacking);
        }

        List<Cost> charge = new ArrayList<>();
        shortfall.forEach((key, count) -> charge.add(new Cost(key, count)));
        List<ItemStack> paid = charge.isEmpty() ? List.of() : payer.tryPay(charge);
        if (paid == null)
            return false;
        List<ItemStack> purse = new ArrayList<>();
        paid.forEach(stack -> purse.add(stack.copy()));

        for (PanelElement element : to.elements()) {
            List<ItemStack> got = covered.get(element);
            for (Cost line : missing.get(element))
                take(purse, line, line.count(), got);
            element.setPaid(got);
        }
        pool.removeIf(ItemStack::isEmpty);
        payer.refund(pool);
        return true;
    }

    /** Moves up to {@code count} items matching {@code line} out of {@code from} into {@code into}; returns how many. */
    private static int take(List<ItemStack> from, Cost line, int count, List<ItemStack> into) {
        int taken = 0;
        for (ItemStack stack : from) {
            if (taken == count)
                break;
            if (!line.matches(stack))
                continue;
            int n = Math.min(count - taken, stack.getCount());
            into.add(stack.copyWithCount(n));
            stack.shrink(n);
            taken += n;
        }
        return taken;
    }

    private static Item resolve(String itemId) {
        return BuiltInRegistries.ITEM.getOptional(location(itemId)).orElse(Items.AIR);
    }

    private static ResourceLocation location(String id) {
        int colon = id.indexOf(':');
        return McCompat.id(id.substring(0, colon), id.substring(colon + 1));
    }
}
