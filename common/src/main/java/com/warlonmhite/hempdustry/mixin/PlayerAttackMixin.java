package com.warlonmhite.hempdustry.mixin;

import com.warlonmhite.hempdustry.entity.Footbag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A hit on a footbag is a kick. {@code Player#attack} runs on the attacking client as well as on the
 * server, so the kicker sees the kick at the click and everyone else as the server takes the hit.
 * Watching only: the attack goes on exactly as it would.
 *
 * <p>Heard at the first thing an attack that goes ahead does ({@code isAutoSpinAttack}), not at its head: before it
 * stand vanilla's {@code cannotAttack} and, on NeoForge, the attack event a mod may cancel, and a kick played for an
 * attack either turned away swung a leg at nothing and held the arm's swing back.
 */
@Mixin(Player.class)
public abstract class PlayerAttackMixin {

    @Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isAutoSpinAttack()Z", ordinal = 0))
    private void hempdustry$kickTheFootbag(Entity target, CallbackInfo info) {
        Footbag.attacked((Player) (Object) this, target);
    }
}
