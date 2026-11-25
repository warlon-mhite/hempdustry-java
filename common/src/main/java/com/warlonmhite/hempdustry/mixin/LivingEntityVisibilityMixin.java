package com.warlonmhite.hempdustry.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.warlonmhite.hempdustry.entity.ai.StashSmell;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A creeper, or any mob of {@code #hempdustry:drawn_to_stash}, sees more of a player carrying loose buds
 * ({@link StashSmell}). Vanilla's targeting multiplies a mob's follow range by this visibility (sneaking,
 * invisibility, a head worn against its own kind); the smell is one more factor on what vanilla returns, held at
 * full visibility, so every one of vanilla's still applies, the reach never passes the follow range, and the
 * line-of-sight check that follows is untouched.
 *
 * <p>On the return value rather than inside the method, so a loader's own visibility hook (NeoForge fires an
 * event in there) runs first and is multiplied too.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityVisibilityMixin {

    @ModifyReturnValue(method = "getVisibilityPercent", at = @At("RETURN"))
    private double hempdustry$smellOfBuds(double visibility, ServerLevel level, @Nullable Entity looker) {
        if (looker != null && StashSmell.drawnToStash(looker) && (Object) this instanceof Player player) {
            return StashSmell.visibility(visibility, player);
        }
        return visibility;
    }
}
