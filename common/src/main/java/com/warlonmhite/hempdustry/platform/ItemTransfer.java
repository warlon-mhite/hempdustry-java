package com.warlonmhite.hempdustry.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Items moved into a neighbour through the loader's own item-storage API — the only thing a pipe,
 * a storage network's interface or any other storage-only neighbour answers to. Each loader's API
 * also covers every ordinary vanilla container, so this is the general path, not a special case.
 */
public interface ItemTransfer {

    /**
     * Puts all of {@code stack} into the storage the block at {@code pos} exposes on {@code side},
     * <b>or none of it</b>: a partial insert is rolled back. {@code stack} itself is not changed.
     */
    Result insertWhole(Level level, BlockPos pos, Direction side, ItemStack stack);

    /**
     * What {@code container}, an item that holds items (a shulker box, a bundle, another mod's backpack), shows
     * inside it through the loader's item API, one level deep; nothing for an item that holds none. Only read:
     * nothing is moved.
     */
    List<ItemStack> contents(ItemStack container);

    enum Result {
        /** All of it went in. */
        MOVED,
        /** There is a storage there, but it took none of it. */
        FULL,
        /** Nothing at {@code pos} exposes a storage on that side. */
        NO_STORAGE
    }
}
