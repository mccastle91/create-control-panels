package dev.mccastle91.createcontrolling.panel;

import org.jetbrains.annotations.Nullable;

/**
 * The material a console is dressed in, applied by right-clicking it with the
 * matching Create casing. Purely cosmetic: every casing has the full feature
 * set. The face frame and the body sides take their look from Create's own
 * casing texture.
 */
public enum ConsoleCasing {

    ANDESITE("andesite", "create:andesite_casing", "create:block/andesite_casing", 0xFFA6A8A0),
    BRASS("brass", "create:brass_casing", "create:block/brass_casing", 0xFFE3B04C),
    COPPER("copper", "create:copper_casing", "create:block/copper_casing", 0xFFD9845A),
    RAILWAY("railway", "create:railway_casing", "create:block/railway_casing", 0xFFD6A647),
    INDUSTRIAL_IRON("industrial_iron", "create:industrial_iron_block", "create:block/industrial_iron_block", 0xFF6F7072);

    /** Freshly crafted consoles. */
    public static final ConsoleCasing DEFAULT = ANDESITE;

    private final String id;
    private final String itemId;
    private final String texture;
    private final int frameColor;

    ConsoleCasing(String id, String itemId, String texture, int frameColor) {
        this.id = id;
        this.itemId = itemId;
        this.texture = texture;
        this.frameColor = frameColor;
    }

    public String id() {
        return id;
    }

    /** Registry id of the Create item that applies this casing. */
    public String itemId() {
        return itemId;
    }

    /** Block-atlas texture used for the body and face frame. */
    public String texture() {
        return texture;
    }

    /** Opaque ARGB approximating the casing's frame, for flat (GUI) drawing. */
    public int frameColor() {
        return frameColor;
    }

    /** The Create item that applies this casing, if Create provides it. */
    public java.util.Optional<net.minecraft.world.item.Item> item() {
        int colon = itemId.indexOf(':');
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(
                dev.mccastle91.createcontrolling.util.McCompat.id(itemId.substring(0, colon), itemId.substring(colon + 1)));
    }

    public static ConsoleCasing byId(String id) {
        for (ConsoleCasing casing : values())
            if (casing.id.equals(id))
                return casing;
        return DEFAULT;
    }

    @Nullable
    public static ConsoleCasing byItemId(String itemId) {
        for (ConsoleCasing casing : values())
            if (casing.itemId.equals(itemId))
                return casing;
        return null;
    }
}
