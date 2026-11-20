package com.warlonmhite.hempdustry.client.anim.compat;

import com.warlonmhite.hempdustry.client.anim.BodyPoses;
import com.warlonmhite.hempdustry.client.anim.Gestures;
import com.warlonmhite.hempdustry.network.PlayerGesture;
import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.api.BasicAnimation;
import dev.tr7zw.notenoughanimations.api.NotEnoughAnimationsApi;
import dev.tr7zw.notenoughanimations.versionless.animations.BodyPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * Not Enough Animations: it poses a player's parts after the game has, by the priority of the
 * animation that claims each one, then smooths every arm and leg towards its new pose. The mod's
 * poses are already on the model by then, so it claims the parts they use at the priority Not Enough
 * Animations gives vanilla's own held poses (the goat horn, the spyglass) and leaves them as they
 * are, exactly as it does for those: nothing below that priority (its idle and riding arms) covers
 * them, and its smoothing eases them in and out, as it eases its eating. Loaded only when the mod is present.
 */
public final class NotEnoughAnimations {
    /** Vanilla's held poses' priority in Not Enough Animations ({@code VanillaSingleHandedAnimation}). */
    private static final int HELD_POSE = 3100;

    private NotEnoughAnimations() {
    }

    public static void register() {
        NotEnoughAnimationsApi.registerAnimation(new Claim());
        // It picks the enabled animations once, the first time it poses a player; by a world join it
        // may already have.
        NotEnoughAnimationsApi.refreshEnabledAnimations();
    }

    private static final class Claim extends BasicAnimation {
        private BodyPart[] parts = new BodyPart[0];

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public boolean isValid(AbstractClientPlayer player, PlayerData data) {
            BodyPoses.Hold hold = player.isUsingItem() ? BodyPoses.Hold.of(player.getUseItem()) : null;
            boolean kick = Gestures.progress(player.getId(), PlayerGesture.Kind.KICK, player.tickCount) >= 0.0F;
            boolean cough = Gestures.progress(player.getId(), PlayerGesture.Kind.COUGH, player.tickCount) >= 0.0F;
            if (hold == null && !kick && !cough) {
                return false;
            }
            HumanoidArm main = player.getMainArm();
            HumanoidArm drawing = hold == null ? null
                    : player.getUsedItemHand() == InteractionHand.MAIN_HAND ? main : main.getOpposite();
            List<BodyPart> claimed = new ArrayList<>(3);
            if (drawing != null) {
                claimed.add(part(drawing));
                // The bong takes both hands; an empty hand cups a pipe's bowl.
                ItemStack other = player.getItemInHand(player.getUsedItemHand() == InteractionHand.MAIN_HAND
                        ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
                if (hold == BodyPoses.Hold.BONG || hold == BodyPoses.Hold.PIPE && other.isEmpty()) {
                    claimed.add(part(drawing.getOpposite()));
                }
            }
            if (cough) {
                // The free hand comes to the mouth, as BodyPoses picks it.
                HumanoidArm free = player.getOffhandItem().isEmpty() ? main.getOpposite()
                        : player.getMainHandItem().isEmpty() ? main : null;
                if (free != null && !claimed.contains(part(free))) {
                    claimed.add(part(free));
                }
            }
            if (kick) {
                claimed.add(main == HumanoidArm.RIGHT ? BodyPart.RIGHT_LEG : BodyPart.LEFT_LEG);
            }
            parts = claimed.toArray(new BodyPart[0]);
            return parts.length > 0;
        }

        private static BodyPart part(HumanoidArm arm) {
            return arm == HumanoidArm.RIGHT ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
        }

        @Override
        public BodyPart[] getBodyParts(AbstractClientPlayer player, PlayerData data) {
            return parts;
        }

        @Override
        public int getPriority(AbstractClientPlayer player, PlayerData data) {
            return HELD_POSE;
        }

        @Override
        public void apply(AbstractClientPlayer player, PlayerData data, PlayerModel model, BodyPart part, float delta,
                          float tickCounter) {
            // The pose is on the model already; claiming the part is what keeps it there.
        }
    }
}
