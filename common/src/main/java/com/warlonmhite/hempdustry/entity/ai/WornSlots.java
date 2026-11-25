package com.warlonmhite.hempdustry.entity.ai;

import com.warlonmhite.hempdustry.entity.ai.compat.TravelersBackSlot;
import com.warlonmhite.hempdustry.entity.ai.compat.TrinketsSlots;
import com.warlonmhite.hempdustry.platform.Services;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What a player wears outside the inventory, in the slots other mods add: a backpack on the back is carried as
 * surely as one in the hotbar. Vanilla's own equipment slots are the inventory's already
 * ({@code Inventory#getContainerSize} walks them), and so is a slot a mod adds to the inventory itself.
 *
 * <ul>
 *   <li>Curios (NeoForge), through the loader's side ({@code Platform#wornAccessories});</li>
 *   <li>Trinkets Updated, on either loader;</li>
 *   <li>Traveler's Backpack's own back slot, on either loader.</li>
 * </ul>
 *
 * Each is asked only when its mod is loaded, so a player with none of them never loads a line of their code.
 */
public final class WornSlots {
    private static final boolean TRINKETS = Services.PLATFORM.isModLoaded("trinkets_updated");
    private static final boolean TRAVELERS = Services.PLATFORM.isModLoaded("travelersbackpack");

    private WornSlots() {
    }

    /** Every stack {@code player} wears in another mod's slot; empty stacks are left out. */
    public static List<ItemStack> of(Player player) {
        List<ItemStack> worn = new ArrayList<>();
        Services.PLATFORM.wornAccessories(player, stack -> add(worn, stack));
        if (TRINKETS) {
            TrinketsSlots.wornBy(player, stack -> add(worn, stack));
        }
        if (TRAVELERS) {
            TravelersBackSlot.wornBy(player, stack -> add(worn, stack));
        }
        return worn;
    }

    private static void add(List<ItemStack> worn, ItemStack stack) {
        if (!stack.isEmpty()) {
            worn.add(stack);
        }
    }
}
