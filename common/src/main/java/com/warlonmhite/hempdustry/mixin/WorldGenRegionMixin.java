package com.warlonmhite.hempdustry.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.warlonmhite.hempdustry.block.custom.RecordOnTheRoot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Leaves no block-entity placeholder on the blocks of a generated plant that hold no record.
 *
 * <p>When chunk generation places a block that can hold a block entity into a chunk still being
 * generated, {@code WorldGenRegion#setBlock} does not make the block entity: it leaves a placeholder
 * ({@code "DUMMY"}), and {@code LevelChunk#promotePendingBlockEntity} makes it once the chunk goes live,
 * warning "Tried to load a block entity … but failed" when the block makes none. Vanilla asks only
 * whether the block is an {@code EntityBlock}, never which of its states hold one. A wild plant of ours is
 * placed whole by a block column and keeps its record on its root alone ({@link RecordOnTheRoot}), so
 * every other block of it left a placeholder that came to nothing: a warning per block, each time a chunk
 * holding wild Beldía, Warped Kush, Crimson Weed or Ender Haze loaded.
 *
 * <p>So the placeholder is left only where the block holds a record. The root keeps its placeholder, and
 * so its record, made when the chunk goes live like any block entity's; every other block of every other
 * mod passes through untouched.
 *
 * <h2>Why {@code @WrapWithCondition}</h2>
 *
 * It skips the one call and leaves the rest of {@code setBlock} as vanilla wrote it, and several mods can
 * wrap the same call and compose, where a {@code @Redirect} is exclusive and two of them on one call are a
 * start-up crash with {@code defaultRequire: 1}.
 */
@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionMixin {
    @WrapWithCondition(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkAccess;setBlockEntityNbt(Lnet/minecraft/nbt/CompoundTag;)V"))
    private boolean hempdustry$onlyWhereARecordIsHeld(ChunkAccess chunk, CompoundTag placeholder,
            @Local(argsOnly = true) BlockState state) {
        return !(state.getBlock() instanceof RecordOnTheRoot plant) || plant.holdsRecord(state);
    }
}
