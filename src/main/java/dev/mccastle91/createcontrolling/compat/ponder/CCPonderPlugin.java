package dev.mccastle91.createcontrolling.compat.ponder;

import dev.mccastle91.createcontrolling.CreateControlling;
import dev.mccastle91.createcontrolling.registry.CCContent;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class CCPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return CreateControlling.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(CCContent.CONTROL_PANEL.getId())
                .addStoryBoard("control_panel", CCPonderScenes::building)
                .addStoryBoard("control_panel_linking", CCPonderScenes::linking)
                .addStoryBoard("control_panel_display", CCPonderScenes::display);
    }
}
