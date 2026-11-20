package com.warlonmhite.hempdustry.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A smokeable drawn in third person sits at the mouth: its mouth end on the skin's mouth row, pointing
 * out from the face, carried by the head (so it follows every look), and the hand that holds it is
 * aimed at the grip on the item. Measured in the model's pixels: the head box runs from y −8 (crown)
 * to 0 (chin) and its face is the plane z = −4; a skin's mouth is its seventh row, y −2 to −1.
 * The right side of the model is −x; every point and direction below is the right hand's, mirrored
 * for the left.
 */
public final class MouthHold {
    private MouthHold() {
    }

    /**
     * How one smokeable is held.
     *
     * @param itemMouth  the item's mouth end, in its model's pixels (a flat item: texture u, 16 − v, the middle 8)
     * @param itemAxis   the item's direction from the mouth end out, in the same pixels
     * @param itemUp     a direction in the item that should face up (the bowl's opening, a bevel, the bong's bowl side)
     * @param display    the scale of the item's {@code thirdperson_righthand} display (its translation is
     *                   vanilla's 0, 3, 1 px for both kinds the mod has)
     * @param mouth      where the mouth end sits, in the head's pixels
     * @param out        the direction the item leaves the mouth, in the head's pixels
     * @param upTarget   where {@code itemUp} turns to, in the head's pixels
     * @param scale      the item's size at the mouth, over its size in the hand
     * @param hand       where the holding hand aims: so far along {@code out}, so far down, so far out to its side (pixels)
     */
    record Hold(Vector3f itemMouth, Vector3f itemAxis, Vector3f itemUp, float display,
                Vector3f mouth, Vector3f out, Vector3f upTarget, float scale, Vector3f hand) {
    }

    private static final Vector3f UP = new Vector3f(0.0F, -1.0F, 0.0F);

    // The spliff's mouth end is the lower right of its art; it leaves the corner of the mouth on the
    // hand's side, out and a little down, the fist under its lit end.
    private static final Hold SPLIFF = new Hold(v(13, 3, 8), v(-1, 1, 0), v(1, 1, 0), 0.55F,
            v(-1.0F, -1.5F, -4.1F), v(-0.35F, 0.4F, -1.0F), UP, 0.7F, v(3.0F, 3.0F, 2.0F));
    // The pipe the same way, its stem a little straighter and its bowl's opening up; the fist at the bowl.
    private static final Hold PIPE = new Hold(v(13, 2, 8), v(-1, 1, 0), v(1, 1, 0), 0.55F,
            v(-0.8F, -1.5F, -4.1F), v(-0.15F, 0.35F, -1.0F), UP, 0.8F, v(4.5F, 2.5F, 2.0F));
    // The vaporizer's mouthpiece is its upper-left end, the bevel along its upper edge facing up.
    private static final Hold VAPORIZER = new Hold(v(4, 13, 8), v(1, -1, 0), v(1, 1, 0), 0.55F,
            v(-0.5F, -1.5F, -4.1F), v(-0.25F, 0.25F, -1.0F), UP, 0.7F, v(3.0F, 3.0F, 2.0F));
    // The bong (its block model): the lip at the mouth, the body down and out from the chest, the bowl
    // on the drawing hand's side, a hand at each side of the chamber.
    private static final Hold BONG = new Hold(v(8, 15, 8), v(0, -1, 0), v(1, 0, 0), 0.5F,
            v(0.0F, -1.5F, -4.3F), v(0.0F, 1.0F, -0.5F), v(-1, 0, 0), 1.0F, v(6.0F, 0.0F, 3.5F));
    /** The other hand on a bong, at the other side of the chamber. */
    private static final Vector3f BONG_OTHER_HAND = v(4.5F, 0.0F, 3.5F);
    /** An empty other hand cupping a pipe's bowl, from below. */
    private static final Vector3f PIPE_CUP = v(5.0F, 2.5F, -1.0F);

    private static Vector3f v(float x, float y, float z) {
        return new Vector3f(x, y, z);
    }

    private static Hold of(BodyPoses.Hold hold) {
        return switch (hold) {
            case SPLIFF -> SPLIFF;
            case PIPE -> PIPE;
            case VAPORIZER -> VAPORIZER;
            case BONG -> BONG;
        };
    }

    /** Puts {@code poseStack}, in the model's space, where a drawn item in {@code arm} is drawn from. */
    public static void placeItem(PoseStack poseStack, HumanoidModel<?> model, HumanoidArm arm, BodyPoses.Hold kind) {
        Hold hold = of(kind);
        float sx = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        model.root().translateAndRotate(poseStack);
        model.head.translateAndRotate(poseStack);
        Quaternionf turn = turn(hold, sx);
        // The item's mouth end once vanilla's display has placed it, in blocks.
        Vector3f mouthEnd = new Vector3f(hold.itemMouth).div(16.0F).sub(0.5F, 0.5F, 0.5F).mul(hold.display)
                .add(0.0F, 3.0F / 16.0F, 1.0F / 16.0F).mul(hold.scale);
        Vector3f at = mirror(hold.mouth, sx).div(16.0F).sub(turn.transform(mouthEnd));
        poseStack.translate(at.x, at.y, at.z);
        poseStack.mulPose(turn);
        poseStack.scale(hold.scale, hold.scale, hold.scale);
    }

    /** The turn taking the item's own directions to the head's. */
    private static Quaternionf turn(Hold hold, float sx) {
        Matrix3f from = basis(new Vector3f(hold.itemAxis), new Vector3f(hold.itemUp));
        Matrix3f to = basis(mirror(hold.out, sx), mirror(hold.upTarget, sx));
        return to.mul(from.transpose()).getNormalizedRotation(new Quaternionf());
    }

    /** An orthonormal basis: {@code axis}, {@code up} made square to it, and their cross. */
    private static Matrix3f basis(Vector3f axis, Vector3f up) {
        axis.normalize();
        up.sub(new Vector3f(axis).mul(up.dot(axis))).normalize();
        Vector3f third = new Vector3f(axis).cross(up);
        return new Matrix3f(axis, up, third);
    }

    private static Vector3f mirror(Vector3f p, float sx) {
        return new Vector3f(p.x * sx, p.y, p.z);
    }

    /**
     * Aims {@code arm} on {@code side} at where it holds the drawn item: the drawing hand at its grip,
     * or (when {@code other}) the other hand at its own place on the device.
     */
    static void aim(HumanoidModel<?> model, ModelPart arm, HumanoidArm side, HumanoidArm drawing, BodyPoses.Hold kind,
                    boolean other) {
        Hold hold = of(kind);
        float sx = drawing == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        Vector3f grip = other
                ? (kind == BodyPoses.Hold.BONG ? BONG_OTHER_HAND : PIPE_CUP)
                : hold.hand;
        float outward = other ? -grip.z : grip.z;
        Vector3f out = mirror(hold.out, sx).normalize();
        Vector3f target = mirror(hold.mouth, sx)
                .add(new Vector3f(out).mul(grip.x))
                .add(0.0F, grip.y, 0.0F)
                .add(-sx * outward, 0.0F, 0.0F);
        // Into the model's space through the head's pivot and turn.
        new Quaternionf().rotationZYX(model.head.zRot, model.head.yRot, model.head.xRot).transform(target);
        target.add(model.head.x, model.head.y, model.head.z);
        Vector3f dir = target.sub(arm.x, arm.y, arm.z).normalize();
        // A hanging arm points down (+y); it turns about x first, then y (ModelPart's ZYX order).
        float sinX = -Mth.sqrt(Math.max(0.0F, 1.0F - dir.y * dir.y));
        arm.xRot = (float) Math.atan2(sinX, dir.y);
        arm.yRot = Math.abs(sinX) < 1.0E-4F ? 0.0F : (float) Math.atan2(dir.x / sinX, dir.z / sinX);
        arm.zRot = 0.0F;
    }
}
