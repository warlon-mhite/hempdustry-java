package com.warlonmhite.hempdustry.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.warlonmhite.hempdustry.entity.ai.StashSmell;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A creeper (any mob of {@code #hempdustry:drawn_to_stash}) that noticed a player by their smell keeps them as far
 * as it noticed them. Vanilla drops a
 * target the moment it is further than the mob's follow range, so without this a creeper would notice a
 * player carrying buds at 20 blocks ({@link LivingEntityVisibilityMixin}) and give them up at the next tick.
 * The same factor, so the chase ends where the noticing would.
 */
@Mixin(TargetGoal.class)
public abstract class TargetGoalMixin {
    @Shadow
    @Final
    protected Mob mob;

    @WrapOperation(method = "canContinueToUse",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/goal/target/TargetGoal;getFollowDistance()D"))
    private double hempdustry$followTheSmell(TargetGoal goal, Operation<Double> original) {
        double range = original.call(goal);
        if (StashSmell.drawnToStash(mob) && mob.getTarget() instanceof Player player) {
            return range * StashSmell.reachFactor(player);
        }
        return range;
    }
}
