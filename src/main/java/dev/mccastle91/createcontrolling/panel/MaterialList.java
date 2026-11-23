package dev.mccastle91.createcontrolling.panel;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Cost lines as the player reads them, coloured by what they hold: red with
 * none of an item, yellow with some, green with enough. Used by the designer's
 * palette, the blueprint's tooltip and the blueprint's own material list, so
 * all three count and colour the same way.
 */
public final class MaterialList {

    public enum Status {
        NONE(ChatFormatting.RED), SOME(ChatFormatting.YELLOW), ALL(ChatFormatting.GREEN);

        public final ChatFormatting color;

        Status(ChatFormatting color) {
            this.color = color;
        }
    }

    private MaterialList() {
    }

    public static Status status(int have, int need) {
        return have >= need ? Status.ALL : have > 0 ? Status.SOME : Status.NONE;
    }

    /**
     * "1/2 Redstone Lamp" in the colour of its status. A player in creative
     * pays nothing, so the line reads "2× Redstone Lamp" in green; with no
     * player at all it is left uncoloured.
     */
    public static MutableComponent line(ComponentCosts.Cost cost, @Nullable Player player) {
        if (player == null)
            return plain(cost);
        if (player.getAbilities().instabuild)
            return plain(cost).withStyle(Status.ALL.color);
        int have = ComponentCosts.held(player, cost);
        return Component.literal(have + "/" + cost.count() + " ").append(cost.name())
                .withStyle(status(have, cost.count()).color);
    }

    public static List<Component> lines(List<ComponentCosts.Cost> costs, @Nullable Player player) {
        List<Component> lines = new ArrayList<>();
        for (ComponentCosts.Cost cost : costs)
            lines.add(line(cost, player));
        return lines;
    }

    /** "1× Redstone Lamp, 2× Nixie Tube": for a message that names what is missing. */
    public static Component inline(List<ComponentCosts.Cost> costs) {
        MutableComponent text = Component.empty();
        for (int i = 0; i < costs.size(); i++) {
            if (i > 0)
                text.append(", ");
            text.append(plain(costs.get(i)));
        }
        return text;
    }

    /**
     * What the colours depend on, as one value: when it changes, text built
     * from {@link #line} is out of date.
     */
    public static int signature(List<ComponentCosts.Cost> costs, @Nullable Player player) {
        if (player == null)
            return 0;
        int signature = player.getAbilities().instabuild ? 1 : 2;
        for (ComponentCosts.Cost cost : costs)
            signature = 31 * signature + ComponentCosts.held(player, cost);
        return signature;
    }

    private static MutableComponent plain(ComponentCosts.Cost cost) {
        return Component.literal(cost.count() + "× ").append(cost.name());
    }
}
