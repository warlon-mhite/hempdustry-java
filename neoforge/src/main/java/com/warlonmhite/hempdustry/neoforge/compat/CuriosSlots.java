package com.warlonmhite.hempdustry.neoforge.compat;

import java.util.function.Consumer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Curios: what a player wears in its accessory slots, a backpack on the back among them. Loaded only when the mod
 * is present ({@code NeoForgePlatform#wornAccessories}).
 */
public final class CuriosSlots {
    private CuriosSlots() {
    }

    /** Hands {@code out} a copy of every stack {@code player} wears in a Curios slot. */
    public static void wornBy(Player player, Consumer<ItemStack> out) {
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            ResourceHandler<ItemResource> equipped = curios.getEquippedCurios();
            for (int index = 0; index < equipped.size(); index++) {
                ItemResource resource = equipped.getResource(index);
                long amount = equipped.getAmountAsLong(index);
                if (!resource.isEmpty() && amount > 0) {
                    out.accept(resource.toStack((int) Math.min(amount, Integer.MAX_VALUE)));
                }
            }
        });
    }
}
