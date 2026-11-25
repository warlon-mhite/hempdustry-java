package com.warlonmhite.hempdustry.entity.ai.compat;

import com.tiviacz.travelersbackpack.attachment.AttachmentUtils;
import com.tiviacz.travelersbackpack.attachment.BackpackAttachment;
import java.util.function.Consumer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Traveler's Backpack's own back slot, the same on either loader: a backpack worn there is kept on the player,
 * outside the inventory. Only the slot of its own: when the mod puts a worn backpack in an accessory slot instead,
 * that slot is read on its own ({@code WornSlots}), so asking the mod for "the backpack worn" here would count it
 * twice. Loaded only when the mod is present.
 */
public final class TravelersBackSlot {
    private TravelersBackSlot() {
    }

    /** Hands {@code out} the backpack {@code player} wears in Traveler's Backpack's own slot, if any. */
    public static void wornBy(Player player, Consumer<ItemStack> out) {
        AttachmentUtils.getAttachment(player).map(BackpackAttachment::getBackpack).ifPresent(out);
    }
}
