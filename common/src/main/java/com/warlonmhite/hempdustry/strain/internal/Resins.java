package com.warlonmhite.hempdustry.strain.internal;

import com.warlonmhite.hempdustry.balance.Genetics;
import com.warlonmhite.hempdustry.component.ModComponents;
import com.warlonmhite.hempdustry.strain.ResinStep;
import com.warlonmhite.hempdustry.strain.ResinTexture;
import com.warlonmhite.hempdustry.util.ModTags;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * What a {@link ResinStep} pays at the sieve and the rub, and where a {@link ResinTexture} sends a rich plant's
 * bonus.
 *
 * <table>
 *   <tr><th>step</th><th>sieve, full bonus</th><th>rub, full bonus</th></tr>
 *   <tr><td>very low</td><td>×0.5</td><td>1 in 8</td></tr>
 *   <tr><td>low</td><td>×0.75</td><td>1 in 6</td></tr>
 *   <tr><td>average</td><td>×1</td><td>1 in 4</td></tr>
 *   <tr><td>rich</td><td>×1.5</td><td>1 in 3</td></tr>
 *   <tr><td>very rich</td><td>×2, the ceiling</td><td>1 in 2</td></tr>
 * </table>
 *
 * <table>
 *   <tr><th>texture</th><th>sieve</th><th>rub</th></tr>
 *   <tr><td>dry</td><td>full bonus</td><td>no bonus</td></tr>
 *   <tr><td>even</td><td>half</td><td>half</td></tr>
 *   <tr><td>sticky</td><td>half</td><td>full bonus</td></tr>
 * </table>
 *
 * <p>A low step costs in full on both routes. A lamp record or a stressed plant still halves the rub.
 *
 * <p>Those are the built-in numbers. Every step but average, and every share, is read from the world's
 * {@code genetics/default.json} ({@link Genetics.Resin}); average stays ×1 and 1 in 4, the rates a bud or a plant
 * with no step has always had.
 */
public final class Resins {
    private Resins() {
    }

    private static float sieve(Genetics genetics, ResinStep step) {
        Genetics.Steps sieve = genetics.resin().sieve();
        return switch (step) {
            case VERY_LOW -> sieve.veryLow();
            case LOW -> sieve.low();
            case AVERAGE -> 1.0F;
            case RICH -> sieve.rich();
            case VERY_RICH -> sieve.veryRich();
        };
    }

    private static float rub(Genetics genetics, ResinStep step) {
        Genetics.RubOdds rub = genetics.resin().rubOneIn();
        return 1.0F / switch (step) {
            case VERY_LOW -> rub.veryLowOneIn();
            case LOW -> rub.lowOneIn();
            case AVERAGE -> Genetics.RubOdds.AVERAGE_ONE_IN;
            case RICH -> rub.richOneIn();
            case VERY_RICH -> rub.veryRichOneIn();
        };
    }

    private static float share(Genetics.Shares shares, ResinTexture texture) {
        return switch (texture) {
            case DRY -> shares.dry();
            case EVEN -> shares.even();
            case STICKY -> shares.sticky();
        };
    }

    /** How much of a rich plant's bonus the sieve pays for {@code texture}: 0 to 1. */
    public static float sieveShare(Genetics genetics, ResinTexture texture) {
        return share(genetics.resin().sieveShare(), texture);
    }

    /** How much of a rich plant's bonus the rub pays for {@code texture}: 0 to 1. */
    public static float rubShare(Genetics genetics, ResinTexture texture) {
        return share(genetics.resin().rubShare(), texture);
    }

    /** The step {@code offset} places around average, held to the five. */
    public static ResinStep ofOffset(int offset) {
        ResinStep[] steps = ResinStep.values();
        return steps[Math.max(0, Math.min(steps.length - 1, offset + 2))];
    }

    /** What a bud of {@code step} multiplies its rate at the Sifting Box by, for a strain of {@code texture}. */
    public static float sieveFactor(Genetics genetics, ResinStep step, ResinTexture texture) {
        float sieve = sieve(genetics, step);
        return step.offset() <= 0 ? sieve : 1.0F + (sieve - 1.0F) * sieveShare(genetics, texture);
    }

    /** The chance that rubbing a plant of {@code step} pays a charas, for a strain of {@code texture}, before a lamp halves it. */
    public static float rubChance(Genetics genetics, ResinStep step, ResinTexture texture) {
        float rub = rub(genetics, step);
        float average = rub(genetics, ResinStep.AVERAGE);
        return step.offset() <= 0 ? rub : average + (rub - average) * rubShare(genetics, texture);
    }

    /**
     * The lowest and the highest {@link #sieveFactor} of any step and texture: what a recipe viewer's sieve page says
     * a bud's resin moves its rate between (×0.5 to ×2 built in).
     */
    public static float[] sieveRange(Genetics genetics) {
        float low = Float.MAX_VALUE;
        float high = 0.0F;
        for (ResinStep step : ResinStep.values()) {
            for (ResinTexture texture : ResinTexture.values()) {
                float factor = sieveFactor(genetics, step, texture);
                low = Math.min(low, factor);
                high = Math.max(high, factor);
            }
        }
        return new float[]{low, high};
    }

    /** The lowest and the highest {@link #rubChance} of any step and texture (1 in 8 to 1 in 2 built in). */
    public static float[] rubRange(Genetics genetics) {
        float low = Float.MAX_VALUE;
        float high = 0.0F;
        for (ResinStep step : ResinStep.values()) {
            for (ResinTexture texture : ResinTexture.values()) {
                float chance = rubChance(genetics, step, texture);
                low = Math.min(low, chance);
                high = Math.max(high, chance);
            }
        }
        return new float[]{low, high};
    }

    /** The step an item carries, if one is written on it. */
    public static Optional<ResinStep> written(ItemStack stack) {
        return Optional.ofNullable(stack.get(ModComponents.RESIN_STEP));
    }

    /**
     * The step a bud with none written on it sieves as: very rich for a bud in {@code #hempdustry:siftable/resinous}
     * (two levels a bud), average for every other (one).
     */
    public static ResinStep legacy(ItemStack stack) {
        return stack.is(ModTags.Items.SIFTABLE_RESINOUS) ? ResinStep.VERY_RICH : ResinStep.AVERAGE;
    }

    /** Writes {@code step} on {@code stack}; the step it reads with none ({@link #legacy}) writes nothing. Returns the stack. */
    public static ItemStack stamp(ItemStack stack, ResinStep step) {
        if (step == legacy(stack)) {
            stack.remove(ModComponents.RESIN_STEP);
        } else {
            stack.set(ModComponents.RESIN_STEP, step);
        }
        return stack;
    }

    /**
     * {@link #stamp(ItemStack, ResinStep)} for a bud of a strain of {@code texture}: its {@link #legacy} step is
     * written too when a bud with nothing written would sieve otherwise. Only a strain a datapack adds to
     * {@code #hempdustry:siftable/resinous} that is not dry meets it: its very rich buds sieve at its texture's
     * rate, and left unwritten they would take the tag's double one. So does any very rich bud of a world whose
     * {@code genetics} sieve it at another rate than the tag's, which stays the built-in very rich rate. Returns the
     * stack.
     */
    public static ItemStack stamp(Genetics genetics, ItemStack stack, ResinStep step, ResinTexture texture) {
        ResinStep legacy = legacy(stack);
        if (step == legacy && sieveFactor(genetics, step, texture) == sieve(Genetics.BUILT_IN, legacy)) {
            stack.remove(ModComponents.RESIN_STEP);
        } else {
            stack.set(ModComponents.RESIN_STEP, step);
        }
        return stack;
    }

    /** The tooltip line, grey; average has none. */
    public static Optional<Component> tooltipLine(ResinStep step) {
        return step == ResinStep.AVERAGE
                ? Optional.empty()
                : Optional.of(Component.translatable(step.getTranslationKey()).withStyle(ChatFormatting.GRAY));
    }
}
