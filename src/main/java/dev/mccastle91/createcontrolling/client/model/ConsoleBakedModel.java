package dev.mccastle91.createcontrolling.client.model;

import com.simibubi.create.AllSpriteShifts;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import dev.mccastle91.createcontrolling.panel.ConsoleCasing;
import dev.mccastle91.createcontrolling.panel.ConsoleOrientation;
import dev.mccastle91.createcontrolling.panel.ConsoleShape;
import dev.mccastle91.createcontrolling.panel.CreatePanelBlockEntity;
import dev.mccastle91.createcontrolling.util.McCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.jetbrains.annotations.Nullable;
//? if neoforge {
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
//?} else {
/*import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
*///?}

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The console body. Swapped in for the block's JSON model at bake time; the
 * JSON model still supplies particle texture, item transforms and render
 * type. Shape and casing come from the block entity as model data, edge
 * connections from the blockstate. Meshes are cached per distinct console —
 * in practice a handful of keys.
 */
public class ConsoleBakedModel extends BakedModelWrapper<BakedModel> {

    private static final ResourceLocation PLATE = McCompat.id("create_controlling", "block/console_plate");

    private record Key(ConsoleOrientation orientation, ConsoleShape shape, ConsoleCasing casing,
                       ConsoleMesh.Connections connections) {
    }

    private final Map<Key, ConsoleMesh.Mesh> cache = new ConcurrentHashMap<>();
    private final Map<ConsoleCasing, TextureAtlasSprite> casingSprites = new EnumMap<>(ConsoleCasing.class);
    private TextureAtlasSprite plateSprite;

    public ConsoleBakedModel(BakedModel original) {
        super(original);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return getQuads(state, side, rand, ModelData.EMPTY, null);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData data, @Nullable RenderType renderType) {
        ConsoleMesh.Mesh mesh = cache.computeIfAbsent(keyFor(state, data), this::build);
        if (side == null)
            return mesh.quads();
        return side == mesh.bottomSide() ? mesh.bottom() : List.of();
    }

    // Item rendering swaps in whatever model these return. The wrapper's
    // defaults delegate to the placeholder JSON model, which answers with
    // itself — and it has no quads, so the item would be invisible.

    @Override
    public BakedModel applyTransform(net.minecraft.world.item.ItemDisplayContext context,
                                     com.mojang.blaze3d.vertex.PoseStack poseStack, boolean leftHand) {
        super.applyTransform(context, poseStack, leftHand);
        return this;
    }

    @Override
    public List<BakedModel> getRenderPasses(net.minecraft.world.item.ItemStack stack, boolean fabulous) {
        return List.of(this);
    }

    private static Key keyFor(@Nullable BlockState state, ModelData data) {
        if (state == null) // item
            return new Key(ConsoleOrientation.of(AttachFace.FLOOR, Direction.NORTH), ConsoleShape.DEFAULT,
                    ConsoleCasing.DEFAULT, ConsoleMesh.Connections.NONE);
        ConsoleShape shape = data.get(CreatePanelBlockEntity.SHAPE_PROPERTY);
        ConsoleCasing casing = data.get(CreatePanelBlockEntity.CASING_PROPERTY);
        if (shape == null)
            shape = ConsoleShape.defaultFor(
                    state.getValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACE));
        return new Key(ConsoleOrientation.of(state), shape, casing == null ? ConsoleCasing.DEFAULT : casing,
                ConsoleMesh.Connections.of(state));
    }

    private ConsoleMesh.Mesh build(Key key) {
        return ConsoleMesh.build(key.orientation(), key.shape(), key.connections(),
                casingSprite(key.casing()), connectedTexture(key.casing()), plateSprite());
    }

    /** Create's connected-texture sheet for a casing, if it has one. */
    @Nullable
    private static CTSpriteShiftEntry connectedTexture(ConsoleCasing casing) {
        switch (casing) {
            case ANDESITE: return AllSpriteShifts.ANDESITE_CASING;
            case BRASS: return AllSpriteShifts.BRASS_CASING;
            case COPPER: return AllSpriteShifts.COPPER_CASING;
            case RAILWAY: return AllSpriteShifts.RAILWAY_CASING;
            default: return null;
        }
    }

    private TextureAtlasSprite casingSprite(ConsoleCasing casing) {
        synchronized (casingSprites) {
            return casingSprites.computeIfAbsent(casing, c -> sprite(textureId(c.texture())));
        }
    }

    private TextureAtlasSprite plateSprite() {
        TextureAtlasSprite sprite = plateSprite;
        if (sprite == null)
            plateSprite = sprite = sprite(PLATE);
        return sprite;
    }

    private static TextureAtlasSprite sprite(ResourceLocation texture) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
    }

    private static ResourceLocation textureId(String id) {
        int colon = id.indexOf(':');
        return McCompat.id(id.substring(0, colon), id.substring(colon + 1));
    }
}
