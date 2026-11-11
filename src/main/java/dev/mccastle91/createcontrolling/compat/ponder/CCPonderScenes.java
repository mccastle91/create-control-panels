package dev.mccastle91.createcontrolling.compat.ponder;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import dev.mccastle91.createcontrolling.panel.ConsoleCasing;
import dev.mccastle91.createcontrolling.panel.ConsoleShape;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.PanelFaceGeometry;
import dev.mccastle91.createcontrolling.panel.element.NixieStripElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElementType;
import dev.mccastle91.createcontrolling.panel.element.PanelElements;
import dev.mccastle91.createcontrolling.panel.element.ReadoutElement;
import dev.mccastle91.createcontrolling.registry.CCContent;
import dev.mccastle91.createcontrolling.util.McCompat;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * The console's Ponder scenes. The schematics (see GameTestStructureGenerator)
 * hold an empty console whose low edge faces the camera; each scene mounts its
 * own parts, so layouts sit next to the steps that animate them. Nothing here
 * runs the link network: a scene sets what each part and block would show.
 *
 * Texts are looked up by number in en_us.json ({@code <scene>.text_N}, in the
 * order written here) — keep the two in step.
 */
public class CCPonderScenes {

    /** Every scene's console keeps the default shape while text points at its parts. */
    private static final ConsoleShape SHAPE = ConsoleShape.DEFAULT;

    // --- Building -----------------------------------------------------------

    public static void building(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("control_panel", "Building a Control Console");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        BlockPos console = new BlockPos(2, 2, 2);
        Selection consoleOnly = util.select().position(console);

        Spot label = Spot.of(PanelElements.LABEL, 1, 1);
        Spot needle = Spot.of(PanelElements.NEEDLE_METER, 1, 2.5f);
        Spot nixie = Spot.of(PanelElements.NIXIE, 8.5f, 2.5f);
        Spot knob = Spot.of(PanelElements.KNOB, 1, 8.5f);
        Spot toggle = Spot.of(PanelElements.TOGGLE_SWITCH, 6, 9);
        Spot covered = Spot.of(PanelElements.COVERED_SWITCH, 9, 8.5f);
        Spot lamp = Spot.of(PanelElements.LAMP, 13, 9);
        Spot lamp2 = Spot.of(PanelElements.LAMP, 13, 12.5f);
        Spot slider = Spot.of(PanelElements.SLIDER_H, 1, 13);

        scene.idle(10);
        scene.world().showSection(util.select().position(2, 1, 2), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(consoleOnly, Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(70)
                .text("A Control Console starts out as a blank, sloped face")
                .pointAt(onFace(console, 8, 8, 0))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(onFace(console, 8, 8, 2), Pointing.DOWN, 50).rightClick().whileSneaking();
        scene.idle(7);
        scene.overlay().showText(60)
                .text("Sneak-click it with an empty hand to open the Console Designer")
                .pointAt(onFace(console, 8, 8, 0))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);

        scene.overlay().showText(90)
                .text("Mount parts anywhere on the face; each one costs a few materials")
                .pointAt(onFace(console, 8, 4, 0))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(15);
        console(scene, console, be -> mount(be, PanelElements.LABEL, label).setText("BOILER"));
        scene.idle(6);
        console(scene, console, be -> mount(be, PanelElements.NEEDLE_METER, needle));
        scene.idle(6);
        console(scene, console, be -> mount(be, PanelElements.NIXIE, nixie));
        scene.idle(6);
        console(scene, console, be -> mount(be, PanelElements.KNOB, knob));
        scene.idle(6);
        console(scene, console, be -> mount(be, PanelElements.TOGGLE_SWITCH, toggle));
        scene.idle(6);
        console(scene, console, be -> mount(be, PanelElements.COVERED_SWITCH, covered).setColor(DyeColor.RED));
        scene.idle(6);
        console(scene, console, be -> mount(be, PanelElements.LAMP, lamp));
        scene.idle(6);
        console(scene, console, be -> mount(be, PanelElements.LAMP, lamp2).setColor(DyeColor.LIME));
        scene.idle(6);
        console(scene, console, be -> mount(be, PanelElements.SLIDER_H, slider).setValue(5));
        scene.idle(40);

        scene.overlay().showText(80)
                .colored(PonderPalette.INPUT)
                .text("Buttons, switches, knobs and sliders send redstone signals")
                .pointAt(knob.top(console))
                .placeNearTarget()
                .attachKeyFrame();
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.INPUT, "knob", knob.box(console), 80);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.INPUT, "toggle", toggle.box(console), 80);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.INPUT, "covered", covered.box(console), 80);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.INPUT, "slider", slider.box(console), 80);
        scene.idle(20);
        part(scene, console, PanelElements.TOGGLE_SWITCH, part -> part.setOn(true));
        scene.idle(10);
        for (int step = 1; step <= 8; step++) {
            int value = step;
            part(scene, console, PanelElements.KNOB, part -> part.setValue(value));
            scene.idle(4);
        }
        scene.idle(30);

        // What the receivers show from here on; body changes reload the console, so it is re-applied
        Consumer<PanelBlockEntity> readings = be -> {
            each(be, PanelElements.NEEDLE_METER, part -> part.setReceivedStrength(8));
            each(be, PanelElements.NIXIE, part -> part.setReceivedStrength(8));
            each(be, PanelElements.LAMP, part -> part.setReceivedStrength(15));
        };

        scene.overlay().showText(80)
                .colored(PonderPalette.OUTPUT)
                .text("Lamps, gauges and nixie tubes show the signals they receive")
                .pointAt(needle.top(console))
                .placeNearTarget()
                .attachKeyFrame();
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, "needle", needle.box(console), 80);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, "nixie", nixie.box(console), 80);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, "lamp", lamp.box(console), 80);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, "lamp2", lamp2.box(console), 80);
        scene.idle(20);
        for (int step = 2; step <= 8; step += 2) {
            int strength = step;
            console(scene, console, be -> {
                each(be, PanelElements.NEEDLE_METER, part -> part.setReceivedStrength(strength));
                each(be, PanelElements.NIXIE, part -> part.setReceivedStrength(strength));
            });
            scene.idle(5);
        }
        console(scene, console, readings);
        scene.idle(50);

        scene.overlay().showControls(onFace(console, 8, 8, 4), Pointing.DOWN, 75)
                .rightClick().whileSneaking().withItem(AllItems.WRENCH.asStack());
        scene.idle(7);
        scene.overlay().showText(70)
                .text("Sneak-click with a Wrench to change the slope")
                .pointAt(onFace(console, 8, 1, 0))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(15);
        for (int step = 0; step < 4; step++) {
            body(scene, consoleOnly, nbt -> nbt.put(PanelBlockEntity.TAG_SHAPE,
                    ConsoleShape.load(nbt.getCompound(PanelBlockEntity.TAG_SHAPE)).nextPreset().save()));
            console(scene, console, readings);
            scene.idle(15);
        }
        scene.idle(10);

        scene.overlay().showText(90)
                .text("Right-click with a Create casing to re-dress the console")
                .pointAt(onFace(console, 8, 8, 0))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(10);
        dress(scene, console, consoleOnly, AllBlocks.COPPER_CASING.asStack(), ConsoleCasing.COPPER, readings);
        scene.idle(35);
        dress(scene, console, consoleOnly, AllBlocks.BRASS_CASING.asStack(), ConsoleCasing.BRASS, readings);
        scene.idle(50);

        scene.markAsFinished();
    }

    private static void dress(SceneBuilder scene, BlockPos console, Selection consoleOnly, ItemStack item,
                              ConsoleCasing casing, Consumer<PanelBlockEntity> readings) {
        scene.overlay().showControls(onFace(console, 8, 8, 4), Pointing.DOWN, 25).rightClick().withItem(item);
        scene.idle(7);
        body(scene, consoleOnly, nbt -> nbt.putString(PanelBlockEntity.TAG_CASING, casing.id()));
        console(scene, console, readings);
    }

    // --- Linking ------------------------------------------------------------

    public static void linking(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("control_panel_linking", "Linking Consoles to Redstone");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        BlockPos console = new BlockPos(2, 2, 1);
        BlockPos receiver = new BlockPos(0, 2, 3);
        BlockPos transmitter = new BlockPos(4, 1, 3);
        BlockPos lever = new BlockPos(4, 1, 2);
        Selection receiverSide = util.select().fromTo(0, 1, 3, 0, 2, 3);
        Selection transmitterSide = util.select().fromTo(4, 1, 2, 4, 1, 3);

        Spot meter = Spot.of(PanelElements.NEEDLE_METER, 1.5f, 1.5f);
        Spot lamp = Spot.of(PanelElements.LARGE_LAMP, 10, 2);
        Spot toggle = Spot.of(PanelElements.TOGGLE_SWITCH, 2.5f, 10);
        Spot knob = Spot.of(PanelElements.KNOB, 6, 9.5f);
        Spot button = Spot.of(PanelElements.BUTTON, 11.5f, 10);

        console(scene, console, be -> {
            mount(be, PanelElements.NEEDLE_METER, meter);
            mount(be, PanelElements.LARGE_LAMP, lamp).setColor(DyeColor.LIME);
            mount(be, PanelElements.TOGGLE_SWITCH, toggle);
            mount(be, PanelElements.KNOB, knob);
            mount(be, PanelElements.BUTTON, button);
        });
        frequency(scene, util, receiver, new ItemStack(Items.REDSTONE), new ItemStack(Items.IRON_INGOT));
        frequency(scene, util, transmitter, new ItemStack(Items.REDSTONE), new ItemStack(Items.GOLD_INGOT));

        scene.idle(10);
        scene.world().showSection(util.select().position(2, 1, 1), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(console), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(70)
                .text("Every part on a console has its own Redstone Link frequency")
                .pointAt(onFace(console, 8, 8, 0))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        // A switch and a button drive a receiving link
        scene.world().showSection(receiverSide, Direction.DOWN);
        scene.idle(15);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, "toggle", toggle.box(console), 150);
        scene.overlay().showOutline(PonderPalette.GREEN, "receiver", util.select().position(receiver), 150);
        scene.overlay().showText(70)
                .colored(PonderPalette.GREEN)
                .text("Give a switch the same frequency as a Redstone Link…")
                .pointAt(toggle.top(console))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(toggle.top(console), Pointing.DOWN, 20).rightClick();
        scene.idle(7);
        part(scene, console, PanelElements.TOGGLE_SWITCH, part -> part.setOn(true));
        receive(scene, util, receiver, receiverSide, 15);
        scene.idle(10);
        scene.overlay().showText(60)
                .colored(PonderPalette.GREEN)
                .text("…and flipping it powers that link from anywhere in range")
                .pointAt(util.vector().centerOf(receiver))
                .placeNearTarget();
        scene.idle(70);
        scene.overlay().showControls(toggle.top(console), Pointing.DOWN, 20).rightClick();
        scene.idle(7);
        part(scene, console, PanelElements.TOGGLE_SWITCH, part -> part.setOn(false));
        receive(scene, util, receiver, receiverSide, 0);
        scene.idle(25);

        scene.overlay().showText(60)
                .text("A button sends a short pulse instead")
                .pointAt(button.top(console))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(20);
        for (int press = 0; press < 2; press++) {
            scene.overlay().showControls(button.top(console), Pointing.DOWN, 15).rightClick();
            scene.idle(7);
            part(scene, console, PanelElements.BUTTON, part -> part.applyDisplayStrength(15));
            receive(scene, util, receiver, receiverSide, 15);
            scene.idle(20);
            part(scene, console, PanelElements.BUTTON, part -> part.applyDisplayStrength(0));
            receive(scene, util, receiver, receiverSide, 0);
            scene.idle(15);
        }
        scene.idle(10);

        // Analog controls
        scene.overlay().showText(70)
                .text("Knobs and sliders send any strength from 0 to 15")
                .pointAt(knob.top(console))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(15);
        scene.overlay().showControls(knob.top(console), Pointing.DOWN, 45).rightClick();
        scene.idle(7);
        for (int step = 1; step <= 9; step++) {
            int value = step;
            part(scene, console, PanelElements.KNOB, part -> part.setValue(value));
            scene.idle(5);
        }
        scene.idle(15);
        scene.overlay().showText(70)
                .text("Click to step up, Sneak-click to step back down")
                .pointAt(knob.top(console))
                .placeNearTarget();
        scene.idle(15);
        scene.overlay().showControls(knob.top(console), Pointing.DOWN, 35).rightClick().whileSneaking();
        scene.idle(7);
        for (int step = 8; step >= 4; step--) {
            int value = step;
            part(scene, console, PanelElements.KNOB, part -> part.setValue(value));
            scene.idle(5);
        }
        scene.idle(35);

        // Indicators listen to a transmitting link
        scene.world().showSection(transmitterSide, Direction.DOWN);
        scene.idle(15);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, "lamp", lamp.box(console), 150);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, "meter", meter.box(console), 150);
        scene.overlay().showOutline(PonderPalette.BLUE, "transmitter", util.select().position(transmitter), 150);
        scene.overlay().showText(70)
                .colored(PonderPalette.BLUE)
                .text("Lamps and gauges listen on their frequency instead")
                .pointAt(lamp.top(console))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.world().toggleRedstonePower(transmitterSide);
        scene.effects().indicateRedstone(lever);
        scene.idle(3);
        part(scene, console, PanelElements.LARGE_LAMP, part -> part.setReceivedStrength(15));
        for (int step = 3; step <= 15; step += 3) {
            int strength = step;
            part(scene, console, PanelElements.NEEDLE_METER, part -> part.setReceivedStrength(strength));
            scene.idle(2);
        }
        scene.overlay().showText(70)
                .colored(PonderPalette.BLUE)
                .text("Any transmitter on it lights them up, even another console")
                .pointAt(util.vector().centerOf(transmitter))
                .placeNearTarget();
        scene.idle(80);
        scene.world().toggleRedstonePower(transmitterSide);
        scene.idle(3);
        part(scene, console, PanelElements.LARGE_LAMP, part -> part.setReceivedStrength(0));
        for (int step = 12; step >= 0; step -= 3) {
            int strength = step;
            part(scene, console, PanelElements.NEEDLE_METER, part -> part.setReceivedStrength(strength));
            scene.idle(2);
        }
        scene.idle(30);

        scene.markAsFinished();
    }

    /** A receiving link (and what it powers) takes the strength a console part sends. */
    private static void receive(SceneBuilder scene, SceneBuildingUtil util, BlockPos link, Selection powered,
                                int strength) {
        scene.world().toggleRedstonePower(powered);
        scene.world().modifyBlockEntityNBT(util.select().position(link), RedstoneLinkBlockEntity.class,
                nbt -> nbt.putInt("Receive", strength));
        if (strength > 0)
            scene.effects().indicateRedstone(link);
    }

    /** Shows a frequency pair in a Redstone Link's two slots. */
    private static void frequency(SceneBuilder scene, SceneBuildingUtil util, BlockPos link, ItemStack first,
                                  ItemStack last) {
        scene.world().modifyBlockEntityNBT(util.select().position(link), RedstoneLinkBlockEntity.class, nbt -> {
            var level = Minecraft.getInstance().level;
            if (level == null)
                return;
            nbt.put("FrequencyFirst", McCompat.saveStack(first, level.registryAccess()));
            nbt.put("FrequencyLast", McCompat.saveStack(last, level.registryAccess()));
        });
    }

    // --- Display Links ------------------------------------------------------

    public static void display(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("control_panel_display", "Showing Display Link text on a Console");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        BlockPos console = new BlockPos(2, 2, 1);
        BlockPos link = new BlockPos(1, 2, 3);
        Selection source = util.select().fromTo(1, 1, 3, 1, 2, 3);

        NixieStripElement tubes = PanelElements.NIXIE_STRIP.create();
        tubes.setSize(NixieStripElement.MAX_TUBES);
        tubes.setPos(1.5f, 1.5f);
        Spot strip = Spot.of(tubes);
        Spot board = Spot.of(PanelElements.DISPLAY_BOARD, 2, 7.5f);

        console(scene, console, be -> {
            mount(be, PanelElements.NIXIE_STRIP, strip).setSize(NixieStripElement.MAX_TUBES);
            mount(be, PanelElements.DISPLAY_BOARD, board).setLine(1, ReadoutElement.NO_LINE);
        });

        scene.idle(10);
        scene.world().showSection(util.select().position(2, 1, 1), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(console), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(70)
                .text("Nixie Strips and Display Boards are readouts for text")
                .pointAt(strip.top(console))
                .placeNearTarget()
                .attachKeyFrame();
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, "strip", strip.box(console), 70);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.OUTPUT, "board", board.box(console), 70);
        scene.idle(80);

        scene.world().showSection(source, Direction.DOWN);
        scene.idle(20);
        scene.overlay().showControls(onFace(console, 8, 8, 4), Pointing.DOWN, 40)
                .rightClick().withItem(AllBlocks.DISPLAY_LINK.asStack());
        scene.idle(7);
        scene.overlay().showText(70)
                .text("Point a Display Link at the console, just like at a Display Board")
                .pointAt(util.vector().centerOf(link))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(45);
        scene.overlay().showLine(PonderPalette.GREEN, util.vector().centerOf(link), onFace(console, 8, 8, 1), 150);
        time(scene, console, "06:30");
        scene.effects().indicateSuccess(console);
        scene.idle(35);

        scene.overlay().showText(70)
                .colored(PonderPalette.GREEN)
                .text("The readouts follow whatever the link reads")
                .pointAt(strip.top(console))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(25);
        time(scene, console, "06:31");
        scene.idle(30);
        time(scene, console, "06:32");
        scene.idle(30);

        scene.overlay().showText(80)
                .text("Each readout picks its line in the Console Designer; two can share one")
                .pointAt(board.top(console))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(30);
        time(scene, console, "06:33");
        scene.idle(30);
        time(scene, console, "06:34");
        scene.idle(40);

        scene.markAsFinished();
    }

    private static void time(SceneBuilder scene, BlockPos console, String text) {
        console(scene, console, be -> be.setDisplayLine(1, Component.literal(text)));
    }

    // --- Helpers ------------------------------------------------------------

    /** The schematics' console: on a floor, its low edge towards the camera (north). */
    private static BlockState consoleState() {
        return CCContent.CONTROL_PANEL.getDefaultState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);
    }

    /** Scene position of design point ({@code px}, {@code py}) on a console, {@code lift} px off its face. */
    private static Vec3 onFace(BlockPos console, double px, double py, double lift) {
        return Vec3.atLowerCornerOf(console).add(PanelFaceGeometry.of(consoleState(), SHAPE).point(px, py, lift));
    }

    /** Changes a console in place: its parts and what they show. */
    private static void console(SceneBuilder scene, BlockPos console, Consumer<PanelBlockEntity> change) {
        scene.world().modifyBlockEntity(console, PanelBlockEntity.class, change);
    }

    /** Changes a console's shape or casing through its data, and redraws its body. */
    private static void body(SceneBuilder scene, Selection console, Consumer<CompoundTag> change) {
        scene.world().modifyBlockEntityNBT(console, PanelBlockEntity.class, change, true);
    }

    /** Changes the first part of a type on a console. */
    private static <T extends PanelElement> void part(SceneBuilder scene, BlockPos console, PanelElementType<T> type,
                                                      Consumer<T> change) {
        console(scene, console, be -> {
            for (PanelElement element : be.getLayout().elements()) {
                if (element.type() == type) {
                    change.accept(cast(element));
                    return;
                }
            }
        });
    }

    /** Changes every part of a type on a console. */
    private static <T extends PanelElement> void each(PanelBlockEntity be, PanelElementType<T> type,
                                                      Consumer<T> change) {
        for (PanelElement element : be.getLayout().elements())
            if (element.type() == type)
                change.accept(cast(element));
    }

    @SuppressWarnings("unchecked")
    private static <T extends PanelElement> T cast(PanelElement element) {
        return (T) element;
    }

    private static <T extends PanelElement> T mount(PanelBlockEntity be, PanelElementType<T> type, Spot spot) {
        T element = type.create();
        element.setPos(spot.x(), spot.y());
        be.getLayout().addElement(element);
        return element;
    }

    /** Where a part sits on the face, px: for pointing at it and outlining it. */
    private record Spot(float x, float y, float width, float depth, float height) {

        static Spot of(PanelElementType<?> type, float x, float y) {
            PanelElement element = type.create();
            element.setPos(x, y);
            return of(element);
        }

        static Spot of(PanelElement element) {
            return new Spot(element.x(), element.y(), element.width(), element.depth(), element.height());
        }

        Vec3 top(BlockPos console) {
            return onFace(console, x + width / 2, y + depth / 2, height);
        }

        AABB box(BlockPos console) {
            double lift = Math.max(height, 0.5f);
            Vec3 min = onFace(console, x, y, 0);
            Vec3 max = min;
            for (int corner = 1; corner < 8; corner++) {
                Vec3 point = onFace(console, x + ((corner & 1) != 0 ? width : 0),
                        y + ((corner & 2) != 0 ? depth : 0), (corner & 4) != 0 ? lift : 0);
                min = new Vec3(Math.min(min.x, point.x), Math.min(min.y, point.y), Math.min(min.z, point.z));
                max = new Vec3(Math.max(max.x, point.x), Math.max(max.y, point.y), Math.max(max.z, point.z));
            }
            return new AABB(min, max);
        }
    }
}
