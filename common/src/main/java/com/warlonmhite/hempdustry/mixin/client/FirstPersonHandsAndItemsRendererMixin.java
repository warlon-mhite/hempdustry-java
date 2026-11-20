package com.warlonmhite.hempdustry.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.warlonmhite.hempdustry.client.anim.BodyPoses;
import com.warlonmhite.hempdustry.client.anim.FirstPersonDraw;
import com.warlonmhite.hempdustry.client.anim.Style;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A smokeable being drawn rises to the mouth in first person, as food does (vanilla gives a use with
 * no animation no move at all), and a bong drawn with both hands puts the other hand's item away.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsAndItemsRendererMixin {

    private static final String SUBMIT_ARM = "submitArmWithItem(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;"
            + "Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;FFLnet/minecraft/world/InteractionHand;"
            + "FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V";

    @Inject(method = SUBMIT_ARM, at = @At("HEAD"), cancellable = true)
    private void hempdustry$otherHandOffTheBong(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state,
                                               float partialTicks, float xRot, InteractionHand hand, float attack,
                                               ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
                                               SubmitNodeCollector collector, int lightCoords, CallbackInfo info) {
        AvatarRenderState avatar = playerState.avatarRenderState;
        if (avatar != null && avatar.isUsingItem && avatar.useItemHand != hand && !itemStack.isEmpty()
                && BodyPoses.Hold.of(avatar.getUseItemStackForArm(avatar.useItemHand.asArm(avatar.mainArm))) == BodyPoses.Hold.BONG
                && Style.now(false) != null) {
            info.cancel();
        }
    }

    @Inject(method = SUBMIT_ARM, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemUseAnimation;hasCustomArmTransform()Z"))
    private void hempdustry$toTheMouth(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state,
                                       float partialTicks, float xRot, InteractionHand hand, float attack,
                                       ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
                                       SubmitNodeCollector collector, int lightCoords, CallbackInfo info) {
        BodyPoses.Hold hold = BodyPoses.Hold.of(itemStack);
        AvatarRenderState avatar = playerState.avatarRenderState;
        if (hold == null || avatar == null || Style.now(false) == null) {
            return;
        }
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? avatar.mainArm : avatar.mainArm.getOpposite();
        int duration = hand == InteractionHand.MAIN_HAND ? state.mainHandUseDuration : state.offHandUseDuration;
        FirstPersonDraw.apply(poseStack, arm, hold, state.useItemRemainingTicks - partialTicks + 1.0F, duration, inverseArmHeight);
    }
}
