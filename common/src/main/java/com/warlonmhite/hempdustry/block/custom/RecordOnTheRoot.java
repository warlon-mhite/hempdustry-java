package com.warlonmhite.hempdustry.block.custom;

import com.warlonmhite.hempdustry.block.entity.custom.PlantRecordBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A plant of several blocks that keeps its record ({@link PlantRecordBlockEntity}) on its root alone: every
 * other block of it is a block-entity block that holds none.
 *
 * <p>Vanilla knows no such block. To it a block either holds a block entity in every state or in none
 * ({@code BlockState#hasBlockEntity} is {@code instanceof EntityBlock}), so chunk generation leaves a
 * placeholder for one on every block of the plant, and each placeholder off the root finds nothing to make
 * when the chunk goes live, and warns. This is how the mod's world generation asks a block which of its
 * states hold one before it leaves the placeholder ({@code WorldGenRegionMixin}).
 */
public interface RecordOnTheRoot extends EntityBlock {
    /** Whether the block in {@code state} holds the plant's record: true for its root, false for the rest. */
    boolean holdsRecord(BlockState state);

    /**
     * After another mod wrote a root's state on a block above the root and the crop put that block back, takes
     * away the record the write leaves there. The game makes a block entity from the state written, after the
     * block's own placing has run, so the record arrives once the block is put back and must go a moment later.
     * Nothing ever reads such a record (every loot pool and lookup goes through the root); left, it would sit in
     * the save for ever.
     */
    static void dropStrayRecord(Level world, BlockPos pos, RecordOnTheRoot plant) {
        if (world instanceof ServerLevel server) {
            // Scheduled, never executed: on the server thread execute() runs the task in place, before the record
            // it is meant to take away exists.
            BlockPos at = pos.immutable();
            server.getServer().schedule(new TickTask(server.getServer().getTickCount(), () -> {
                if (server.isLoaded(at) && server.getBlockEntity(at) instanceof PlantRecordBlockEntity
                        && !plant.holdsRecord(server.getBlockState(at))) {
                    server.removeBlockEntity(at);
                }
            }));
        }
    }
}
