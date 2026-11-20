package com.warlonmhite.hempdustry.client.anim.compat;

import com.warlonmhite.hempdustry.client.anim.Claims;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import traben.entity_model_features.EMFAnimationApi;

/**
 * Entity Model Features, which runs Fresh Animations' player pack: it animates a model right after the
 * game has posed it, which would undo a pose of ours. Its own animation hook lets the mod snapshot the
 * parts it posed just before, and lay them back over the pack's result after — blended by the claim's
 * weight, so the pack keeps the body, the legs and the breathing, and the arms and head ease between
 * its moves and ours. Loaded only when the mod is present.
 */
public final class ModelFeatures {
    private static final Map<ModelPart, Pending> PENDING = new IdentityHashMap<>();

    private ModelFeatures() {
    }

    public static boolean animates(EntityModel<?> model) {
        return EMFAnimationApi.isModelAnimatedByEMF(model);
    }

    public static void register() {
        try {
            EMFAnimationApi.registerAnimationHook(new EMFAnimationApi.EMFAnimationHook() {
                @Override
                public boolean onAnimationStart(AnimationContext context, boolean isCancelledByHook) {
                    ModelPart root = context.animatingModelRoot();
                    Claims.Claim claim = Claims.take(root);
                    if (claim != null) {
                        PENDING.put(root, new Pending(claim, claim.snapshot()));
                    }
                    return true;
                }

                @Override
                public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
                    Pending pending = PENDING.remove(context.animatingModelRoot());
                    if (pending != null) {
                        pending.claim.restore(pending.snapshot);
                    }
                }
            });
        } catch (Exception e) {
            com.warlonmhite.hempdustry.Hempdustry.LOGGER.warn("Entity Model Features refused the animation hook; "
                    + "a pack that animates players will cover the mod's poses", e);
        }
    }

    private record Pending(Claims.Claim claim, float[] snapshot) {
    }
}
