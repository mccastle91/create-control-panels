package dev.mccastle91.createcontrolling.item;

import dev.mccastle91.createcontrolling.client.CCClientHooks;
import dev.mccastle91.createcontrolling.panel.ComponentCosts;
import dev.mccastle91.createcontrolling.panel.CostPayer;
import dev.mccastle91.createcontrolling.panel.MaterialList;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.PanelEditOps;
import dev.mccastle91.createcontrolling.panel.PanelLayout;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.util.McCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Holds a console design, and is where one can be drawn without a console.
 *
 * <ul>
 * <li>Used in the air (or on any block that isn't a console) it opens the
 * designer on the design it holds. Drawing there costs nothing.</li>
 * <li>Clicked on a console while it holds no parts, it copies that console's
 * design. A blueprint that holds parts is never overwritten by a click.</li>
 * <li>Sneak-clicked on a console, it pastes: the console's parts are swapped
 * for the design's, charging only for what the console lacks and handing back
 * what it no longer needs. A plain click only says what a paste would need.</li>
 * </ul>
 */
public class PanelBlueprintItem extends Item {

    public static final String TAG_LAYOUT = "Layout";
    public static final String TAG_COUNT = "ElementCount";

    public PanelBlueprintItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    // --- The stored design ---------------------------------------------------

    /** How many parts the blueprint holds; none means it is empty. */
    public static int partCount(ItemStack stack) {
        CompoundTag tag = McCompat.getCustomData(stack);
        return tag != null && tag.contains(TAG_LAYOUT) ? tag.getInt(TAG_COUNT) : 0;
    }

    /** The design the blueprint holds, as a layout of its own; an empty one if it holds none. */
    public static PanelLayout design(ItemStack stack, HolderLookup.Provider registries) {
        CompoundTag tag = McCompat.getCustomData(stack);
        return tag != null && tag.contains(TAG_LAYOUT)
                ? PanelLayout.load(tag.getCompound(TAG_LAYOUT), registries) : new PanelLayout();
    }

    public static void setDesign(ItemStack stack, PanelLayout layout, HolderLookup.Provider registries) {
        CompoundTag wrap = new CompoundTag();
        wrap.put(TAG_LAYOUT, layout.save(registries));
        wrap.putInt(TAG_COUNT, layout.elementCount());
        McCompat.setCustomData(stack, wrap);
    }

    /**
     * One designer edit on the design the blueprint holds (server-side).
     * Nothing is paid for: a blueprint is a drawing, and its parts are paid
     * when it is pasted. Returns true if the design changed.
     */
    public static boolean edit(ItemStack stack, CompoundTag op, HolderLookup.Provider registries) {
        PanelLayout layout = design(stack, registries);
        boolean changed;
        if (PanelEditOps.OP_CLEAR.equals(op.getString(PanelEditOps.KEY_OP))) {
            changed = !layout.isEmpty();
            layout = new PanelLayout();
        } else {
            changed = PanelEditOps.applyToLayout(layout, op, registries, CostPayer.FREE);
        }
        if (changed)
            setDesign(stack, layout, registries);
        return changed;
    }

    // --- Use -------------------------------------------------------------------

    /** In the air, or on a block that isn't a console: open the designer on this blueprint. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide && CCClientHooks.OPEN_BLUEPRINT != null)
            CCClientHooks.OPEN_BLUEPRINT.accept(hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    /**
     * A sneaking player's click skips the block and lands here, so this is
     * where a sneak-click on a console is caught. (A plain click reaches the
     * console block first, which calls {@link #useOnPanel} itself.)
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof PanelBlockEntity))
            return InteractionResult.PASS;
        useOnPanel(context.getItemInHand(), level, context.getClickedPos(), context.getPlayer());
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A click on a console with the blueprint in hand. Server-side only. */
    public static void useOnPanel(ItemStack stack, Level level, BlockPos pos, @Nullable Player player) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof PanelBlockEntity panel))
            return;
        HolderLookup.Provider registries = level.registryAccess();
        PanelLayout design = design(stack, registries);

        // an empty blueprint copies; one that holds parts never does
        if (design.isEmpty()) {
            if (panel.getLayout().isEmpty()) {
                message(player, "blueprint_nothing_to_copy");
                return;
            }
            // a copy of its own, without what was paid for the console's parts: a drawing owns no items
            PanelLayout copy = PanelLayout.load(panel.getLayout().save(registries), registries);
            for (PanelElement element : copy.elements())
                element.setPaid(List.of());
            setDesign(stack, copy, registries);
            message(player, "blueprint_copied", copy.elementCount());
            return;
        }

        if (design.elementCount() > PanelLayout.maxElements()) {
            message(player, "blueprint_mismatch");
            return;
        }
        List<ComponentCosts.Cost> lacking = player == null ? List.of()
                : ComponentCosts.lacking(player, ComponentCosts.shortfall(panel.getLayout(), design));
        boolean sneaking = player != null && player.isShiftKeyDown();
        if (!sneaking) {
            // a plain click changes nothing: it says what a paste here would take
            if (lacking.isEmpty())
                message(player, "blueprint_ready", design.elementCount());
            else
                message(player, "blueprint_needs", MaterialList.inline(lacking));
            return;
        }
        CostPayer payer = player == null ? CostPayer.FREE : CostPayer.of(player);
        if (!lacking.isEmpty() || !ComponentCosts.settle(panel.getLayout(), design, payer)) {
            message(player, "blueprint_missing_parts", MaterialList.inline(lacking));
            return;
        }
        panel.setLayout(design);
        message(player, "blueprint_pasted", design.elementCount());
    }

    private static void message(@Nullable Player player, String key, Object... args) {
        if (player != null)
            player.displayClientMessage(Component.translatable("message.create_controlling." + key, args), true);
    }

    // --- Tooltip -----------------------------------------------------------------

    @Override
    //? if >=1.21 {
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        HolderLookup.Provider registries = context.registries();
    //?} else {
    /*public void appendHoverText(ItemStack stack, @Nullable Level context, List<Component> tooltip, TooltipFlag flag) {
        HolderLookup.Provider registries = context == null ? null : context.registryAccess();
    *///?}
        int parts = partCount(stack);
        if (parts == 0) {
            tooltip.add(gray("tooltip.create_controlling.blueprint_empty"));
            tooltip.add(gray("tooltip.create_controlling.blueprint_empty_copy"));
        } else {
            tooltip.add(gray("tooltip.create_controlling.blueprint_stored", parts));
            tooltip.add(gray("tooltip.create_controlling.blueprint_paste"));
            boolean shift = CCClientHooks.SHIFT_DOWN != null && CCClientHooks.SHIFT_DOWN.getAsBoolean();
            if (!shift || registries == null) {
                tooltip.add(Component.translatable("tooltip.create_controlling.blueprint_hold_shift")
                        .withStyle(ChatFormatting.DARK_GRAY));
            } else {
                Player player = CCClientHooks.CLIENT_PLAYER == null ? null : CCClientHooks.CLIENT_PLAYER.get();
                tooltip.add(Component.translatable("gui.create_controlling.materials").withStyle(ChatFormatting.GRAY));
                for (Component line : MaterialList.lines(ComponentCosts.totalOf(design(stack, registries)), player))
                    tooltip.add(Component.literal("  ").append(line));
            }
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }

    private static Component gray(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.GRAY);
    }
}
