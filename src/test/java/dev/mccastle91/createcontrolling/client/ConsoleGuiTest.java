package dev.mccastle91.createcontrolling.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How far a label that is too long for its button is shrunk. */
class ConsoleGuiTest {

    @Test
    void textThatFitsIsLeftAlone() {
        assertEquals(1f, ConsoleGui.fitScale(47, 54), "an English palette label");
        assertEquals(1f, ConsoleGui.fitScale(54, 54), "exactly as wide as its room");
        assertEquals(1f, ConsoleGui.fitScale(0, 54));
    }

    @Test
    void longerTextShrinksToItsRoomButNotPastTheFloor() {
        float gros = ConsoleGui.fitScale(61, 54);
        assertTrue(gros < 1f && 61 * gros <= 54.01f, "a little too long: shrunk to fit exactly");
        assertEquals(ConsoleGui.MIN_TEXT_SCALE, ConsoleGui.fitScale(101, 54),
                "far too long: stops at the smallest readable size (and is then cut short)");
        assertEquals(ConsoleGui.MIN_TEXT_SCALE, ConsoleGui.fitScale(90, 54), "just at the floor");
    }
}
