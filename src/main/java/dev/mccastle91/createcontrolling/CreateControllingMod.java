package dev.mccastle91.createcontrolling;

import dev.mccastle91.createcontrolling.client.CreateControllingClient;
import dev.mccastle91.createcontrolling.compat.create.CreateLinkPlatform;
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
        init(modEventBus);
    }
    //?} else {
    /*public CreateControllingMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, CCConfigSpec.SPEC);
        init(FMLJavaModLoadingContext.get().getModEventBus());
    }
    *///?}

    private void init(IEventBus modEventBus) {
        CreateControlling.init();
        CCContent.register();
        CCLink.PLATFORM = new CreateLinkPlatform();
        CCNetwork.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onConfigLoading);
        modEventBus.addListener(this::onConfigReloading);
        if (FMLEnvironment.dist == Dist.CLIENT)
            CreateControllingClient.init(modEventBus);
        // Game tests are discovered via @GameTestHolder annotation scanning
    }

    private void onConfigLoading(ModConfigEvent.Loading event) {
        CCConfigSpec.apply();
    }

    private void onConfigReloading(ModConfigEvent.Reloading event) {
        CCConfigSpec.apply();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(CCContent::commonSetup);
        CreateControlling.LOGGER.info("Setup complete. Create present: {}, Ponder present: {}",
                ModList.get().isLoaded("create"), ModList.get().isLoaded("ponder"));
    }
}
