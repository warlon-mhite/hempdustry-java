package com.warlonmhite.hempdustry.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.warlonmhite.hempdustry.client.anim.BodyPoses;
import com.warlonmhite.hempdustry.client.anim.FirstPersonDraw;
import com.warlonmhite.hempdustry.client.anim.Style;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
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
 *
 * <p>This game draws the first-person arms from the player itself, in {@code ItemInHandRenderer}: what is
 * used, with which hand and for how long is asked of the player, as vanilla's own arm transform asks it.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    private static final String RENDER_ARM = "renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;"
            + "FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;F"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V";

    @Inject(method = RENDER_ARM, at = @At("HEAD"), cancellable = true)
    private void hempdustry$otherHandOffTheBong(AbstractClientPlayer player, float partialTicks, float xRot,
                                               InteractionHand hand, float attack, ItemStack itemStack,
                                               float inverseArmHeight, PoseStack poseStack,
                                               SubmitNodeCollector collector, int lightCoords, CallbackInfo info) {
        if (player.isUsingItem() && player.getUsedItemHand() != hand && !itemStack.isEmpty()
                && BodyPoses.Hold.of(player.getUseItem()) == BodyPoses.Hold.BONG
                && Style.now(false) != null) {
            info.cancel();
        }
    }

    @Inject(method = RENDER_ARM, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemUseAnimation;hasCustomArmTransform()Z"))
    private void hempdustry$toTheMouth(AbstractClientPlayer player, float partialTicks, float xRot,
                                       InteractionHand hand, float attack, ItemStack itemStack,
                                       float inverseArmHeight, PoseStack poseStack,
                                       SubmitNodeCollector collector, int lightCoords, CallbackInfo info) {
        BodyPoses.Hold hold = BodyPoses.Hold.of(itemStack);
        if (hold == null || Style.now(false) == null) {
            return;
        }
        HumanoidArm arm = BodyPoses.armOf(hand, player.getMainArm());
        FirstPersonDraw.apply(poseStack, arm, hold, player.getUseItemRemainingTicks() - partialTicks + 1.0F,
                itemStack.getUseDuration(player), inverseArmHeight);
    }
}
