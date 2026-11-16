package com.warlonmhite.hempdustry.mixin;

import com.warlonmhite.hempdustry.block.custom.EchoOgCropBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SculkBlock;
import net.minecraft.world.level.block.SculkSpreader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A sculk catalyst's charge feeds Echo OG standing on the sculk it reaches.
 *
 * <p>A catalyst turns the experience of a mob dying near it into charge that creeps through the sculk.
 * On a sculk block, {@code SculkBlock#attemptUseCharge} lets the charge decay most ticks; on one tick in
 * {@code chargeDecayRate}, outside {@code noGrowthRadius} of the catalyst, it asks {@code canPlaceGrowth}
 * (air above, few sensors and shriekers about) and, if so, spends {@code growthSpawnCost} (10) on a
 * sensor or a shrieker above — with the odds {@code charge / cost} when the charge is short of it.
 *
 * <p>A plant on the sculk is not air, so vanilla would grow nothing there. This answers that same test
 * first: where the block above is an Echo OG root with a step to take, the charge is spent on the plant
 * instead, at vanilla's price and with vanilla's odds, and nothing else is placed. Everywhere else vanilla
 * goes on as it was, with the same random draws. Only on a server level: a world-generation spreader
 * (a sculk patch) runs before any plant exists.
 */
@Mixin(SculkBlock.class)
public abstract class SculkBlockMixin {

    @Inject(method = "attemptUseCharge", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/SculkBlock;canPlaceGrowth(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;)Z"),
            cancellable = true)
    private void hempdustry$feedEchoOg(SculkSpreader.ChargeCursor cursor, LevelAccessor level, BlockPos originPos,
                                       RandomSource random, SculkSpreader spreader, boolean spreadVein,
                                       CallbackInfoReturnable<Integer> cir) {
        BlockPos sculk = cursor.getPos();
        if (!(level instanceof ServerLevel world)
                || !(world.getBlockState(sculk.above()).getBlock() instanceof EchoOgCropBlock plant)
                || !plant.eatsChargeOn(world, sculk)) {
            return;
        }
        int charge = cursor.getCharge();
        int cost = spreader.growthSpawnCost();
        if (random.nextInt(cost) < charge) {
            plant.eatCharge(world, sculk.above(), random);
        }
        cir.setReturnValue(Math.max(0, charge - cost));
    }
}
