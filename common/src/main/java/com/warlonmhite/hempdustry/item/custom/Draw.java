package com.warlonmhite.hempdustry.item.custom;

import java.util.function.BiConsumer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The draw: the use key held on a packed smokeable until the hit lands on the last tick. Every
 * smokeable draws — the spliff {@value #SPLIFF_TICKS} ticks, the devices as {@link DeviceType#drawTicks()}
 * says — and letting go early spends nothing. What the draw shares between the spliff and the
 * devices lives here: its sound, started on each client that sees it, and the wisp off a lit bowl.
 *
 * <p>The player's pose while drawing is the client's, read off the use state the game already
 * syncs; nothing here sends it.
 */
public final class Draw {
    /** The spliff's draw: a quick drag, the length of a bite of dried kelp. */
    public static final int SPLIFF_TICKS = 16;

    private static final int WISP_EVERY_TICKS = 5;

    /**
     * Starts a draw's sound on this client — set by {@code HempdustryClient}, a no-op on a dedicated
     * server. Client-side because the sound has to stop the moment the drawer lets go, and only a
     * sound instance that belongs to the client can stop itself (vanilla's elytra wind is the same
     * shape). A sound the server sent would play on for seconds after a draw that never became a hit.
     */
    public static BiConsumer<LivingEntity, SoundEvent> sound = (user, sound) -> {};

    /**
     * Where a lit load sits while it is drawn, in blocks from the drawer's eyes, for the right hand
     * (mirrored for the left): as others see it ({@code forward}, {@code down}, {@code side}, carried by
     * the head, so it follows the look as the held item does) and as the drawer sees it in first person
     * ({@code firstForward}, {@code firstDown}, {@code firstSide}, where the item is drawn in their view).
     */
    public record LitEnd(double forward, double down, double side,
                         double firstForward, double firstDown, double firstSide) {
        /** The spliff's ember: out from the corner of the mouth. */
        public static final LitEnd SPLIFF = new LitEnd(0.56, 0.14, 0.16, 0.5, 0.14, 0.03);
        /** The pipe's bowl, at the end of its stem. */
        public static final LitEnd PIPE = new LitEnd(0.6, 0.09, 0.1, 0.5, 0.025, 0.045);
        /** The bong's bowl, down its body on the drawing hand's side. */
        public static final LitEnd BONG = new LitEnd(0.45, 0.33, 0.16, 0.5, 0.31, 0.13);

        /** The point in the world, for {@code user} drawing now, seen in first person or not. */
        public Vec3 at(LivingEntity user, boolean firstPerson) {
            boolean rightHand = (user.getUsedItemHand() == InteractionHand.MAIN_HAND) == (user.getMainArm() == HumanoidArm.RIGHT);
            float pitch = user.getXRot();
            float yaw = user.getYRot();
            Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
            Vec3 up = Vec3.directionFromRotation(pitch - 90.0F, yaw);
            Vec3 right = Vec3.directionFromRotation(0.0F, yaw + 90.0F);
            double s = rightHand ? 1.0 : -1.0;
            return firstPerson
                    ? user.getEyePosition().add(forward.scale(firstForward)).add(up.scale(-firstDown)).add(right.scale(s * firstSide))
                    : user.getEyePosition().add(forward.scale(this.forward)).add(up.scale(-down)).add(right.scale(s * side));
        }
    }

    /**
     * Lets off the drawer's own wisp on this client — set by {@code HempdustryClient}, a no-op on a
     * dedicated server. The drawer's is theirs alone: where it sits depends on their camera.
     */
    public static BiConsumer<LivingEntity, LitEnd> ownWisp = (user, litEnd) -> {};

    private Draw() {
    }

    /** How long a draw of {@code stack} lasts, in ticks, or {@code 0} for anything that is not a smokeable. */
    public static int length(ItemStack stack) {
        if (stack.getItem() instanceof SpliffItem) {
            return SPLIFF_TICKS;
        }
        if (stack.getItem() instanceof SmokingDeviceItem device) {
            return device.device().drawTicks();
        }
        return 0;
    }

    /**
     * One tick of a draw, {@code drawn} ticks in. Runs on the server and on every client that sees
     * the drawer — a remote player's draw ticks there too, from the use flag the server syncs.
     *
     * @param litEnd where the lit load sits, or {@code null} for a load that is not lit: a burning
     *               bowl lets off a wisp, a vaporizer's does not
     */
    public static void tick(Level world, LivingEntity user, int drawn, SoundEvent sound, @Nullable LitEnd litEnd) {
        boolean wisp = litEnd != null && drawn % WISP_EVERY_TICKS == 0;
        if (world.isClientSide()) {
            if (drawn == 0) {
                Draw.sound.accept(user, sound);
            }
            if (wisp) {
                ownWisp.accept(user, litEnd);
            }
        } else if (wisp && world instanceof ServerLevel serverWorld) {
            wisp(serverWorld, user, litEnd);
        }
    }

    /**
     * A wisp off the lit end, where the draw holds it — the draw you can see from across a room. Sent
     * to everyone but the drawer, whose own wisp their client places for their camera
     * ({@link #ownWisp}): in first person the item is drawn in their view, not at their lips.
     */
    private static void wisp(ServerLevel world, LivingEntity user, LitEnd litEnd) {
        Vec3 bowl = litEnd.at(user, false);
        for (ServerPlayer viewer : world.players()) {
            if (viewer != user) {
                world.sendParticles(viewer, ParticleTypes.SMOKE, false, false, bowl.x, bowl.y, bowl.z, 1, 0.02, 0.02, 0.02, 0.005);
            }
        }
    }
}
