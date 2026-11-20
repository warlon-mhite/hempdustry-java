package com.warlonmhite.hempdustry.strain.internal;

import com.warlonmhite.hempdustry.strain.GrowQuality;
import com.warlonmhite.hempdustry.strain.Potency;
import com.warlonmhite.hempdustry.strain.PotencyBand;
import com.warlonmhite.hempdustry.strain.QualityGene;
import com.warlonmhite.hempdustry.strain.ResinSheet;
import com.warlonmhite.hempdustry.strain.ResinStep;
import com.warlonmhite.hempdustry.strain.Resistance;
import com.warlonmhite.hempdustry.strain.ResistanceGenes;
import com.warlonmhite.hempdustry.strain.ResistanceOdds;
import com.warlonmhite.hempdustry.strain.Resistances;
import com.warlonmhite.hempdustry.strain.StressPhenomenon;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.util.RandomSource;

/**
 * How a plant rolls its genes at planting from its strain's sheets: one weighted draw per gene, in the sheet's key
 * order. The breeding update replaces these with inheritance; until then a plant is its strain's odds.
 */
public final class Rolls {
    private Rolls() {
    }

    /**
     * One key drawn from {@code weights} by weight, in key order: one draw on {@code random}, or none and
     * {@code fallback} when the weights are all zero.
     */
    static <K extends Enum<K>> K draw(Map<K, Integer> weights, K fallback, RandomSource random) {
        int total = 0;
        for (int weight : weights.values()) {
            total += weight;
        }
        if (total <= 0) {
            return fallback;
        }
        int pick = random.nextInt(total);
        for (Map.Entry<K, Integer> entry : weights.entrySet()) {
            pick -= entry.getValue();
            if (pick < 0) {
                return entry.getKey();
            }
        }
        return fallback;
    }

    /** One plant's band, drawn from the sheet's odds. A sheet whose odds are all zero is always mid. */
    public static PotencyBand roll(Potency potency, RandomSource random) {
        return draw(potency.weights(), PotencyBand.MID, random);
    }

    /** One plant's quality gene, drawn from the sheet's odds. A sheet whose odds are all zero is always average. */
    public static QualityGene roll(GrowQuality quality, RandomSource random) {
        return draw(quality.weights(), QualityGene.AVERAGE, random);
    }

    /** One plant's resin step, drawn from the sheet's odds. Odds that are all zero are always average, and take no draw. */
    public static ResinStep roll(ResinSheet resin, RandomSource random) {
        return draw(resin.weights(), ResinStep.AVERAGE, random);
    }

    /**
     * One plant's step against one phenomenon, drawn from the odds. Odds that are all zero, which no strain file
     * can hold, are always neutral, and take no draw.
     */
    public static Resistance roll(ResistanceOdds odds, RandomSource random) {
        return draw(odds.weights(), Resistance.NEUTRAL, random);
    }

    /** One plant's resistances, each phenomenon drawn on its own from its odds, in phenomenon order. */
    public static ResistanceGenes roll(Resistances resistances, RandomSource random) {
        EnumMap<StressPhenomenon, Resistance> steps = new EnumMap<>(StressPhenomenon.class);
        for (StressPhenomenon phenomenon : StressPhenomenon.values()) {
            steps.put(phenomenon, roll(resistances.odds(phenomenon), random));
        }
        return new ResistanceGenes(steps);
    }

    /** What {@code gene} adds to its plant's grow luck: poor −1, average 0, good +1, elite +2. */
    public static int luck(QualityGene gene) {
        return switch (gene) {
            case POOR -> -1;
            case AVERAGE -> 0;
            case GOOD -> 1;
            case ELITE -> 2;
        };
    }
}
