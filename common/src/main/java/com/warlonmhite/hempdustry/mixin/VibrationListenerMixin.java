package com.warlonmhite.hempdustry.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.warlonmhite.hempdustry.advancement.ModCriteria;
import com.warlonmhite.hempdustry.effect.HushEffect;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hush: a vibration a Hushed body makes by itself reaches no sculk sensor, shrieker or Warden.
 *
 * <p>Every vibration listener in vanilla — the sensors, the shrieker, the Warden, the allay — hears
 * through {@code VibrationSystem.Listener#handleGameEvent}, which asks the listener's user
 * {@code isValidVibration} first. That test is where vanilla lets a sneaking player's steps go
 * ({@code #ignore_vibrations_sneaking}, firing {@code avoid_vibration} for Sneak 100) and where wool on
 * a body dampens it. Hush is the next answer after those: a vibration vanilla would have taken, from a
 * Hushed source, of an event in {@code #hempdustry:hush_silences}, is not valid. So a sneaking player's
 * steps are still vanilla's to ignore (and still earn Sneak 100), and Hush adds the rest of the body.
 *
 * <p>Stepping onto a sensor or a shrieker still trips it: their {@code stepOn} schedules a vibration
 * without asking, and Hush leaves that alone, as vanilla leaves it for a sneaking player.
 *
 * <p>Where vanilla would fire {@code avoid_vibration} — a listener that can trigger it (a sensor, a
 * Warden), a player as the source — this fires {@code hempdustry:hush_avoided_vibration}, for Sneak 420.
 * {@code @WrapOperation}, so other mods may wrap the same call.
 */
@Mixin(VibrationSystem.Listener.class)
public abstract class VibrationListenerMixin {

    @WrapOperation(method = "handleGameEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/gameevent/vibrations/VibrationSystem$User;isValidVibration(Lnet/minecraft/core/Holder;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)Z"))
    private boolean hempdustry$hush(VibrationSystem.User user, Holder<GameEvent> event, GameEvent.Context context,
                                    Operation<Boolean> original) {
        if (!original.call(user, event, context)) {
            return false;
        }
        Entity source = context.sourceEntity();
        if (source == null || !HushEffect.silences(source, event)) {
            return true;
        }
        if (user.canTriggerAvoidVibration() && source instanceof ServerPlayer player) {
            ModCriteria.HUSH_AVOIDED_VIBRATION.trigger(player);
        }
        return false;
    }
}
