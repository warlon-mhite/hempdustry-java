package com.warlonmhite.hempdustry.client.anim;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The parts of each model posed by the mod this frame, for a pack that animates the model after it is
 * posed (Fresh Animations' player pack, through Entity Model Features). The pack would otherwise
 * overwrite them; instead the pose is snapshotted just before the pack animates and laid back over
 * its result once it is done, blended by the claim's weight. Render thread only, keyed by the model's
 * root part.
 */
public final class Claims {
    private static final Map<ModelPart, Claim> BY_ROOT = new IdentityHashMap<>();

    private Claims() {
    }

    /** Claims {@code claim}'s parts of the model under {@code root} for this frame, or lets them go when it has none. */
    public static void put(ModelPart root, Claim claim) {
        if (claim.parts().isEmpty()) {
            BY_ROOT.remove(root);
        } else {
            BY_ROOT.put(root, claim);
        }
    }

    public static @Nullable Claim get(ModelPart root) {
        return BY_ROOT.get(root);
    }

    public static @Nullable Claim take(ModelPart root) {
        return BY_ROOT.remove(root);
    }

    /**
     * Posed parts and how much of the pose is ours, {@code 0} to {@code 1}.
     */
    public record Claim(List<ModelPart> parts, float weight) {
        /** Each part's rotation and position as posed, before the pack animates them. */
        public float[] snapshot() {
            float[] values = new float[parts.size() * 6];
            for (int i = 0; i < parts.size(); i++) {
                ModelPart part = parts.get(i);
                values[i * 6] = part.xRot;
                values[i * 6 + 1] = part.yRot;
                values[i * 6 + 2] = part.zRot;
                values[i * 6 + 3] = part.x;
                values[i * 6 + 4] = part.y;
                values[i * 6 + 5] = part.z;
            }
            return values;
        }

        /** Lays {@code snapshot} back over what the pack made of each part, by {@link #weight()}. */
        public void restore(float[] snapshot) {
            for (int i = 0; i < parts.size() && i * 6 + 5 < snapshot.length; i++) {
                ModelPart part = parts.get(i);
                part.xRot = lerpAngle(part.xRot, snapshot[i * 6]);
                part.yRot = lerpAngle(part.yRot, snapshot[i * 6 + 1]);
                part.zRot = lerpAngle(part.zRot, snapshot[i * 6 + 2]);
                part.x += (snapshot[i * 6 + 3] - part.x) * weight;
                part.y += (snapshot[i * 6 + 4] - part.y) * weight;
                part.z += (snapshot[i * 6 + 5] - part.z) * weight;
            }
        }

        private float lerpAngle(float theirs, float ours) {
            return theirs + Mth.wrapDegrees((ours - theirs) * Mth.RAD_TO_DEG)
                    * Mth.DEG_TO_RAD * weight;
        }
    }
}
