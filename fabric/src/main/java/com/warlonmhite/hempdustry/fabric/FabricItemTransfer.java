package com.warlonmhite.hempdustry.fabric;

import com.warlonmhite.hempdustry.platform.ItemTransfer;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Fabric's Transfer API, which also wraps every ordinary {@code Container} on its own. A partial
 * insert is simply never committed: closing the transaction without committing rolls it back.
 */
public final class FabricItemTransfer implements ItemTransfer {
    @Override
    public List<ItemStack> contents(ItemStack container) {
        if (container.isEmpty()) {
            return List.of();
        }
        Storage<ItemVariant> storage = ItemStorage.ITEM.find(container, ContainerItemContext.withConstant(container));
        return read(storage);
    }

    @Override
    public List<ItemStack> contents(Level level, BlockPos pos) {
        return read(ItemStorage.SIDED.find(level, pos, null));
    }

    private static List<ItemStack> read(Storage<ItemVariant> storage) {
        if (storage == null) {
            return List.of();
        }
        List<ItemStack> inside = new ArrayList<>();
        for (StorageView<ItemVariant> view : storage) {
            if (!view.isResourceBlank() && view.getAmount() > 0) {
                inside.add(view.getResource().toStack((int) Math.min(view.getAmount(), Integer.MAX_VALUE)));
            }
        }
        return inside;
    }

    @Override
    public Result insertWhole(Level level, BlockPos pos, Direction side, ItemStack stack) {
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, side);
        if (storage == null) {
            return Result.NO_STORAGE;
        }
        try (Transaction transaction = Transaction.openOuter()) {
            if (storage.insert(ItemVariant.of(stack), stack.getCount(), transaction) < stack.getCount()) {
                return Result.FULL;
            }
            transaction.commit();
            return Result.MOVED;
        }
    }
}
