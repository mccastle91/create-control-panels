package dev.mccastle91.createcontrolling.compat.jei;

import dev.mccastle91.createcontrolling.client.PanelDesignerScreen;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Drag an item from JEI onto a frequency slot to set it, like Create's
 * filter slots. Only items count; the item is copied, never consumed.
 */
class FrequencyGhostHandler implements IGhostIngredientHandler<PanelDesignerScreen> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(PanelDesignerScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        Optional<ItemStack> item = ingredient.getItemStack();
        if (item.isEmpty())
            return targets;
        for (int slot = 0; slot < 2; slot++) {
            Rect2i area = screen.frequencyDropArea(slot);
            if (area != null)
                targets.add(new SlotTarget<>(screen, slot, area, item.get()));
        }
        return targets;
    }

    @Override
    public void onComplete() {
    }

    private record SlotTarget<I>(PanelDesignerScreen screen, int slot, Rect2i area, ItemStack stack)
            implements Target<I> {

        @Override
        public Rect2i getArea() {
            return area;
        }

        @Override
        public void accept(I ingredient) {
            screen.dropFrequency(slot, stack);
        }
    }
}
