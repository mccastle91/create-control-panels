package dev.mccastle91.createcontrolling.client.model;

import com.simibubi.create.AllSpriteShifts;
import com.simibubi.create.content.decoration.copycat.FilteredBlockAndTintGetter;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import dev.mccastle91.createcontrolling.panel.ConsoleCasing;
import dev.mccastle91.createcontrolling.panel.ConsoleOrientation;
import dev.mccastle91.createcontrolling.panel.ConsoleShape;
import dev.mccastle91.createcontrolling.panel.CreatePanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.PanelBlock;
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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.jetbrains.annotations.Nullable;
//? if neoforge {
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
//?} else {
/*import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
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
 *
 * A copycat console's body wears the textures of the block it copies (see
 * {@link ConsoleMesh.Skin}): one sprite and tint per layer of each of that
 * block's faces, stretched over the wedge. That block may draw in another
 * render layer than the console's own solid one (glass, leaves), so the
 * console then draws in both, and each layer gets a mesh of its own.
 */
public class ConsoleBakedModel extends BakedModelWrapper<BakedModel> {

    private static final ResourceLocation PLATE = McCompat.id("create_controlling", "block/console_plate");

    /** The layer a bare copycat console's frame draws in, as Create's copycats do. */
    private static final ChunkRenderTypeSet BARE_COPYCAT = ChunkRenderTypeSet.of(RenderType.cutoutMipped());

    /** @param layer the render layer the mesh is for; null for all of it at once (items, plain renderers) */
    /**
     * @param context the copied block's own model data for this spot in the world (its connected
     *                textures), or null if it has none. Compared by identity: it is made anew
     *                each time the chunk is rebuilt
     */
    private record Key(ConsoleOrientation orientation, ConsoleShape shape, ConsoleCasing casing,
                       ConsoleMesh.Connections connections, @Nullable BlockState material,
                       @Nullable DyeColor plate, @Nullable RenderType layer, @Nullable ModelData context) {
    }

    /**
     * What the copied block's model was told about its surroundings: Create's
     * connected textures (and any mod's that work through model data) read
     * which neighbours they join from it.
     */
    public static final ModelProperty<ModelData> WRAPPED_DATA = new ModelProperty<>();

    /**
     * The last mesh built with a context. Such meshes can't go in the cache
     * (every rebuild brings a new context, so it would only grow), but one
     * rebuild asks for the same mesh once per side, back to back.
     */
    private final ThreadLocal<Object[]> lastWithContext = new ThreadLocal<>();

    private final Map<Key, ConsoleMesh.Mesh> cache = new ConcurrentHashMap<>();
    private final Map<ConsoleCasing, TextureAtlasSprite> casingSprites = new EnumMap<>(ConsoleCasing.class);
    private TextureAtlasSprite plateSprite;
    private final Map<DyeColor, TextureAtlasSprite> dyedPlateSprites = new EnumMap<>(DyeColor.class);
    /** The casing of this model's block before its block entity says otherwise, and of its item. */
    private final ConsoleCasing defaultCasing;

    public ConsoleBakedModel(BakedModel original, ConsoleCasing defaultCasing) {
        super(original);
        this.defaultCasing = defaultCasing;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return getQuads(state, side, rand, ModelData.EMPTY, null);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData data, @Nullable RenderType renderType) {
        Key key = keyFor(state, data, renderType);
        ConsoleMesh.Mesh mesh;
        if (key.context() == null) {
            mesh = cache.computeIfAbsent(key, this::build);
        } else {
            Object[] last = lastWithContext.get();
            if (last != null && key.equals(last[0])) {
                mesh = (ConsoleMesh.Mesh) last[1];
            } else {
                mesh = build(key);
                lastWithContext.set(new Object[] { key, mesh });
            }
        }
        if (side == null)
            return mesh.quads();
        return side == mesh.bottomSide() ? mesh.bottom() : List.of();
    }

    /**
     * Renderers that draw a level of their own (Ponder scenes, schematics)
     * may not keep model data per position; ask the console itself then.
     */
    @Override
    public ModelData getModelData(net.minecraft.world.level.BlockAndTintGetter level, net.minecraft.core.BlockPos pos,
                                  BlockState state, ModelData data) {
        if (!data.has(CreatePanelBlockEntity.SHAPE_PROPERTY)
                && level.getBlockEntity(pos) instanceof CreatePanelBlockEntity console)
            data = console.getModelData();
        // a copied block is asked what it would look like here, with only the consoles
        // this one is joined to in view: its textures then connect across them, and to nothing else
        BlockState material = data.get(CreatePanelBlockEntity.MATERIAL_PROPERTY);
        if (material == null || !(state.getBlock() instanceof PanelBlock block))
            return data;
        ModelData wrapped;
        try {
            wrapped = modelOf(material).getModelData(new FilteredBlockAndTintGetter(level,
                    target -> target.equals(pos) || block.joinedToward(state, level, pos, target)),
                    pos, material, ModelData.EMPTY);
        } catch (RuntimeException e) {
            // another mod's model that can't cope with being asked about a block that isn't there
            return data;
        }
        if (wrapped == null || wrapped == ModelData.EMPTY)
            return data;
        return data.derive().with(WRAPPED_DATA, wrapped).build();
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        ChunkRenderTypeSet own = super.getRenderTypes(state, rand, data);
        BlockState material = data.get(CreatePanelBlockEntity.MATERIAL_PROPERTY);
        if (material != null)
            return ChunkRenderTypeSet.union(own, modelOf(material).getRenderTypes(material, rand, wrappedData(data)));
        ConsoleCasing casing = data.get(CreatePanelBlockEntity.CASING_PROPERTY);
        if ((casing == null ? defaultCasing : casing) == ConsoleCasing.COPYCAT)
            return ChunkRenderTypeSet.union(own, BARE_COPYCAT);
        return own;
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        BlockState material = data.get(CreatePanelBlockEntity.MATERIAL_PROPERTY);
        return material == null ? super.getParticleIcon(data) : modelOf(material).getParticleIcon(ModelData.EMPTY);
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

    private Key keyFor(@Nullable BlockState state, ModelData data, @Nullable RenderType layer) {
        if (state == null) // item
            return new Key(ConsoleOrientation.of(AttachFace.FLOOR, Direction.NORTH), ConsoleShape.DEFAULT,
                    defaultCasing, ConsoleMesh.Connections.NONE, null, null, null, null);
        ConsoleShape shape = data.get(CreatePanelBlockEntity.SHAPE_PROPERTY);
        ConsoleCasing casing = data.get(CreatePanelBlockEntity.CASING_PROPERTY);
        if (shape == null)
            shape = ConsoleShape.defaultFor(
                    state.getValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACE));
        return new Key(ConsoleOrientation.of(state), shape, casing == null ? defaultCasing : casing,
                ConsoleMesh.Connections.of(state), data.get(CreatePanelBlockEntity.MATERIAL_PROPERTY),
                data.get(CreatePanelBlockEntity.PLATE_PROPERTY), layer, data.get(WRAPPED_DATA));
    }

    private ConsoleMesh.Mesh build(Key key) {
        // the console's own textures are solid; a copied block's go in whichever layers it draws in
        boolean own = key.layer() == null || key.layer() == RenderType.solid();
        BlockState material = key.material();
        ConsoleMesh.Skin skin = null;
        boolean glowing = false;
        if (material != null) {
            ModelData context = key.context() == null ? ModelData.EMPTY : key.context();
            skin = side -> layers(material, side, key.layer(), context);
            glowing = material.emissiveRendering(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
                    net.minecraft.core.BlockPos.ZERO);
        } else if (key.casing() == ConsoleCasing.COPYCAT) {
            // bare: Create's copycat frame, cut out so its gaps are open like a copycat's
            boolean cutout = key.layer() == null || key.layer() == RenderType.cutoutMipped();
            List<ConsoleMesh.Layer> frame = cutout
                    ? List.of(new ConsoleMesh.Layer(casingSprite(key.casing()), -1)) : List.of();
            skin = side -> frame;
        }
        return ConsoleMesh.build(key.orientation(), key.shape(), key.connections(),
                casingSprite(key.casing()), connectedTexture(key.casing()), plateSprite(key.plate()), skin, own,
                glowing);
    }

    private static BakedModel modelOf(BlockState material) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(material);
    }

    private static ModelData wrappedData(ModelData data) {
        ModelData wrapped = data.get(WRAPPED_DATA);
        return wrapped == null ? ModelData.EMPTY : wrapped;
    }

    /**
     * What a block shows on one side in one render layer: the sprite, tint
     * and texture mapping of each quad its model has there. A model with
     * nothing on that side (not a plain cube) lends its particle texture, in
     * its first layer only.
     */
    public static List<ConsoleMesh.Layer> layers(BlockState material, Direction side, @Nullable RenderType layer) {
        return layers(material, side, layer, ModelData.EMPTY);
    }

    /** @param context the block's own model data for the spot it is shown at (see {@link #WRAPPED_DATA}) */
    public static List<ConsoleMesh.Layer> layers(BlockState material, Direction side, @Nullable RenderType layer,
                                                 ModelData context) {
        BakedModel model = modelOf(material);
        RandomSource rand = RandomSource.create(42L);
        // plain models hand out the same quads whatever layer is asked for, so
        // ask only for the layers the block draws in — or glass comes out solid
        if (layer != null && !model.getRenderTypes(material, rand, context).contains(layer))
            return List.of();
        List<ConsoleMesh.Layer> layers = new java.util.ArrayList<>();
        for (BakedQuad quad : model.getQuads(material, side, rand, context, layer))
            layers.add(layerOf(quad, side));
        if (layers.isEmpty())
            for (BakedQuad quad : model.getQuads(material, null, rand, context, layer))
                if (quad.getDirection() == side)
                    layers.add(layerOf(quad, side));
        if (layers.isEmpty()) {
            List<RenderType> types = model.getRenderTypes(material, rand, context).asList();
            if (layer == null || !types.isEmpty() && types.get(0) == layer)
                layers.add(new ConsoleMesh.Layer(model.getParticleIcon(ModelData.EMPTY), -1));
        }
        return layers;
    }

    private static ConsoleMesh.Layer layerOf(BakedQuad quad, Direction side) {
        return new ConsoleMesh.Layer(quad.getSprite(), quad.getTintIndex(), SkinMapping.of(quad.getVertices(), side));
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

    /** The face plate: the standard one, or the one painted with {@code dye}. */
    private TextureAtlasSprite plateSprite(@Nullable DyeColor dye) {
        if (dye != null)
            synchronized (dyedPlateSprites) {
                return dyedPlateSprites.computeIfAbsent(dye, d -> sprite(plateTexture(d)));
            }
        TextureAtlasSprite sprite = plateSprite;
        if (sprite == null)
            plateSprite = sprite = sprite(PLATE);
        return sprite;
    }

    /**
     * The block-atlas texture of the face plate: one drawn per dye (see
     * tools/plate_textures.py), and the standard plate for none or for gray.
     */
    public static ResourceLocation plateTexture(@Nullable DyeColor dye) {
        if (dye == null || dye == DyeColor.GRAY)
            return PLATE;
        return McCompat.id("create_controlling", "block/console_plate_" + dye.getName());
    }

    private static TextureAtlasSprite sprite(ResourceLocation texture) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
    }

    private static ResourceLocation textureId(String id) {
        int colon = id.indexOf(':');
        return McCompat.id(id.substring(0, colon), id.substring(colon + 1));
    }
}
