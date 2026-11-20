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
 */
@Mixin(Player.class)
public abstract class PlayerAttackMixin {

    @Inject(method = "attack(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void hempdustry$kickTheFootbag(Entity target, CallbackInfo info) {
        Footbag.attacked((Player) (Object) this, target);
    }
}
