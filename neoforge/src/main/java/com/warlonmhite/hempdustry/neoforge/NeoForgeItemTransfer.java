package com.warlonmhite.hempdustry.neoforge;

import com.warlonmhite.hempdustry.platform.ItemTransfer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * NeoForge's transfer API, shaped like Fabric's: a partial insert is never committed, and closing the
 * transaction without committing rolls it back. Vanilla's containers answer through it too; the
 * mod's own machines are added to it where the mod starts ({@code HempdustryNeoForge}).
 */
public final class NeoForgeItemTransfer implements ItemTransfer {
    @Override
    public List<ItemStack> contents(ItemStack container) {
        if (container.isEmpty()) {
            return List.of();
        }
        ResourceHandler<ItemResource> handler = container.getCapability(Capabilities.Item.ITEM, ItemAccess.forStack(container));
        if (handler == null) {
            return List.of();
        }
        List<ItemStack> inside = new ArrayList<>();
        for (int index = 0; index < handler.size(); index++) {
            ItemResource resource = handler.getResource(index);
            long amount = handler.getAmountAsLong(index);
            if (!resource.isEmpty() && amount > 0) {
                inside.add(resource.toStack((int) Math.min(amount, Integer.MAX_VALUE)));
            }
        }
        return inside;
    }

    @Override
    public Result insertWhole(Level level, BlockPos pos, Direction side, ItemStack stack) {
        ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, pos, side);
        if (handler == null) {
            return Result.NO_STORAGE;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            if (handler.insert(ItemResource.of(stack), stack.getCount(), transaction) < stack.getCount()) {
                return Result.FULL;
            }
            transaction.commit();
            return Result.MOVED;
        }
    }
}
