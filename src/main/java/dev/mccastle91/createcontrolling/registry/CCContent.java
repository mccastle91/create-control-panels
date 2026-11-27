package dev.mccastle91.createcontrolling.registry;

import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.registry.CreateRegistries;
import com.tterrag.registrate.Registrate;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.entry.ItemEntry;
import dev.mccastle91.createcontrolling.CreateControlling;
import dev.mccastle91.createcontrolling.compat.create.ConsoleDisplayTarget;
import dev.mccastle91.createcontrolling.compat.create.CopycatConsoleBlock;
import dev.mccastle91.createcontrolling.compat.create.WrenchablePanelBlock;
import dev.mccastle91.createcontrolling.compat.create.contraption.ConsoleMovement;
import dev.mccastle91.createcontrolling.compat.create.contraption.ConsoleMovingInteraction;
//? if neoforge {
import dev.mccastle91.createcontrolling.compat.drivebysable.DriveBySableCompat;
//?}
import dev.mccastle91.createcontrolling.item.PanelBlueprintItem;
import dev.mccastle91.createcontrolling.panel.CCTypes;
import dev.mccastle91.createcontrolling.panel.CreatePanelBlockEntity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Supplier;

public final class CCContent {

    public static final Registrate REGISTRATE = Registrate.create(CreateControlling.MOD_ID);

    static {
        // Must precede item entries so they collect into our tab; the tab
        // icon defaults to the first registered item (the console).
        REGISTRATE.defaultCreativeTab("base", builder -> {
        }).register();
    }

    // With Drive-By-Sable installed the consoles are built as its cable
    // endpoints (see compat/drivebysable); its classes load in no other case

    private static WrenchablePanelBlock consoleBlock(BlockBehaviour.Properties properties) {
        //? if neoforge {
        if (net.neoforged.fml.ModList.get().isLoaded(DriveBySableCompat.MOD_ID))
            return DriveBySableCompat.consoleBlock(properties);
        //?}
        return new WrenchablePanelBlock(properties);
    }

    private static CopycatConsoleBlock copycatConsoleBlock(BlockBehaviour.Properties properties) {
        //? if neoforge {
        if (net.neoforged.fml.ModList.get().isLoaded(DriveBySableCompat.MOD_ID))
            return DriveBySableCompat.copycatConsoleBlock(properties);
        //?}
        return new CopycatConsoleBlock(properties);
    }

    public static final BlockEntry<WrenchablePanelBlock> CONTROL_PANEL = REGISTRATE
            .block("control_panel", CCContent::consoleBlock)
            .properties(p -> p.mapColor(MapColor.STONE)
                    .strength(1.5f)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .noOcclusion())
            .simpleItem()
            .register();

    /** A console that takes the look of a block applied to it; everything else is the same console. */
    public static final BlockEntry<CopycatConsoleBlock> COPYCAT_CONTROL_PANEL = REGISTRATE
            .block("copycat_control_panel", CCContent::copycatConsoleBlock)
            .properties(p -> p.mapColor(MapColor.GLOW_LICHEN)
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
            .validBlocks(CONTROL_PANEL, COPYCAT_CONTROL_PANEL)
            .register();

    public static final Supplier<ConsoleDisplayTarget> CONSOLE_DISPLAY_TARGET = REGISTRATE
            .simple("console", CreateRegistries.DISPLAY_TARGET, ConsoleDisplayTarget::new);

    // One sound event per kind of part sound, so sounds.json (and any resource pack) decides what each is
    static {
        for (dev.mccastle91.createcontrolling.panel.element.PartSound.Kind kind
                : dev.mccastle91.createcontrolling.panel.element.PartSound.Kind.values())
            REGISTRATE.simple(kind.path(), net.minecraft.core.registries.Registries.SOUND_EVENT,
                    () -> net.minecraft.sounds.SoundEvent.createVariableRangeEvent(kind.id()));
    }

    private CCContent() {
    }

    /** Trigger registration (class init) and hand common code its type holders. */
    public static void register() {
        CCTypes.PANEL_BLOCK_ENTITY = CONTROL_PANEL_ENTITY::get;
    }

    /** Once registries are frozen: makes consoles something a Display Link can target... */
    public static void commonSetup() {
        DisplayTarget.BY_BLOCK.register(CONTROL_PANEL.get(), CONSOLE_DISPLAY_TARGET.get());
        DisplayTarget.BY_BLOCK.register(COPYCAT_CONTROL_PANEL.get(), CONSOLE_DISPLAY_TARGET.get());
        // ...and something that keeps working, and can be clicked, on a contraption
        ConsoleMovement movement = new ConsoleMovement();
        ConsoleMovingInteraction interaction = new ConsoleMovingInteraction();
        for (net.minecraft.world.level.block.Block console : java.util.List.of(CONTROL_PANEL.get(), COPYCAT_CONTROL_PANEL.get())) {
            MovementBehaviour.REGISTRY.register(console, movement);
            MovingInteractionBehaviour.REGISTRY.register(console, interaction);
        }
    }
}
