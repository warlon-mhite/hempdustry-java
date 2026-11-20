package com.warlonmhite.hempdustry.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * A draw in first person, as your own eyes would see it: the smokeable is at your mouth, just under
 * the middle of the view, its mouth end leaving the bottom of the screen and the rest pointing away
 * from you. The spliff and the pipe stand on edge, thin, the pipe's bowl upright; the vaporizer lies face
 * up, turned round so its mouthpiece is the near end. The spliff's lit end, the pipe's bowl and the
 * vaporizer's oven end are the far ends; the bong's lip is in the middle and it leans away, so the smoke
 * filling it shows down its neck. The item rises there over a few ticks and stays for
 * the draw; the bong bobs a little at each stage its chamber fills through. No animation pack moves a
 * held item in first person, so this is the same in every setup.
 */
public final class FirstPersonDraw {
    /** Where the bong's chamber steps up a stage (its draw models, {@code bong_template_draw_1..3}). */
    private static final float[] BONG_STAGES = {0.25F, 0.5F, 0.75F};
    private static final float BONG_BOB = 0.03F;
    private static final float BONG_BOB_WIDTH = 0.06F;
    private static final float RISE_TICKS = 4.0F;

    // Where the item is held, in the view's space for the right hand (x right, y up, the eye looking down
    // −z), then its turn in degrees about y, x and z, and its size over its size in the hand. A flat item in
    // the hand stands on edge with its far end ahead, thin, as a spliff is seen from the eyes; turned −90°
    // about z it lies face up. The pipe is turned 15° off its edge: upright, a little of its side showing.
    //                                        x        y        z      rx   ry    rz     scale
    private static final float[] SPLIFF =    {0.005F, -0.325F, -0.42F, 10F, 10F,  0F,   0.5F};
    private static final float[] PIPE =      {-0.018F, -0.205F, -0.42F, 10F, 10F,  -15F,  0.5F};
    // Turned round, so the mouthpiece (the upper-left end of its art) is the near end.
    private static final float[] VAPORIZER = {0.16F,   -0.15F,  -0.42F, -10F, 190F, -90F,  0.5F};
    // The bong in the middle, leaning away so the smoke shows down its neck and the bowl at the lower right.
    private static final float[] BONG =      {0.04F,   -0.29F,  -0.55F, 52F,  -10F, 0F,    0.85F};

    private FirstPersonDraw() {
    }

    /**
     * Moves the item for a draw with {@code remaining} ticks (partial) of {@code duration} left. Vanilla's
     * own arm placement is applied after this, and undone here first, so the pose above is where the
     * item ends up.
     */
    public static void apply(PoseStack poseStack, HumanoidArm arm, BodyPoses.Hold hold, float remaining, int duration,
                             float inverseArmHeight) {
        if (duration <= 0) {
            return;
        }
        float drawn = 1.0F - Mth.clamp(remaining / duration, 0.0F, 1.0F);
        float up = smooth(Mth.clamp((duration - remaining) / RISE_TICKS, 0.0F, 1.0F));
        float s = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float[] p = switch (hold) {
            case SPLIFF -> SPLIFF;
            case PIPE -> PIPE;
            case VAPORIZER -> VAPORIZER;
            case BONG -> BONG;
        };
        float bob = 0.0F;
        if (hold == BodyPoses.Hold.BONG) {
            for (float stage : BONG_STAGES) {
                bob += BONG_BOB * Math.max(0.0F, 1.0F - Math.abs(drawn - stage) / BONG_BOB_WIDTH);
            }
        }
        // Vanilla's applyItemArmTransform, which follows.
        float ax = s * 0.56F;
        float ay = -0.52F + inverseArmHeight * -0.6F;
        float az = -0.72F;
        poseStack.translate(Mth.lerp(up, ax, s * p[0]), Mth.lerp(up, ay, p[1] + bob), Mth.lerp(up, az, p[2]));
        poseStack.rotateDegrees(Axis.YP, s * up * p[4]);
        poseStack.rotateDegrees(Axis.XP, up * p[3]);
        poseStack.rotateDegrees(Axis.ZP, s * up * p[5]);
        float scale = Mth.lerp(up, 1.0F, p[6]);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-ax, -ay, -az);
    }

    private static float smooth(float u) {
        return u * u * (3.0F - 2.0F * u);
    }
}
