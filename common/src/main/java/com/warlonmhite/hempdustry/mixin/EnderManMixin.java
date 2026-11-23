package com.warlonmhite.hempdustry.mixin;

import com.warlonmhite.hempdustry.effect.ModEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Endermen do not see a player under Averted Gaze. Vanilla's stare test, {@code isBeingStaredBy}, is the one place an
 * enderman decides whether a look provokes it, and a carved pumpkin answers it before anything else; the effect
 * answers it the same way, at the head of the method. An enderman already angry at the player stays angry: the effect
 * hides your eyes, not your sword.
 *
 * <p>Their walk into Ender Haze is added with the other walks hemp draws a mob on, at the end of {@code Mob}'s
 * constructor ({@link MobGoalsMixin}), so a modded enderman that overrides {@code registerGoals} still takes it.
 *
 * <p>An {@code @Inject}: nothing is replaced, so any number of mods can sit on the same method. The class extends
 * {@code Monster} only to match the target's hierarchy.
 */
@Mixin(Enderman.class)
public abstract class EnderManMixin extends Monster {
    protected EnderManMixin(EntityType<? extends Monster> entityType, Level world) {
        super(entityType, world);
    }

    @Inject(method = "isBeingStaredBy", at = @At("HEAD"), cancellable = true)
    private void hempdustry$avertedGaze(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (player.hasEffect(ModEffects.AVERTED_GAZE)) {
            cir.setReturnValue(false);
        }
    }
}
