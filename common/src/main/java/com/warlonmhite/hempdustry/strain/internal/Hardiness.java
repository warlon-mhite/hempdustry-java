package com.warlonmhite.hempdustry.strain.internal;

import com.warlonmhite.hempdustry.balance.Genetics;
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
 *
 * <p>Those are the built-in numbers. Every step but neutral reads its own from the world's
 * {@code genetics/default.json} ({@link Genetics.Resistance}); neutral, the step of a plant with no record, keeps
 * ×1, age 4 and no shrug, so such a plant stresses as it always did.
 */
public final class Hardiness {
    /** The built-in age from which a lost light stresses a weak or very weak plant, a stage before flowering. */
    public static final int WEAK_LIGHT_LOSS_AGE = 3;
    /** The age from which a lost light stresses a neutral plant: flowering ({@code GrowLight#FLOWERING_AGE}). */
    public static final int LIGHT_LOSS_AGE = Genetics.Resistance.NEUTRAL_LIGHT_LOSS_AGE;

    private Hardiness() {
    }

    /** What {@code step} multiplies a cause's chance by. */
    public static float factor(Genetics genetics, Resistance step) {
        Genetics.Hardy<Float> factors = genetics.resistance().stressFactor();
        return switch (step) {
            case VERY_WEAK -> factors.veryWeak();
            case WEAK -> factors.weak();
            case NEUTRAL -> 1.0F;
            case RESISTANT -> factors.resistant();
            case VERY_RESISTANT -> factors.veryResistant();
        };
    }

    /** {@code chance} for a plant of {@code step}: multiplied by {@link #factor}, never above certain. */
    public static float apply(Genetics genetics, Resistance step, float chance) {
        return Math.min(1.0F, chance * factor(genetics, step));
    }

    /** The age from which losing its light stresses a plant of {@code step}. */
    public static int lightLossAge(Genetics genetics, Resistance step) {
        Genetics.Hardy<Integer> ages = genetics.resistance().lightLossAge();
        return switch (step) {
            case VERY_WEAK -> ages.veryWeak();
            case WEAK -> ages.weak();
            case NEUTRAL -> LIGHT_LOSS_AGE;
            case RESISTANT -> ages.resistant();
            case VERY_RESISTANT -> ages.veryResistant();
        };
    }

    /**
     * Whether a plant of {@code step} shrugs off a lost light this time. Draws from {@code random} only for a
     * resistant or very resistant plant whose odds are above none, so every other plant's step takes the draws it
     * always did.
     */
    public static boolean ignoresLostLight(Genetics genetics, Resistance step, RandomSource random) {
        float ignores = ignoresLostLightChance(genetics, step);
        return ignores > 0.0F && random.nextFloat() < ignores;
    }

    /** How often a plant of {@code step} shrugs off a lost light, 0 to 1: never below resistant. */
    public static float ignoresLostLightChance(Genetics genetics, Resistance step) {
        Genetics.Ignores odds = genetics.resistance().ignoresLostLight();
        return switch (step) {
            case VERY_WEAK, WEAK, NEUTRAL -> 0.0F;
            case RESISTANT -> odds.resistant();
            case VERY_RESISTANT -> odds.veryResistant();
        };
    }
}
