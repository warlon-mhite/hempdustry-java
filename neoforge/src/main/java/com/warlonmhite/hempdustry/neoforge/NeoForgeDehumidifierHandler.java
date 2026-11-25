package com.warlonmhite.hempdustry.neoforge;

import com.warlonmhite.hempdustry.block.custom.DehumidifierBlock;
import com.warlonmhite.hempdustry.block.entity.custom.DehumidifierBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A Dehumidifier's tank as NeoForge's transfer API sees it: water a pipe may take out a level at a time, three levels
 * to the bucket as a water cauldron's, so 333 mB a level and the whole bucket for all three. A pipe that asked for a
 * full bucket only could never empty a tank whose pull is smaller, and a full tank stops drying; Fabric's side takes
 * a bottle a level the same way. Nothing goes in: its water comes from the plants it dries.
 * One per tank, kept by the block entity. Outside a transaction it reads the block's levels; inside one it keeps
 * what the transaction has left, and writes it to the block when the root transaction commits.
 */
final class NeoForgeDehumidifierHandler extends SnapshotJournal<Integer> implements ResourceHandler<FluidResource> {
    private static final FluidResource WATER = FluidResource.of(Fluids.WATER);
    private static final int BUCKET = 1000;
    private static final int NONE = -1;

    private final DehumidifierBlockEntity tank;
    /** The levels an open transaction has left, or {@link #NONE} when none is open: the block's are the truth. */
    private int pending = NONE;

    NeoForgeDehumidifierHandler(DehumidifierBlockEntity tank) {
        this.tank = tank;
    }

    private int levels() {
        return pending == NONE ? tank.levels() : pending;
    }

    @Override
    public int size() {
        return 1;
    }

    /** The water in {@code levels} levels: 333 mB each, the whole bucket for a full tank. */
    private static int amountOf(int levels) {
        return levels * BUCKET / DehumidifierBlock.MAX_LEVEL;
    }

    @Override
    public FluidResource getResource(int index) {
        return levels() > 0 ? WATER : FluidResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        return amountOf(levels());
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return BUCKET;
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return resource.equals(WATER);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return 0;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        if (index != 0 || !resource.equals(WATER)) {
            return 0;
        }
        int now = levels();
        int taken = now;
        while (taken > 0 && amountOf(taken) > amount) {
            taken--;
        }
        if (taken == 0) {
            return 0;
        }
        updateSnapshots(transaction);
        pending = now - taken;
        return amountOf(taken);
    }

    @Override
    protected Integer createSnapshot() {
        return pending;
    }

    @Override
    protected void revertToSnapshot(Integer snapshot) {
        pending = snapshot;
    }

    @Override
    protected void onRootCommit(Integer original) {
        if (pending != NONE) {
            tank.setLevels(pending);
            pending = NONE;
        }
    }
}
