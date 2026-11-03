package dev.mccastle91.createcontrolling.panel;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Who pays for mounted components and receives refunds. The edit logic only
 * sees this interface; a player in creative, or a test, can pay with nothing.
 */
public interface CostPayer {

    /**
     * Pays the whole cost or nothing. Returns the exact stacks taken (which
     * button, which dye), or null if something is missing.
     */
    @Nullable
    List<ItemStack> tryPay(List<ComponentCosts.Cost> cost);

    void refund(List<ItemStack> items);

    /** Pays nothing — so parts mounted this way refund nothing either. */
    CostPayer FREE = new CostPayer() {
        @Override
        public List<ItemStack> tryPay(List<ComponentCosts.Cost> cost) {
            return List.of();
        }

        @Override
        public void refund(List<ItemStack> items) {
        }
    };

    /** Pays from the player's main inventory and hotbar; creative players pay nothing. */
    static CostPayer of(Player player) {
        if (player.getAbilities().instabuild)
            return FREE;
        return new CostPayer() {
            @Override
            public List<ItemStack> tryPay(List<ComponentCosts.Cost> cost) {
                var items = player.getInventory().items;
                for (ComponentCosts.Cost need : cost) {
                    int have = 0;
                    for (ItemStack stack : items)
                        if (need.matches(stack))
                            have += stack.getCount();
                    if (have < need.count())
                        return null;
                }
                List<ItemStack> taken = new ArrayList<>();
                for (ComponentCosts.Cost need : cost) {
                    int remaining = need.count();
                    for (ItemStack stack : items) {
                        if (remaining == 0)
                            break;
                        if (!need.matches(stack))
                            continue;
                        int take = Math.min(remaining, stack.getCount());
                        taken.add(stack.copyWithCount(take));
                        stack.shrink(take);
                        remaining -= take;
                    }
                }
                player.getInventory().setChanged();
                return taken;
            }

            @Override
            public void refund(List<ItemStack> items) {
                for (ItemStack stack : items)
                    player.getInventory().placeItemBackInInventory(stack.copy());
            }
        };
    }
}
