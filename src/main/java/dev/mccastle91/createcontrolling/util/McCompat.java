package dev.mccastle91.createcontrolling.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The vanilla APIs that differ between Minecraft 1.20.1 and 1.21.1, gathered
 * so the rest of the code calls one signature and Stonecutter branches live
 * here rather than throughout the tree.
 */
public final class McCompat {

    private McCompat() {
    }

    public static ResourceLocation id(String namespace, String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
        //?} else {
        /*return new ResourceLocation(namespace, path);
        *///?}
    }

    /** Registries are only consulted on 1.21+, where item stacks carry data components. */
    public static Tag saveStack(ItemStack stack, @Nullable HolderLookup.Provider registries) {
        //? if >=1.21 {
        return stack.save(registries);
        //?} else {
        /*return stack.save(new CompoundTag());
        *///?}
    }

    public static ItemStack loadStack(CompoundTag tag, @Nullable HolderLookup.Provider registries) {
        //? if >=1.21 {
        return ItemStack.parseOptional(registries, tag);
        //?} else {
        /*return ItemStack.of(tag);
        *///?}
    }

    public static String componentToJson(Component text, @Nullable HolderLookup.Provider registries) {
        //? if >=1.21 {
        return Component.Serializer.toJson(text, registries);
        //?} else {
        /*return Component.Serializer.toJson(text);
        *///?}
    }

    public static Component componentFromJson(String json, @Nullable HolderLookup.Provider registries) {
        //? if >=1.21 {
        Component text = Component.Serializer.fromJson(json, registries);
        //?} else {
        /*Component text = Component.Serializer.fromJson(json);
        *///?}
        return text == null ? Component.empty() : text;
    }

    /** Opaque ARGB tint of a dye. */
    public static int dyeRgb(DyeColor color) {
        //? if >=1.21 {
        return 0xFF000000 | color.getTextureDiffuseColor();
        //?} else {
        /*float[] c = color.getTextureDiffuseColors();
        return 0xFF000000 | ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255);
        *///?}
    }

    /** Mod-owned item data: the CUSTOM_DATA component on 1.21, the item's NBT on 1.20.1. */
    @Nullable
    public static CompoundTag getCustomData(ItemStack stack) {
        //? if >=1.21 {
        net.minecraft.world.item.component.CustomData data =
                stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
        //?} else {
        /*CompoundTag tag = stack.getTag();
        return tag == null ? null : tag.copy();
        *///?}
    }

    /** Writes the given keys; on 1.20.1 other NBT (display name etc.) is preserved. */
    public static void setCustomData(ItemStack stack, CompoundTag data) {
        //? if >=1.21 {
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(data));
        //?} else {
        /*CompoundTag tag = stack.getOrCreateTag();
        for (String key : data.getAllKeys())
            tag.put(key, data.get(key).copy());
        *///?}
    }

    public static boolean canReach(ServerPlayer player, BlockPos pos) {
        //? if >=1.21 {
        return player.canInteractWithBlock(pos, 4.5);
        //?} else {
        /*return player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) <= 64.0;
        *///?}
    }
}
