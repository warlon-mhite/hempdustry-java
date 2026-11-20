package com.warlonmhite.hempdustry.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.warlonmhite.hempdustry.client.anim.BodyPoses;
import com.warlonmhite.hempdustry.client.anim.Style;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The mod's poses go on a humanoid model where vanilla's use poses do: after the arms are posed for
 * what they hold and before the swing, the crouch and the idle sway, which then play on top of ours
 * as they do on the goat horn's or the spyglass's ({@link BodyPoses}). A kick takes the swing's place.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {

    @WrapOperation(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/HumanoidModel;setupAttackAnimation(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V"))
    private void hempdustry$poseThenSwing(HumanoidModel<?> model, HumanoidRenderState state, Operation<Void> original) {
        BodyPoses.apply(model, state);
        if (BodyPoses.kicking(state) < 0.0F || Style.now(false) == null) {
            original.call(model, state);
        }
    }
}
