package com.warlonmhite.hempdustry.mixin.client;

import com.warlonmhite.hempdustry.client.anim.BodyPoses;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The last word on a kick. Not Enough Animations smooths every leg towards its new pose at the end
 * of a player model's set-up, and a kick lasts the swing's 6 ticks, so smoothed it would barely
 * leave the ground; the knee is laid back on after it (the priority puts this after its own
 * injection), already eased by the kick's own curve. Without that mod it changes nothing.
 */
@Mixin(value = PlayerModel.class, priority = 1100)
public abstract class PlayerModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("RETURN"))
    private void hempdustry$kickAfterSmoothing(AvatarRenderState state, CallbackInfo info) {
        BodyPoses.kickAgain((PlayerModel) (Object) this, state);
    }
}
