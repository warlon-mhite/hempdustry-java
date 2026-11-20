package com.warlonmhite.hempdustry.client.anim;

import com.warlonmhite.hempdustry.client.anim.compat.ModelFeatures;
import com.warlonmhite.hempdustry.item.custom.Draw;
import com.warlonmhite.hempdustry.item.custom.SmokingDeviceItem;
import com.warlonmhite.hempdustry.network.PlayerGesture;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The third-person poses: a smokeable held to the mouth for the length of a draw (in place of the
 * goat-horn pose the bong borrowed until 2.2), and the gestures — a cough, a sniff at a jar, a kick
 * at a footbag. Applied to a player's model after vanilla has posed the arms for the held items and
 * before the swing, the crouch and the idle sway, so those still play on top as they do on any
 * vanilla pose. Only players (and mannequins) are posed: a gesture is found by the entity's id.
 *
 * <p>A drawn item sits at the mouth and is carried by the head ({@link MouthHold}), so a draw follows
 * the look as every vanilla use pose does; each hand is aimed at where it holds the item.
 */
public final class BodyPoses {
    private static final float COUGH_NOD = 0.32F;
    private static final float SNIFF_NOD = 0.45F;
    private static final float KICK_LIFT = 1.15F;
    /** The hand brought to the mouth to cough into. */
    private static final float HAND_TO_MOUTH_LIFT = 2.1F;
    private static final float HAND_TO_MOUTH_IN = 0.55F;
    /** The shoulders hunch with a cough: the arms' pivots rise this far (model pixels). */
    private static final float HUNCH = 0.8F;
    /** The chin lifts at the end of a pull, and the head dips to a bong's mouthpiece as the draw starts (radians). */
    private static final float CHIN_LIFT = 0.12F;
    private static final float BONG_DIP = 0.18F;
    /** Ticks a fresh-style pose takes to let go after a draw. */
    private static final int LET_GO_TICKS = 3;

    private static final Int2ObjectOpenHashMap<LastDraw> LAST_DRAW = new Int2ObjectOpenHashMap<>();

    private BodyPoses() {
    }

    /** The kind of hold a smokeable takes, by how it is smoked. */
    public enum Hold {
        /** One hand, the lit end out from the mouth. */
        SPLIFF,
        /** One hand, the bowl up and out from the mouth; an empty other hand cups the bowl where the setup is rich. */
        PIPE,
        /** One hand, the mouthpiece end at the lips. */
        VAPORIZER,
        /** Both hands, the off-hand item put away. */
        BONG;

        public static @Nullable Hold of(ItemStack stack) {
            if (Draw.length(stack) <= 0) {
                return null;
            }
            if (stack.getItem() instanceof SmokingDeviceItem device) {
                return switch (device.device()) {
                    case BONG -> BONG;
                    case VAPORIZER -> VAPORIZER;
                    case PIPE -> PIPE;
                };
            }
            return SPLIFF;
        }
    }

    /** The draw a render state shows, if it shows one: what is held, in which arm, and how far in. */
    public record Drawing(Hold hold, HumanoidArm arm, float progress) {
        public static @Nullable Drawing of(HumanoidRenderState state) {
            if (!state.isUsingItem) {
                return null;
            }
            HumanoidArm arm = state.useItemHand.asArm(state.mainArm);
            ItemStack stack = state.getUseItemStackForArm(arm);
            Hold hold = Hold.of(stack);
            if (hold == null) {
                return null;
            }
            return new Drawing(hold, arm, Mth.clamp(state.ticksUsingItem / Draw.length(stack), 0.0F, 1.0F));
        }
    }

    /**
     * Poses {@code model} for what {@code state} shows, in this client's style, and claims the posed
     * parts from a pack that animates the model after it is posed.
     */
    public static void apply(HumanoidModel<?> model, HumanoidRenderState state) {
        boolean pack = Style.modelFeatures() && ModelFeatures.animates(model);
        Style style = Style.now(pack);
        if (style == null) {
            return;
        }
        Claims.Claim claim = pose(model, state, style);
        if (pack) {
            Claims.put(model.root(), claim);
        }
    }

    /**
     * Poses {@code model} for what {@code state} shows, in {@code style}.
     *
     * @return the parts posed here, which a pack that animates the model must leave as they are, and
     *         how much of the pose is ours where the pack's and ours are blended
     */
    public static Claims.Claim pose(HumanoidModel<?> model, HumanoidRenderState state, Style style) {
        List<ModelPart> posed = new ArrayList<>(4);
        float weight = 1.0F;
        if (!(state instanceof AvatarRenderState avatar)) {
            return new Claims.Claim(posed, weight);
        }
        float age = state.ageInTicks;
        Drawing drawing = Drawing.of(state);
        if (drawing != null) {
            LAST_DRAW.put(avatar.id, new LastDraw(drawing, age));
        } else if (style == Style.FRESH) {
            // Fresh Animations eases its own moves out; a pose of ours that snapped off would be the
            // one hard edge on the body, so it lets go over a few ticks instead.
            LastDraw last = LAST_DRAW.get(avatar.id);
            if (last != null && age - last.age < LET_GO_TICKS && age >= last.age) {
                drawing = last.drawing;
                weight = 1.0F - (age - last.age) / LET_GO_TICKS;
            }
        }
        if (drawing != null) {
            draw(model, state, style, drawing, posed);
        }

        float cough = Gestures.progress(avatar.id, PlayerGesture.Kind.COUGH, age);
        if (cough >= 0.0F) {
            cough(model, state, style, cough, drawing, posed);
        }
        float sniff = Gestures.progress(avatar.id, PlayerGesture.Kind.SNIFF, age);
        if (sniff >= 0.0F) {
            model.head.xRot += SNIFF_NOD * curve(style, sniff);
            posed.add(model.head);
        }
        float kick = kicking(state);
        if (kick >= 0.0F) {
            // The leg is the pack's in the Fresh style too: a kick is the one move whose whole point is the foot.
            posed.add(kick(model, state, style, kick));
        }
        if (style == Style.FRESH && drawing != null && weight >= 1.0F) {
            // ...and takes the pose up over as many ticks at the start.
            weight = smooth(Mth.clamp(drawing.progress() * Draw.length(state.getUseItemStackForArm(drawing.arm())) / LET_GO_TICKS, 0.0F, 1.0F));
        }
        return new Claims.Claim(posed, weight);
    }

    private static ModelPart kick(HumanoidModel<?> model, HumanoidRenderState state, Style style, float kick) {
        ModelPart leg = state.mainArm == HumanoidArm.RIGHT ? model.rightLeg : model.leftLeg;
        leg.xRot = -KICK_LIFT * curve(style, kick);
        leg.yRot = 0.0F;
        leg.zRot = 0.0F;
        return leg;
    }

    /**
     * The kick again, once Not Enough Animations has smoothed the legs: a 6-tick move smoothed would
     * barely leave the ground. Its own eased curve is the easing.
     */
    public static void kickAgain(HumanoidModel<?> model, HumanoidRenderState state) {
        float kick = kicking(state);
        Style style = kick >= 0.0F ? Style.now(false) : null;
        if (style == Style.EASED) {
            kick(model, state, style, kick);
        }
    }

    /** How far through a kick the body in {@code state} is, or a negative number when it is not kicking. */
    public static float kicking(HumanoidRenderState state) {
        return state instanceof AvatarRenderState avatar
                ? Gestures.progress(avatar.id, PlayerGesture.Kind.KICK, state.ageInTicks) : -1.0F;
    }

    private static void draw(HumanoidModel<?> model, HumanoidRenderState state, Style style, Drawing drawing,
                             List<ModelPart> posed) {
        HumanoidArm side = drawing.arm();
        if (drawing.hold() == Hold.BONG) {
            // The head goes down to the bong's mouthpiece as the draw starts.
            model.head.xRot += BONG_DIP * smooth(Mth.clamp(drawing.progress() * 6.0F, 0.0F, 1.0F));
            posed.add(model.head);
        }
        if (style == Style.FRESH && drawing.hold() != Hold.BONG) {
            // Fresh's touch on top: the chin lifts as a pull ends.
            model.head.xRot -= CHIN_LIFT * smooth(Mth.clamp((drawing.progress() - 0.7F) / 0.3F, 0.0F, 1.0F));
            posed.add(model.head);
        }
        // The item sits at the mouth, carried by the head (MouthHold); each hand goes where it holds it.
        aim(model, side, drawing, false, posed);
        if (drawing.hold() == Hold.BONG
                || drawing.hold() == Hold.PIPE && style.rich() && emptyHand(state, side.getOpposite())) {
            // Both hands to the bong; an empty off hand cups the pipe's bowl, where a richer setup shows hands at work.
            aim(model, side.getOpposite(), drawing, true, posed);
        }
    }

    private static void aim(HumanoidModel<?> model, HumanoidArm side, Drawing drawing, boolean other, List<ModelPart> posed) {
        ModelPart arm = model.getArm(side);
        MouthHold.aim(model, arm, side, drawing.arm(), drawing.hold(), other);
        posed.add(arm);
    }

    private static void cough(HumanoidModel<?> model, HumanoidRenderState state, Style style, float progress,
                              @Nullable Drawing drawing, List<ModelPart> posed) {
        // Two jolts, 3 ticks each, the second after a beat.
        float t = progress * Gestures.COUGH_TICKS;
        float jolt = t < 3.0F ? t / 3.0F : t >= 5.0F && t < 8.0F ? (t - 5.0F) / 3.0F : -1.0F;
        if (jolt >= 0.0F) {
            model.head.xRot += COUGH_NOD * curve(style, jolt);
        }
        posed.add(model.head);
        if (!style.rich()) {
            return;
        }
        // The free hand comes up to the mouth for the whole cough: the off hand if it is empty, else
        // the main hand if that one is.
        HumanoidArm free = emptyHand(state, state.mainArm.getOpposite()) ? state.mainArm.getOpposite()
                : emptyHand(state, state.mainArm) ? state.mainArm : null;
        if (free != null && (drawing == null || drawing.arm() != free)) {
            float in = smooth(Mth.clamp(Math.min(t, Gestures.COUGH_TICKS - t) / 2.0F, 0.0F, 1.0F));
            ModelPart arm = model.getArm(free);
            float headX = Mth.clamp(model.head.xRot, -1.2F, 1.2F);
            float side = free == HumanoidArm.RIGHT ? 1.0F : -1.0F;
            arm.xRot = Mth.lerp(in, arm.xRot, headX - HAND_TO_MOUTH_LIFT);
            arm.yRot = Mth.lerp(in, arm.yRot, model.head.yRot - side * HAND_TO_MOUTH_IN);
            arm.zRot = Mth.lerp(in, arm.zRot, 0.0F);
            posed.add(arm);
        }
        if (style == Style.FRESH) {
            float hunch = HUNCH * (float) Math.sin(Math.PI * progress);
            model.rightArm.y -= hunch;
            model.leftArm.y -= hunch;
        }
    }


    private static boolean emptyHand(HumanoidRenderState state, HumanoidArm arm) {
        return (arm == HumanoidArm.RIGHT ? state.rightHandItemStack : state.leftHandItemStack).isEmpty();
    }

    /** Vanilla's swing curve for a short move; eased at both ends where the body around it eases. */
    private static float curve(Style style, float u) {
        float clamped = Mth.clamp(u, 0.0F, 1.0F);
        float sin = Mth.sin(clamped * Mth.PI);
        return style == Style.VANILLA ? sin : sin * sin;
    }

    private static float smooth(float u) {
        return u * u * (3.0F - 2.0F * u);
    }

    /** Forgets the bodies this client has seen: a new world has new entities under the same ids. */
    public static void clear() {
        LAST_DRAW.clear();
    }

    private record LastDraw(Drawing drawing, float age) {
    }
}
