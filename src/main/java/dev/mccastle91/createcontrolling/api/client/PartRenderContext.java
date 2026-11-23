package dev.mccastle91.createcontrolling.api.client;

import dev.mccastle91.createcontrolling.client.ComponentPainter;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * What a {@link ComponentRenderer} draws with.
 *
 * @param painter   draws boxes, plates and text in face pixels; its transform is at the part's corner
 * @param console   the console the part is on
 * @param font      the game's font
 * @param light     packed light at the console: pass it to the painter for anything that doesn't glow,
 *                  and {@link ComponentPainter#FULL_BRIGHT} for what does
 * @param trim      the parts' metal on this console (bezels, housings); follows its casing
 * @param iron      the dark metal of bases and levers
 * @param plain     a plain surface to tint any colour: caps, lenses, markings
 * @param hanging   the console is on a ceiling: standing text should turn to read from below
 * @param onWall    the console is on a wall: the player looks straight at the face
 * @param partialTick how far into the current tick this frame is, 0-1
 */
public record PartRenderContext(ComponentPainter painter, PanelBlockEntity console, Font font, int light,
                                TextureAtlasSprite trim, TextureAtlasSprite iron, TextureAtlasSprite plain,
                                boolean hanging, boolean onWall, float partialTick) {
}
