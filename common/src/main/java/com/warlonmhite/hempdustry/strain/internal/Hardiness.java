package com.warlonmhite.hempdustry.strain.internal;

import com.warlonmhite.hempdustry.strain.Resistance;
import net.minecraft.util.RandomSource;

/**
 * What a {@link Resistance} step does to a plant's stress.
 *
 * <ul>
 *   <li><b>A step multiplies a cause's chance</b> ({@link #factor}): very weak ×2, weak ×1.5, neutral ×1,
 *       resistant ×½, very resistant ×¼. A dry bed's 1 in 3 becomes 2 in 3, 1 in 2, 1 in 3, 1 in 6, 1 in 12.</li>
 *   <li><b>A lost light</b> is certain, so for light the step reads as how often the plant ignores the loss
 *       ({@link #ignoresLostLight}: resistant 1 time in 2, very resistant 3 in 4) and from which age a loss
 *       stresses it ({@link #lightLossAge}: weak and very weak from age 3, everyone else from age 4).</li>
 * </ul>
 */
public final class Hardiness {
    /** The age from which a lost light stresses a weak or very weak plant, a stage before flowering. */
    public static final int WEAK_LIGHT_LOSS_AGE = 3;
    /** The age from which a lost light stresses every other plant: flowering ({@code GrowLight#FLOWERING_AGE}). */
    public static final int LIGHT_LOSS_AGE = 4;

    private Hardiness() {
    }

    /** What {@code step} multiplies a cause's chance by. */
    public static float factor(Resistance step) {
        return switch (step) {
            case VERY_WEAK -> 2.0F;
            case WEAK -> 1.5F;
            case NEUTRAL -> 1.0F;
            case RESISTANT -> 0.5F;
            case VERY_RESISTANT -> 0.25F;
        };
    }

    /** {@code chance} for a plant of {@code step}: multiplied by {@link #factor}, never above certain. */
    public static float apply(Resistance step, float chance) {
        return Math.min(1.0F, chance * factor(step));
    }

    /** The age from which losing its light stresses a plant of {@code step}. */
    public static int lightLossAge(Resistance step) {
        return step.score() < 0 ? WEAK_LIGHT_LOSS_AGE : LIGHT_LOSS_AGE;
    }

    /**
     * Whether a plant of {@code step} shrugs off a lost light this time. Draws from {@code random} only for a
     * resistant or very resistant plant, so every other plant's step takes the draws it always did.
     */
    public static boolean ignoresLostLight(Resistance step, RandomSource random) {
        float ignores = switch (step) {
            case VERY_WEAK, WEAK, NEUTRAL -> 0.0F;
            case RESISTANT -> 0.5F;
            case VERY_RESISTANT -> 0.75F;
        };
        return ignores > 0.0F && random.nextFloat() < ignores;
    }
}
