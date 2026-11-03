package dev.mccastle91.createcontrolling.compat.ponder;

import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;

public class CCPonderScenes {

    public static void controlPanel(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("control_panel", "Buildable Control Consoles");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);
        scene.world().showSection(util.select().position(2, 1, 2), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(80)
                .text("Control Consoles carry buttons, switches, knobs, sliders, lamps, gauges and nixie readouts on a sloped face")
                .pointAt(util.vector().topOf(2, 1, 2))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showText(80)
                .text("Sneak-use with an empty hand to open the Console Designer; each component costs parts to mount")
                .pointAt(util.vector().topOf(2, 1, 2))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showText(100)
                .text("Each component carries its own Redstone Link frequency pair — consoles talk directly to Redstone Links, Linked Controllers, and other consoles")
                .independent(30)
                .attachKeyFrame();
        scene.idle(110);

        scene.overlay().showText(80)
                .text("Use the console to press buttons and flip switches; sneak-scroll on a knob or slider fine-tunes its value. Sneak-wrench to change the angle, or apply a Create casing to re-dress it")
                .pointAt(util.vector().topOf(2, 1, 2))
                .placeNearTarget();
        scene.idle(90);

        scene.markAsFinished();
    }
}
