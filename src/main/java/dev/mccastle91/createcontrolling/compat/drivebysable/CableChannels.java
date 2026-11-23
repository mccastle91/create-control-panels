package dev.mccastle91.createcontrolling.compat.drivebysable;

import dev.mccastle91.createcontrolling.panel.PanelLayout;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The cable channel each console part goes by: the part's name and a short
 * code from its id, "Knob 3FA2". The code never changes while the part
 * exists, however the console is edited around it, so a cable stays on the
 * part it was clicked onto. Knows nothing of Drive-By-Sable itself.
 */
public final class CableChannels {

    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("button", "Push Button"),
            Map.entry("covered_switch", "Guarded Switch"),
            Map.entry("slider_h", "Slider"),
            Map.entry("slider_v", "Slider"),
            Map.entry("lamp", "Indicator Lamp"),
            Map.entry("needle_meter", "Needle Gauge"),
            Map.entry("bar_meter", "Bar Meter"),
            Map.entry("bar_meter_v", "Bar Meter"),
            Map.entry("nixie", "Nixie Readout"));

    private CableChannels() {
    }

    /** Whether a part can carry a cable at all: controls send on one, lamps and meters read one. */
    public static boolean wirable(PanelElement element) {
        return element.isTransmitter() || element.isReceiver();
    }

    /** Every wirable part by its channel, in layout order. */
    public static Map<String, PanelElement> of(PanelLayout layout) {
        Map<String, PanelElement> channels = new LinkedHashMap<>();
        for (PanelElement element : layout.elements()) {
            if (!wirable(element))
                continue;
            String name = name(element, 4);
            // two parts of a kind sharing a code (1 in 65,536): the later one spells out more of its id
            if (channels.containsKey(name))
                name = name(element, 8);
            if (channels.containsKey(name))
                name = typeName(element) + " " + element.id();
            channels.put(name, element);
        }
        return channels;
    }

    /** The channel of the part with this id, or null if the layout has no such wirable part. */
    public static String of(PanelLayout layout, UUID id) {
        for (Map.Entry<String, PanelElement> entry : of(layout).entrySet())
            if (entry.getValue().id().equals(id))
                return entry.getKey();
        return null;
    }

    private static String name(PanelElement element, int digits) {
        String hex = String.format(Locale.ROOT, "%016X", element.id().getMostSignificantBits());
        return typeName(element) + " " + hex.substring(0, digits);
    }

    /** The part's English name: channels are stored by the cable network, so they can't follow the language. */
    static String typeName(PanelElement element) {
        String id = element.type().id();
        String known = NAMES.get(id);
        if (known != null)
            return known;
        StringBuilder name = new StringBuilder();
        // another mod's part ("modid:key_switch") is named by what follows its namespace
        for (String word : id.substring(id.indexOf(':') + 1).split("_")) {
            if (word.isEmpty())
                continue;
            if (name.length() > 0)
                name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }
}
