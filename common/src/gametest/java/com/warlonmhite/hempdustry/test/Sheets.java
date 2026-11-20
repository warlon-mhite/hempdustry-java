package com.warlonmhite.hempdustry.test;

import com.warlonmhite.hempdustry.strain.GrowQuality;
import com.warlonmhite.hempdustry.strain.Potency;
import com.warlonmhite.hempdustry.strain.PotencyBand;
import com.warlonmhite.hempdustry.strain.QualityGene;
import com.warlonmhite.hempdustry.strain.ResinSheet;
import com.warlonmhite.hempdustry.strain.ResinStep;
import com.warlonmhite.hempdustry.strain.ResinTexture;
import com.warlonmhite.hempdustry.strain.Resistance;
import com.warlonmhite.hempdustry.strain.ResistanceOdds;
import com.warlonmhite.hempdustry.strain.Resistances;
import com.warlonmhite.hempdustry.strain.StressPhenomenon;
import java.util.Map;

/**
 * Gene sheets written the short way, every entry in key order, for tests that state a sheet in full. A sheet is a
 * map keyed by what each entry is; these spell today's keys out positionally, which only a test may do.
 */
final class Sheets {
    private Sheets() {
    }

    /** A potency sheet: the base, then the weights low, mid, high, extra-high. */
    static Potency potency(int base, int low, int mid, int high, int extraHigh) {
        return new Potency(base, Map.of(PotencyBand.LOW, low, PotencyBand.MID, mid, PotencyBand.HIGH, high,
                PotencyBand.EXTRA_HIGH, extraHigh));
    }

    /** A quality sheet: the base luck, then the weights poor, average, good, elite. */
    static GrowQuality quality(int baseLuck, int poor, int average, int good, int elite) {
        return new GrowQuality(baseLuck, Map.of(QualityGene.POOR, poor, QualityGene.AVERAGE, average,
                QualityGene.GOOD, good, QualityGene.ELITE, elite));
    }

    /** A resin sheet: the weights very low to very rich, then the texture. */
    static ResinSheet resin(int veryLow, int low, int average, int rich, int veryRich, ResinTexture texture) {
        return new ResinSheet(Map.of(ResinStep.VERY_LOW, veryLow, ResinStep.LOW, low, ResinStep.AVERAGE, average,
                ResinStep.RICH, rich, ResinStep.VERY_RICH, veryRich), texture);
    }

    /** One phenomenon's odds: the weights very weak to very resistant. */
    static ResistanceOdds odds(int veryWeak, int weak, int neutral, int resistant, int veryResistant) {
        return new ResistanceOdds(Map.of(Resistance.VERY_WEAK, veryWeak, Resistance.WEAK, weak,
                Resistance.NEUTRAL, neutral, Resistance.RESISTANT, resistant, Resistance.VERY_RESISTANT, veryResistant));
    }

    /** A resistance sheet of odds: dry ground, overwatering, light, mildew. */
    static Resistances resistances(ResistanceOdds dryGround, ResistanceOdds overwatering, ResistanceOdds light,
                                   ResistanceOdds mildew) {
        return new Resistances(Map.of(StressPhenomenon.DRY_GROUND, dryGround, StressPhenomenon.OVERWATERING, overwatering,
                StressPhenomenon.LIGHT, light, StressPhenomenon.MILDEW, mildew));
    }

    /** A resistance sheet of most likely steps, each spread by the shared spread: dry ground, overwatering, light, mildew. */
    static Resistances resistances(Resistance dryGround, Resistance overwatering, Resistance light, Resistance mildew) {
        return resistances(ResistanceOdds.spread(dryGround), ResistanceOdds.spread(overwatering),
                ResistanceOdds.spread(light), ResistanceOdds.spread(mildew));
    }
}
