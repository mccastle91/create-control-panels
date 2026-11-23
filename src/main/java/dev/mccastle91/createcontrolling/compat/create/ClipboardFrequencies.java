package dev.mccastle91.createcontrolling.compat.create;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides;
import dev.mccastle91.createcontrolling.panel.ConsolePicker;
import dev.mccastle91.createcontrolling.panel.FrequencyPair;
import dev.mccastle91.createcontrolling.panel.PanelBlock;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.util.McCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
//? if neoforge {
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
//?} else {
/*import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
*///?}

/**
 * Create's clipboard on a console, working as it does on a Redstone Link:
 * right-click a component to copy its frequency, left-click one to paste.
 *
 * Create's own handler only looks at its SmartBlockEntities' behaviours, and a
 * console is neither one block entity behaviour nor one frequency, so this is
 * our own handler for the component under the crosshair. It reads and writes
 * the clipboard exactly as Create's LinkBehaviour does (the copied values'
 * "Frequencies" entry, with "First" and "Last" items), so a frequency copied
 * from a real link pastes onto a component and the other way round.
 *
 * That layout is Create's internal format, not public API: if a Create update
 * changes it, {@link #copiedValues} and {@link #storeCopiedValues} are the
 * two places to follow it (the gametests copy to and from a real link to
 * notice).
 */
public final class ClipboardFrequencies {

    /** LinkBehaviour's clipboard key and item tags. */
    public static final String KEY = "Frequencies";
    public static final String TAG_FIRST = "First";
    public static final String TAG_LAST = "Last";

    private ClipboardFrequencies() {
    }

    public static void init() {
        //? if neoforge {
        NeoForge.EVENT_BUS.addListener(ClipboardFrequencies::onRightClick);
        NeoForge.EVENT_BUS.addListener(ClipboardFrequencies::onLeftClick);
        //?} else {
        /*MinecraftForge.EVENT_BUS.addListener(ClipboardFrequencies::onRightClick);
        MinecraftForge.EVENT_BUS.addListener(ClipboardFrequencies::onLeftClick);
        *///?}
    }

    private static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        PanelElement element = aimedComponent(event);
        if (element == null)
            return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        Level level = event.getLevel();
        // both sides, as Create does: the player's own copy of the item is right at once
        copy(event.getItemStack(), element, level.registryAccess());
        if (!level.isClientSide)
            tell(event.getEntity(), "message.create_controlling.frequency_copied", element);
    }

    private static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        PanelElement element = aimedComponent(event);
        if (element == null || !hasFrequency(event.getItemStack()))
            return;
        // every stage of the click, or a creative player breaks the console
        event.setCanceled(true);
        Level level = event.getLevel();
        if (level.isClientSide || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START)
            return;
        if (level.getBlockEntity(event.getPos()) instanceof PanelBlockEntity panel
                && paste(event.getItemStack(), element, level.registryAccess())) {
            panel.markLayoutChanged();
            tell(event.getEntity(), "message.create_controlling.frequency_pasted", element);
        }
    }

    /** The linkable component a player holding a clipboard is looking at, or null. */
    @Nullable
    private static PanelElement aimedComponent(PlayerInteractEvent event) {
        Player player = event.getEntity();
        if (!AllBlocks.CLIPBOARD.isIn(event.getItemStack()) || player.isSpectator() || player.isShiftKeyDown())
            return null;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PanelBlock) || !(level.getBlockEntity(pos) instanceof PanelBlockEntity panel))
            return null;
        // a left click carries no hit position: both clicks follow the player's line of sight
        Vec3 eye = player.getEyePosition().subtract(Vec3.atLowerCornerOf(pos));
        return ConsolePicker.pick(state, panel.shape(), panel.getLayout(), eye, player.getLookAngle(),
                        ClipboardFrequencies::isLinkable)
                .map(ConsolePicker.Pick::element).orElse(null);
    }

    private static boolean isLinkable(PanelElement element) {
        return element.isTransmitter() || element.isReceiver();
    }

    private static void tell(Player player, String key, PanelElement element) {
        player.displayClientMessage(Component.translatable(key,
                        Component.translatable("gui.create_controlling.element." + element.type().langId())
                                .withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GREEN), true);
    }

    // --- The clipboard's contents ------------------------------------------------

    /** Copies a component's frequency onto a clipboard, replacing whatever it had copied (as Create does). */
    public static void copy(ItemStack clipboard, PanelElement element, @Nullable HolderLookup.Provider registries) {
        CompoundTag frequency = new CompoundTag();
        frequency.put(TAG_FIRST, McCompat.saveStackOptional(element.frequency().first(), registries));
        frequency.put(TAG_LAST, McCompat.saveStackOptional(element.frequency().second(), registries));
        CompoundTag values = new CompoundTag();
        values.put(KEY, frequency);
        storeCopiedValues(clipboard, values);
    }

    /** Sets a component's frequency from a clipboard. Returns true if that changed it (the caller syncs). */
    public static boolean paste(ItemStack clipboard, PanelElement element, @Nullable HolderLookup.Provider registries) {
        if (!hasFrequency(clipboard) || !isLinkable(element))
            return false;
        CompoundTag frequency = copiedValues(clipboard).getCompound(KEY);
        FrequencyPair pasted = FrequencyPair.of(
                McCompat.loadStack(frequency.getCompound(TAG_FIRST), registries),
                McCompat.loadStack(frequency.getCompound(TAG_LAST), registries));
        FrequencyPair current = element.frequency();
        if (ItemStack.matches(current.first(), pasted.first()) && ItemStack.matches(current.second(), pasted.second()))
            return false;
        element.setFrequency(pasted);
        return true;
    }

    /** Whether a clipboard holds a copied frequency. */
    public static boolean hasFrequency(ItemStack clipboard) {
        CompoundTag values = copiedValues(clipboard);
        if (values == null || !values.contains(KEY))
            return false;
        CompoundTag frequency = values.getCompound(KEY);
        return frequency.contains(TAG_FIRST) && frequency.contains(TAG_LAST);
    }

    /** What a clipboard has copied, keyed by what it was copied from; null if nothing. */
    @Nullable
    public static CompoundTag copiedValues(ItemStack clipboard) {
        //? if >=1.21 {
        var content = clipboard.get(com.simibubi.create.AllDataComponents.CLIPBOARD_CONTENT);
        return content == null ? null : content.copiedValues().orElse(null);
        //?} else {
        /*return clipboard.getTagElement("CopiedValues");
        *///?}
    }

    private static void storeCopiedValues(ItemStack clipboard, CompoundTag values) {
        //? if >=1.21 {
        var content = clipboard.getOrDefault(com.simibubi.create.AllDataComponents.CLIPBOARD_CONTENT,
                com.simibubi.create.content.equipment.clipboard.ClipboardContent.EMPTY);
        clipboard.set(com.simibubi.create.AllDataComponents.CLIPBOARD_CONTENT,
                content.setType(ClipboardOverrides.ClipboardType.WRITTEN).setCopiedValues(values));
        //?} else {
        /*ClipboardOverrides.switchTo(ClipboardOverrides.ClipboardType.WRITTEN, clipboard);
        clipboard.getOrCreateTag().put("CopiedValues", values);
        *///?}
    }
}
