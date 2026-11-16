package com.warlonmhite.hempdustry.effect;

import com.warlonmhite.hempdustry.util.ModTags;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Hush: while it lasts, your own body makes no vibrations. Steps, swimming, landing, eating, drinking,
 * putting armour on, gliding, using an item ({@code #hempdustry:hush_silences}) reach no sculk sensor,
 * shrieker or Warden; whatever you do to the world — a block broken or placed, a chest opened, a
 * projectile landing, a hit taken — is still heard, and stepping onto a sensor still trips it, as it
 * does for a sneaking player. It does nothing on its own: {@code VibrationListenerMixin} reads it,
 * just after vanilla's own test for a sneaking player.
 *
 * <p>The smoke itself is the one thing about you a Warden can still sense: it smells a Hushed player
 * from twice as far ({@link #SNIFF_DISTANCE}, {@code SniffingMixin}).
 *
 * <p>Echo OG's signature, and the plant's half of a vanilla pair: sneaking and Swift Sneak quiet your
 * steps; this quiets the rest of your body too, for a hit's length, and the two stack.
 */
public class HushEffect extends MobEffect {
    /** How far, flat, a Warden smells a Hushed player: twice vanilla's 6 ({@code Sniffing#stop}). */
    public static final double SNIFF_DISTANCE = 12.0;

    public HushEffect(int color) {
        super(MobEffectCategory.BENEFICIAL, color);
    }

    /** Whether {@code event} from {@code source} is one Hush silences: a body of its own under Hush. */
    public static boolean silences(Entity source, Holder<GameEvent> event) {
        return source instanceof LivingEntity living && living.hasEffect(ModEffects.HUSH)
                && event.is(ModTags.GameEvents.HUSH_SILENCES);
    }
}
