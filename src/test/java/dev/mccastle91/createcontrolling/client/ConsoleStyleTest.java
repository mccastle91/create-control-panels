package dev.mccastle91.createcontrolling.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A resource pack's console_style.json: what it changes, and what it can't break. */
class ConsoleStyleTest {

    @AfterEach
    void reset() {
        for (StyleColor color : StyleColor.values())
            color.reset();
        for (CasingTheme theme : CasingTheme.values())
            theme.reset();
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void coloursAreSixOrEightHexDigits() {
        assertEquals(0xFF33CCFF, ConsoleStyle.parse(new JsonPrimitive("#33CCFF"), "test", "k"));
        assertEquals(0x8033CCFF, ConsoleStyle.parse(new JsonPrimitive("#8033ccff"), "test", "k"));
        assertEquals(0xFF000000, ConsoleStyle.parse(new JsonPrimitive("000000"), "test", "k"));
        assertNull(ConsoleStyle.parse(new JsonPrimitive("blue"), "test", "k"));
        assertNull(ConsoleStyle.parse(new JsonPrimitive("#FFF"), "test", "k"));
        assertNull(ConsoleStyle.parse(new JsonObject(), "test", "k"));
    }

    @Test
    void aPackChangesOnlyWhatItLists() {
        int text = StyleColor.TEXT.argb();
        int brassAccent = CasingTheme.BRASS.accent;
        ConsoleStyle.apply(json("{\"colors\": {\"nixie_glow\": \"#33CCFF\", \"grid_major\": \"#40FF0000\", \"needle\": \"oops\"},"
                + "\"themes\": {\"copper\": {\"title_text\": \"#FFFFFF\"}, \"nonsense\": {\"accent\": \"#000000\"}}}"), "test");
        assertEquals(0xFF33CCFF, StyleColor.NIXIE_GLOW.argb());
        assertEquals(0x33CCFF, StyleColor.NIXIE_GLOW.rgb());
        assertEquals(0x40FF0000, StyleColor.GRID_MAJOR.argb(), "alpha is kept where given");
        assertEquals(0xFFB3261E, StyleColor.NEEDLE.argb(), "an unreadable colour stays the mod's own");
        assertEquals(text, StyleColor.TEXT.argb());
        assertEquals(0xFFFFFF, CasingTheme.COPPER.titleText);
        assertEquals(brassAccent, CasingTheme.BRASS.accent);

        // a later pack stacks on top of an earlier one
        ConsoleStyle.apply(json("{\"colors\": {\"needle\": \"#00FF00\"}}"), "other");
        assertEquals(0xFF00FF00, StyleColor.NEEDLE.argb());
        assertEquals(0xFF33CCFF, StyleColor.NIXIE_GLOW.argb());
    }

    @Test
    void keysAreUnique() {
        Set<String> keys = new HashSet<>();
        for (StyleColor color : StyleColor.values())
            assertTrue(keys.add(color.key()), "duplicate colour key " + color.key());
    }
}
