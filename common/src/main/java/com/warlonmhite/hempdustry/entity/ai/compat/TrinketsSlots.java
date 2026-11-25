package com.warlonmhite.hempdustry.entity.ai.compat;

import eu.pb4.trinkets.api.TrinketAttachment;
import eu.pb4.trinkets.api.TrinketsApi;
import java.util.function.Consumer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Trinkets Updated, on either loader: what a player wears in its accessory slots, a backpack on the back among
 * them. Loaded only when the mod is present ({@code WornSlots}).
 */
public final class TrinketsSlots {
    private TrinketsSlots() {
    }

    /** Hands {@code out} every stack {@code player} wears in a Trinkets slot, cosmetic ones included. */
    public static void wornBy(Player player, Consumer<ItemStack> out) {
        TrinketAttachment worn = TrinketsApi.getAttachment(player);
        if (worn != null) {
            worn.forEach((slot, stack) -> out.accept(stack));
        }
    }
}
