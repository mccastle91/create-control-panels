package dev.mccastle91.createcontrolling.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Render types of our own. Extends {@link RenderType} only to reach its
 * protected state shards; never instantiated.
 */
public final class CCRenderTypes extends RenderType {

    /**
     * See-through block-atlas geometry that writes no depth, so whatever sits
     * inside it (a nixie digit) shows through whichever is drawn first.
     */
    public static final RenderType GLASS = create("create_controlling_glass",
            DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 256, false, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_TRANSLUCENT_SHADER)
                    .setTextureState(BLOCK_SHEET_MIPPED)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(true));

    private CCRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                          boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }
}
