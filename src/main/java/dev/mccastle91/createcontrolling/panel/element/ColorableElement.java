package dev.mccastle91.createcontrolling.panel.element;

import net.minecraft.world.item.DyeColor;

/** Elements with a player-configurable dye color (shared designer control). */
public interface ColorableElement {

    DyeColor color();

    void setColor(DyeColor color);
}
