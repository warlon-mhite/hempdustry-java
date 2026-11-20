package com.warlonmhite.hempdustry.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.warlonmhite.hempdustry.client.anim.BodyPoses;
import com.warlonmhite.hempdustry.client.anim.MouthHold;
import com.warlonmhite.hempdustry.client.anim.Style;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A smokeable being drawn is drawn at the mouth, carried by the head, not in the fist ({@link MouthHold}). */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void hempdustry$atTheMouth(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack itemStack,
                                       HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector collector,
                                       int lightCoords, CallbackInfo info) {
        if (item.isEmpty() || !(state instanceof HumanoidRenderState humanoid)
                || !(((RenderLayer<?, ?>) (Object) this).getParentModel() instanceof HumanoidModel<?> model)) {
            return;
        }
        BodyPoses.Drawing drawing = BodyPoses.Drawing.of(humanoid);
        if (drawing == null || drawing.arm() != arm || Style.now(false) == null) {
            return;
        }
        poseStack.pushPose();
        MouthHold.placeItem(poseStack, model, arm, drawing.hold());
        item.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        info.cancel();
    }
}
