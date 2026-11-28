package dev.mccastle91.createcontrolling;

import dev.mccastle91.createcontrolling.client.CreateControllingClient;
import dev.mccastle91.createcontrolling.compat.computercraft.ComputerCraftCompat;
import dev.mccastle91.createcontrolling.compat.create.ClipboardFrequencies;
//? if neoforge {
import dev.mccastle91.createcontrolling.compat.drivebysable.DriveBySableCompat;
//?}
import dev.mccastle91.createcontrolling.compat.create.CreateLinkPlatform;
import dev.mccastle91.createcontrolling.compat.create.contraption.ConsoleMovement;
import dev.mccastle91.createcontrolling.config.CCClientConfigSpec;
import dev.mccastle91.createcontrolling.config.CCConfigSpec;
import dev.mccastle91.createcontrolling.link.CCLink;
import dev.mccastle91.createcontrolling.network.CCNetwork;
import dev.mccastle91.createcontrolling.registry.CCContent;
//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
//?} else {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
*///?}

@Mod(CreateControlling.MOD_ID)
public class CreateControllingMod {

    //? if neoforge {
    public CreateControllingMod(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, CCConfigSpec.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, CCClientConfigSpec.SPEC);
        init(modEventBus);
    }
    //?} else {
    /*public CreateControllingMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, CCConfigSpec.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CCClientConfigSpec.SPEC);
        init(FMLJavaModLoadingContext.get().getModEventBus());
    }
    *///?}

    private void init(IEventBus modEventBus) {
        CreateControlling.init();
        CCContent.register();
        CCLink.PLATFORM = new CreateLinkPlatform();
        CCNetwork.register(modEventBus);
        ClipboardFrequencies.init();
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ConsoleMovement::onStartTracking);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ConsoleMovement::onEntityLeave);
        //?} else {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ConsoleMovement::onStartTracking);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ConsoleMovement::onEntityLeave);
        *///?}
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onConfigLoading);
        modEventBus.addListener(this::onConfigReloading);
        if (FMLEnvironment.dist == Dist.CLIENT)
            CreateControllingClient.init(modEventBus);
        // Optional: the class below is only ever loaded with CC: Tweaked present
        if (ModList.get().isLoaded("computercraft"))
            ComputerCraftCompat.init(modEventBus);
        //? if neoforge {
        // Optional: likewise only loaded with Drive-By-Sable present
        if (ModList.get().isLoaded(DriveBySableCompat.MOD_ID))
            DriveBySableCompat.init();
        //?}
        // Game tests are discovered via @GameTestHolder annotation scanning
    }

    private void onConfigLoading(ModConfigEvent.Loading event) {
        applyConfig(event);
    }

    private void onConfigReloading(ModConfigEvent.Reloading event) {
        applyConfig(event);
    }

    /** Each file loads on its own schedule: only read the one this event is about. */
    private static void applyConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() == CCConfigSpec.SPEC)
            CCConfigSpec.apply();
        else if (event.getConfig().getSpec() == CCClientConfigSpec.SPEC)
            CCClientConfigSpec.apply();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(CCContent::commonSetup);
        CreateControlling.LOGGER.info("Setup complete. Create present: {}, Ponder present: {}",
                ModList.get().isLoaded("create"), ModList.get().isLoaded("ponder"));
    }
}
