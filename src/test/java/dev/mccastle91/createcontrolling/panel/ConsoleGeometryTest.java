package dev.mccastle91.createcontrolling.panel;

import dev.mccastle91.createcontrolling.panel.element.ButtonElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElements;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The console's shared geometry: one orientation mapping drives the body,
 * the renderer, the click ray and the outline, so these properties hold for
 * all of them at once.
 */
class ConsoleGeometryTest {

    private static final double EPS = 1e-6;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        PanelElements.init();
        VanillaRegistries.createLookup();
    }

    /** Any face-attached block carries the same FACE/FACING properties as a console. */
    private static BlockState placement(AttachFace face, Direction facing) {
        return Blocks.LEVER.defaultBlockState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, face)
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACING, facing);
    }

    @Test
    void everyPlacementIsAProperRotation() {
        for (AttachFace face : AttachFace.values())
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                ConsoleOrientation o = ConsoleOrientation.of(face, facing);
                assertEquals(1, o.determinant(), EPS, face + "/" + facing + " must not mirror");
                Vec3 p = new Vec3(0.1, 0.2, 0.3);
                Vec3 back = o.unapply(o.apply(p));
                assertTrue(back.distanceTo(p) < EPS, "apply/unapply are inverse");
            }
    }

    @Test
    void floorConsoleFacesUpAndWallConsoleFacesOut() {
        assertTrue(ConsoleOrientation.of(AttachFace.FLOOR, Direction.NORTH).normal().distanceTo(new Vec3(0, 1, 0)) < EPS);
        assertTrue(ConsoleOrientation.of(AttachFace.CEILING, Direction.NORTH).normal().distanceTo(new Vec3(0, -1, 0)) < EPS);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Vec3 n = ConsoleOrientation.of(AttachFace.WALL, facing).normal();
            assertTrue(n.distanceTo(Vec3.atLowerCornerOf(facing.getNormal())) < EPS, "wall console faces " + facing);
        }
    }

    @Test
    void designAreaIsCentredOnTheSlopeAndRisesAtTheAngle() {
        ConsoleShape shape = ConsoleShape.of(30, 2);
        PanelFaceGeometry face = PanelFaceGeometry.model(shape);
        float margin = shape.designMargin();
        assertTrue(margin > 0, "a 30° face is longer than the design area");
        Vec3 nearLeft = face.point(0, 16, 0);
        assertTrue(face.point(0, 16 + margin, 0).distanceTo(new Vec3(0, 2 / 16.0, 1)) < EPS,
                "one margin below the design area is the near edge at front height");
        assertTrue(face.point(0, -margin, 0).distanceTo(new Vec3(0, shape.backHeight() / 16.0, 0)) < EPS,
                "one margin above it is the far edge at back height");
        double slope = Math.toDegrees(Math.atan2(face.point(0, 0, 0).y - nearLeft.y, nearLeft.z - face.point(0, 0, 0).z));
        assertEquals(30, slope, 1e-4);
        assertEquals(0, face.normal().dot(face.u()), EPS);
        assertEquals(0, face.normal().dot(face.v()), EPS);
        assertTrue(shape.backHeight() <= 16 + EPS);
    }

    @Test
    void shapesStayInsideTheBlock() {
        for (float angle = 0; angle <= 45; angle += 2.5f) {
            ConsoleShape shape = ConsoleShape.of(angle, 16);
            assertTrue(shape.backHeight() <= 16 + 1e-3, "back edge within the block at " + angle);
        }
        assertEquals(ConsoleShape.MIN_FLAT_THICKNESS, ConsoleShape.of(0, 0).frontHeight(), "flat needs thickness");
        assertEquals(15, ConsoleShape.of(13.4f, 2).nextPreset().angle(), EPS);
        assertEquals(0, ConsoleShape.of(45, 0).nextPreset().angle(), EPS, "presets wrap around");
        assertEquals(30, ConsoleShape.snapAngle(31.5f, 2), EPS);
        assertEquals(27, ConsoleShape.snapAngle(27, 2), EPS);
    }

    @Test
    void lookingAtAButtonPicksItInEveryPlacement() {
        PanelLayout layout = new PanelLayout();
        ButtonElement button = PanelElements.BUTTON.create();
        button.setPos(6, 6);
        layout.addElement(button);

        for (ConsoleShape shape : SHAPES)
            for (AttachFace face : AttachFace.values())
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    BlockState state = placement(face, facing);
                    ConsoleShape actual = shape.forFace(face);
                    PanelFaceGeometry geometry = PanelFaceGeometry.of(state, actual);
                    Vec3 target = geometry.point(7, 8, 0);
                    Vec3 eye = target.add(geometry.normal().scale(1.5)).add(geometry.v().scale(0.4));
                    Optional<ConsolePicker.Pick> pick = ConsolePicker.pick(state, actual, layout, eye, target.subtract(eye));
                    String where = face + "/" + facing + " at " + actual;
                    assertTrue(pick.isPresent(), "button picked: " + where);
                    assertEquals(button.id(), pick.get().element().id(), where);
                    assertTrue(pick.get().localX() >= 0 && pick.get().localX() <= button.width(), where);
                    assertTrue(pick.get().localY() >= 0 && pick.get().localY() <= button.depth(), where);

                    // aiming at bare plate picks nothing
                    Vec3 bare = geometry.point(13, 2, 0);
                    Vec3 bareEye = bare.add(geometry.normal());
                    assertTrue(ConsolePicker.pick(state, actual, layout, bareEye, bare.subtract(bareEye)).isEmpty(),
                            "bare plate: " + where);
                }
    }

    /** Every kind of slope, including the reversed ones walls may take. */
    private static final List<ConsoleShape> SHAPES = List.of(ConsoleShape.FLAT, ConsoleShape.DEFAULT,
            ConsoleShape.of(45, 0), ConsoleShape.of(15, 2, true), ConsoleShape.of(45, 0, true));

    @Test
    void theBodyLiesJustUnderTheFaceInEveryPlacement() {
        for (ConsoleShape shape : SHAPES)
            for (AttachFace face : AttachFace.values())
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    BlockState state = placement(face, facing);
                    ConsoleShape actual = shape.forFace(face);
                    PanelFaceGeometry geometry = PanelFaceGeometry.of(state, actual);
                    List<AABB> boxes = ConsoleShapes.collision(state, actual).toAabbs();
                    for (int x = 1; x <= 15; x += 7)
                        for (int y = 1; y <= 15; y += 7) {
                            Vec3 under = geometry.point(x, y, -0.25);
                            assertTrue(boxes.stream().anyMatch(box -> box.inflate(1e-6).contains(under)),
                                    "body under design (" + x + ", " + y + "): " + face + "/" + facing + " " + actual);
                        }
                }
    }

    @Test
    void ceilingFaceReadsUprightFromBelowAndKeepsItsSlope() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            // the player faced `facing` when placing it, so stands on the opposite side looking up
            Vec3 ahead = Vec3.atLowerCornerOf(facing.getNormal());
            Vec3 right = Vec3.atLowerCornerOf(facing.getClockWise().getNormal());
            ConsoleShape shape = ConsoleShape.DEFAULT.forFace(AttachFace.CEILING);
            PanelFaceGeometry face = PanelFaceGeometry.of(placement(AttachFace.CEILING, facing), shape);
            String where = "ceiling/" + facing;
            assertTrue(face.u().distanceTo(right) < EPS, "text runs to the player's right: " + where);
            assertTrue(face.v().dot(ahead) > 0.9, "lines run away from the player, top line nearest: " + where);
            // the edge nearest the player hugs the ceiling, the far edge hangs lower: unchanged by the flip
            assertTrue(face.point(8, 0, 0).y > face.point(8, 16, 0).y, "far edge hangs lower: " + where);
            assertTrue(face.normal().y < 0 && face.normal().dot(ahead) < 0, "face looks down at the player: " + where);
        }
    }

    @Test
    void floorFaceReadsUprightFromItsFront() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Vec3 ahead = Vec3.atLowerCornerOf(facing.getNormal());
            Vec3 right = Vec3.atLowerCornerOf(facing.getClockWise().getNormal());
            PanelFaceGeometry face = PanelFaceGeometry.of(placement(AttachFace.FLOOR, facing), ConsoleShape.DEFAULT);
            assertTrue(face.u().distanceTo(right) < EPS, "floor/" + facing);
            assertTrue(face.v().dot(ahead) < -0.9, "floor/" + facing);
            assertTrue(face.point(8, 0, 0).y > face.point(8, 16, 0).y, "far edge raised: floor/" + facing);
        }
    }

    @Test
    void wallConsolesSlopeEitherWay() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Vec3 out = Vec3.atLowerCornerOf(facing.getNormal());
            BlockState state = placement(AttachFace.WALL, facing);
            String where = "wall/" + facing;

            PanelFaceGeometry topOut = PanelFaceGeometry.of(state, ConsoleShape.of(30, 2, false));
            assertTrue(topOut.v().y < 0, "design runs down the wall: " + where);
            assertTrue(topOut.point(8, 0, 0).subtract(topOut.point(8, 16, 0)).dot(out) > 0.4,
                    "top edge stands out: " + where);
            assertTrue(topOut.normal().y < 0, "top-out faces down: " + where);

            PanelFaceGeometry bottomOut = PanelFaceGeometry.of(state, ConsoleShape.of(30, 2, true));
            assertTrue(bottomOut.v().y < 0, "still reads top to bottom: " + where);
            assertTrue(bottomOut.point(8, 16, 0).subtract(bottomOut.point(8, 0, 0)).dot(out) > 0.4,
                    "bottom edge stands out: " + where);
            assertTrue(bottomOut.normal().y > 0, "bottom-out faces up: " + where);
            assertTrue(bottomOut.u().distanceTo(topOut.u()) < EPS, "same reading direction: " + where);
        }
    }

    @Test
    void eachPlacementKeepsOnlyTheSlopesItAllows() {
        ConsoleShape bottomOut = ConsoleShape.of(30, 2, true);
        assertTrue(bottomOut.forFace(AttachFace.WALL).reversed(), "walls keep either way");
        assertFalse(bottomOut.forFace(AttachFace.FLOOR).reversed(), "floors slope one way");
        assertTrue(ConsoleShape.of(30, 2).forFace(AttachFace.CEILING).reversed(), "ceilings slope one way");
        assertFalse(ConsoleShape.of(0, 2, true).reversed(), "flat is never reversed");
        assertTrue(ConsoleShape.defaultFor(AttachFace.WALL).isFlat(), "walls start flat");
        assertFalse(ConsoleShape.defaultFor(AttachFace.FLOOR).isFlat());
        assertEquals(bottomOut, ConsoleShape.load(bottomOut.save()), "the raised edge is saved");
        assertEquals(ConsoleShape.of(30, 2), ConsoleShape.load(ConsoleShape.of(30, 2).save()));

        // the wall wrench runs up the presets top-out, then bottom-out, then flat
        ConsoleShape shape = ConsoleShape.FLAT;
        StringBuilder steps = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            shape = shape.nextPreset(true);
            steps.append(Math.round(shape.angle())).append(shape.reversed() ? "b " : "t ");
        }
        assertEquals("15t 30t 45t 15b 30b 45b 0t ", steps.toString());
        assertEquals(0, ConsoleShape.of(45, 0).nextPreset().angle(), EPS, "floors and ceilings never reverse");
    }

    @Test
    void pastingABlueprintChargesOnlyTheDifference() {
        // one large lamp (2 redstone lamps), paid for
        PanelLayout one = lamps(1);
        PanelEditOpsTest.Purse purse = new PanelEditOpsTest.Purse(10);

        PanelLayout two = lamps(2);
        assertTrue(ComponentCosts.settle(one, two, purse));
        assertEquals(8, purse.redstoneLamps, "only the second lamp is charged");
        assertTrue(purse.refunds.isEmpty());
        for (var element : two.elements())
            assertEquals(2, element.paid().get(0).getCount(), "each new part records what pays for it");

        PanelLayout again = lamps(2);
        assertTrue(ComponentCosts.settle(two, again, purse));
        assertEquals(8, purse.redstoneLamps, "re-pasting the same design is free");
        assertTrue(purse.refunds.isEmpty());

        assertTrue(ComponentCosts.settle(again, lamps(1), purse));
        assertEquals(2, purse.refunded(), "removed parts come back");

        PanelEditOpsTest.Purse broke = new PanelEditOpsTest.Purse(1);
        PanelLayout before = lamps(1);
        assertFalse(ComponentCosts.settle(before, lamps(3), broke), "can't cover the shortfall");
        assertEquals(1, broke.redstoneLamps, "nothing taken");
    }

    /** {@code n} large lamps, each recorded as paid for. */
    private static PanelLayout lamps(int n) {
        PanelLayout layout = new PanelLayout();
        for (int i = 0; i < n; i++) {
            var lamp = PanelElements.LARGE_LAMP.create();
            lamp.setPos(1 + 5 * i, 1);
            lamp.setPaid(List.of(new ItemStack(Items.REDSTONE_LAMP, 2)));
            layout.addElement(lamp);
        }
        return layout;
    }
}
