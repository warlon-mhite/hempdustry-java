package com.warlonmhite.hempdustry.mixin.client;

import com.warlonmhite.hempdustry.client.anim.BodyPoses;
import com.warlonmhite.hempdustry.client.anim.Style;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Both hands go to a bong while it is drawn, so whatever the other hand holds is put away for the
 * draw, as Not Enough Animations does for the bow; the stack itself is untouched, only not drawn.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void hempdustry$bothHandsOnTheBong(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo info) {
        BodyPoses.Drawing drawing = BodyPoses.Drawing.of(state);
        if (drawing == null || drawing.hold() != BodyPoses.Hold.BONG || Style.now(false) == null) {
            return;
        }
        if (drawing.arm() == HumanoidArm.RIGHT) {
            state.leftHandItemState.clear();
            state.leftHandItemStack = ItemStack.EMPTY;
            state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        } else {
            state.rightHandItemState.clear();
            state.rightHandItemStack = ItemStack.EMPTY;
            state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        }
    }
}
