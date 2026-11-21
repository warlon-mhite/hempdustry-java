package com.warlonmhite.hempdustry.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.warlonmhite.hempdustry.entity.ai.StashSmell;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A creeper notices a player carrying loose buds from further away ({@link StashSmell}). Vanilla's targeting
 * multiplies a mob's follow range by this visibility (sneaking, invisibility, a head worn against its own
 * kind); the smell is one more factor on what vanilla returns, so every one of vanilla's still applies, and
 * the line-of-sight check that follows is untouched.
 *
 * <p>On the return value rather than inside the method, so a loader's own visibility hook (NeoForge fires an
 * event in there) runs first and is multiplied too.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityVisibilityMixin {

    @ModifyReturnValue(method = "getVisibilityPercent", at = @At("RETURN"))
    private double hempdustry$smellOfBuds(double visibility, ServerLevel level, @Nullable Entity looker) {
        if (looker instanceof Creeper && (Object) this instanceof Player player) {
            return visibility * StashSmell.reachFactor(player);
        }
        return visibility;
    }
}
