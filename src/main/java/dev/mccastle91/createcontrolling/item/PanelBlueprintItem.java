package dev.mccastle91.createcontrolling.item;

import dev.mccastle91.createcontrolling.panel.ComponentCosts;
import dev.mccastle91.createcontrolling.panel.CostPayer;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.PanelLayout;
import dev.mccastle91.createcontrolling.util.McCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Copies a console design and stamps it onto other consoles — Schematic-
 * flavored ergonomics: click an empty blueprint on a console to copy; click a
 * filled one to paste; sneak-click to re-copy over the stored design. Pasting
 * charges only the parts the target doesn't already have, and refunds the
 * parts it no longer needs.
 */
public class PanelBlueprintItem extends Item {

    public static final String TAG_LAYOUT = "Layout";
    public static final String TAG_COUNT = "ElementCount";

    public PanelBlueprintItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /**
     * Called from the console block's item-use hook, ahead of play-mode
     * interaction so the click isn't swallowed by a component. Server-side only.
     */
    public static void useOnPanel(ItemStack stack, Level level, BlockPos pos, Player player) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof PanelBlockEntity panel))
            return;

        CompoundTag stored = storedLayout(stack);
        boolean copy = stored == null || (player != null && player.isShiftKeyDown());
        if (copy) {
            CompoundTag wrap = new CompoundTag();
            wrap.put(TAG_LAYOUT, panel.getLayout().save(level.registryAccess()));
            wrap.putInt(TAG_COUNT, panel.getLayout().elementCount());
            McCompat.setCustomData(stack, wrap);
            message(player, "blueprint_copied", panel.getLayout().elementCount());
            return;
        }

        PanelLayout layout = PanelLayout.load(stored.getCompound(TAG_LAYOUT), level.registryAccess());
        if (layout.elementCount() > PanelLayout.maxElements()) {
            message(player, "blueprint_mismatch", layout.elementCount());
            return;
        }
        CostPayer payer = player == null ? CostPayer.FREE : CostPayer.of(player);
        if (!ComponentCosts.settle(panel.getLayout(), layout, payer)) {
            message(player, "blueprint_missing_parts", layout.elementCount());
            return;
        }
        panel.setLayout(layout);
        message(player, "blueprint_pasted", layout.elementCount());
    }

    private static CompoundTag storedLayout(ItemStack stack) {
        CompoundTag tag = McCompat.getCustomData(stack);
        return tag != null && tag.contains(TAG_LAYOUT) ? tag : null;
    }

    private static void message(Player player, String key, int count) {
        if (player != null)
            player.displayClientMessage(
                    Component.translatable("message.create_controlling." + key, count), true);
    }

    @Override
    //? if >=1.21 {
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
    //?} else {
    /*public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable Level context, List<Component> tooltip, TooltipFlag flag) {
    *///?}
        CompoundTag stored = storedLayout(stack);
        tooltip.add(stored == null
                ? Component.translatable("tooltip.create_controlling.blueprint_empty")
                : Component.translatable("tooltip.create_controlling.blueprint_stored", stored.getInt(TAG_COUNT)));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
