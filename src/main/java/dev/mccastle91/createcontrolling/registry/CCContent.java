package dev.mccastle91.createcontrolling.registry;

import com.tterrag.registrate.Registrate;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.entry.ItemEntry;
import dev.mccastle91.createcontrolling.CreateControlling;
import dev.mccastle91.createcontrolling.compat.create.WrenchablePanelBlock;
import dev.mccastle91.createcontrolling.item.PanelBlueprintItem;
import dev.mccastle91.createcontrolling.panel.CCTypes;
import dev.mccastle91.createcontrolling.panel.CreatePanelBlockEntity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

public final class CCContent {

    public static final Registrate REGISTRATE = Registrate.create(CreateControlling.MOD_ID);

    static {
        // Must precede item entries so they collect into our tab; the tab
        // icon defaults to the first registered item (the console).
        REGISTRATE.defaultCreativeTab("base", builder -> {
        }).register();
    }

    public static final BlockEntry<WrenchablePanelBlock> CONTROL_PANEL = REGISTRATE
            .block("control_panel", WrenchablePanelBlock::new)
            .properties(p -> p.mapColor(MapColor.STONE)
                    .strength(1.5f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion())
            .simpleItem()
            .register();

    public static final ItemEntry<PanelBlueprintItem> PANEL_BLUEPRINT = REGISTRATE
            .item("panel_blueprint", PanelBlueprintItem::new)
            .register();

    public static final BlockEntityEntry<CreatePanelBlockEntity> CONTROL_PANEL_ENTITY = REGISTRATE
            .blockEntity("control_panel", CreatePanelBlockEntity::new)
            .validBlocks(CONTROL_PANEL)
            .register();

    private CCContent() {
    }

    /** Trigger registration (class init) and hand common code its type holders. */
    public static void register() {
        CCTypes.PANEL_BLOCK_ENTITY = CONTROL_PANEL_ENTITY::get;
    }
}
