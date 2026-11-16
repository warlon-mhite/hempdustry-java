package com.warlonmhite.hempdustry.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.warlonmhite.hempdustry.util.ModTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SpongeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A sponge takes the blocks of {@code #hempdustry:sponge_clears} as it takes kelp and seagrass: with their drops,
 * leaving the water gone. Vanilla names those four blocks one by one in the search
 * {@code SpongeBlock#removeWaterBreadthFirstSearch} runs, and skips anything else that holds its own water, so a
 * Maui Wowie plant would have stood in a dry hole holding a block of sea.
 *
 * <p>The last of the four tests (tall seagrass) also answers for the tag, so a block of ours goes down vanilla's
 * own branch: {@code dropResources}, then air. The test sits in a lambda, which the wrap reaches by its call.
 *
 * <p>The lambda is named by a pattern, not by its number: the number is the compiler's choice and differs from
 * one build of the game to the next (the loaders' builds already capture different arguments in it), and a
 * renumbered lambda named outright would stop the game at start-up. Any lambda of the search may match; only
 * the tall-seagrass test is changed.
 *
 * <p>On 1.21.11 the lambda has a name of its own on each loader: NeoForge runs the game in Mojang's names,
 * where it is {@code lambda$removeWaterBreadthFirstSearch$N}, and Fabric in intermediary, where every lambda
 * is a {@code method_} number fixed for this game version ({@code method_49829} here). Both are named; on
 * each loader one of them matches.
 */
@Mixin(SpongeBlock.class)
public abstract class SpongeBlockMixin {

    @WrapOperation(method = {"/^lambda\\$removeWaterBreadthFirstSearch\\$\\d+$/", "method_49829"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"))
    private static boolean hempdustry$clearOurs(BlockState state, Block block, Operation<Boolean> original) {
        return original.call(state, block) || block == Blocks.TALL_SEAGRASS && state.is(ModTags.Blocks.SPONGE_CLEARS);
    }
}
