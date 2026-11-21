package com.warlonmhite.hempdustry.fabric;

import com.warlonmhite.hempdustry.block.custom.DehumidifierBlock;
import com.warlonmhite.hempdustry.block.entity.custom.DehumidifierBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.ExtractionOnlyStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.level.material.Fluids;

/**
 * A Dehumidifier's tank as Fabric's transfer API sees it: water a pipe may take out, a bottle a level, as Fabric
 * reads a water cauldron. Nothing goes in: its water comes from the plants it dries. One per tank, kept by the
 * block entity. Outside a transaction it reads the block's levels; inside one it keeps what the transaction has
 * left, and writes it to the block when the outermost transaction commits.
 */
final class FabricDehumidifierStorage extends SnapshotParticipant<Integer>
        implements ExtractionOnlyStorage<FluidVariant>, SingleSlotStorage<FluidVariant> {
    private static final FluidVariant WATER = FluidVariant.of(Fluids.WATER);
    private static final int NONE = -1;

    private final DehumidifierBlockEntity tank;
    /** The levels an open transaction has left, or {@link #NONE} when none is open: the block's are the truth. */
    private int pending = NONE;

    FabricDehumidifierStorage(DehumidifierBlockEntity tank) {
        this.tank = tank;
    }

    private int levels() {
        return pending == NONE ? tank.levels() : pending;
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        int now = levels();
        int taken = resource.equals(WATER) ? (int) Math.min(now, maxAmount / FluidConstants.BOTTLE) : 0;
        if (taken <= 0) {
            return 0;
        }
        updateSnapshots(transaction);
        pending = now - taken;
        return taken * FluidConstants.BOTTLE;
    }

    @Override
    public boolean isResourceBlank() {
        return levels() == 0;
    }

    @Override
    public FluidVariant getResource() {
        return levels() == 0 ? FluidVariant.blank() : WATER;
    }

    @Override
    public long getAmount() {
        return levels() * FluidConstants.BOTTLE;
    }

    @Override
    public long getCapacity() {
        return DehumidifierBlock.MAX_LEVEL * FluidConstants.BOTTLE;
    }

    @Override
    protected Integer createSnapshot() {
        return pending;
    }

    @Override
    protected void readSnapshot(Integer snapshot) {
        pending = snapshot;
    }

    @Override
    protected void onFinalCommit() {
        if (pending != NONE) {
            tank.setLevels(pending);
            pending = NONE;
        }
    }
}
