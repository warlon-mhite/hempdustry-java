package com.warlonmhite.hempdustry.strain;

import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.Map;

/**
 * A strain's resistance sheet: for each {@link StressPhenomenon}, the odds of each {@link Resistance} step a
 * plant of it rolls at planting.
 *
 * <p>Written in a strain's {@code plant} object as {@code "resistances": {"dry_ground": "neutral",
 * "overwatering": "resistant", "light": "neutral", "mildew": "weak"}}, each phenomenon as its most likely step
 * or as a weight per step ({@link ResistanceOdds}). <b>A phenomenon the file does not name takes the shared spread
 * around neutral</b>, as {@code "light": "neutral"} does, and a strain with no {@code resistances} takes it on every
 * phenomenon: a datapack strain written for 2.1 varies plant to plant like every strain. A strain that wants every
 * plant neutral, as before resistances, writes {@code "light": {"neutral": 100}}. A sheet is written back with every
 * phenomenon named.
 *
 * <p>One entry per phenomenon, so a phenomenon added later is one more key, read as the spread around neutral by
 * every sheet written before it.
 *
 * @param odds the odds for each phenomenon; a phenomenon the map leaves out takes the spread around neutral
 */
public record Resistances(Map<StressPhenomenon, ResistanceOdds> odds) {

    /** The odds of a phenomenon a file leaves out: the shared spread around neutral. */
    private static final ResistanceOdds UNNAMED = ResistanceOdds.spread(Resistance.NEUTRAL);

    /** The spread around neutral on every phenomenon: a strain that names no resistances. */
    public static final Resistances DEFAULT = new Resistances(Map.of());

    /** Every phenomenon always written, even where neutral, so the mod's own files name each of them. */
    public static final Codec<Resistances> CODEC = Keyed.codec(StressPhenomenon.class, ResistanceOdds.CODEC,
                    phenomenon -> UNNAMED, Keyed::always)
            .xmap(Resistances::new, Resistances::odds).codec();

    public Resistances {
        odds = Keyed.complete(StressPhenomenon.class, odds, phenomenon -> UNNAMED);
    }

    /** The odds for {@code phenomenon}. */
    public ResistanceOdds odds(StressPhenomenon phenomenon) {
        return odds.get(phenomenon);
    }

    /** This sheet with {@code odds} for {@code phenomenon}. */
    public Resistances with(StressPhenomenon phenomenon, ResistanceOdds odds) {
        EnumMap<StressPhenomenon, ResistanceOdds> changed = new EnumMap<>(this.odds);
        changed.put(phenomenon, odds);
        return new Resistances(changed);
    }
}
