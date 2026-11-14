package com.warlonmhite.hempdustry.block.custom;

import com.warlonmhite.hempdustry.block.entity.custom.PlantRecordBlockEntity;
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
}
