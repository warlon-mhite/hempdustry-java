package com.warlonmhite.hempdustry.strain;

import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.Map;

/**
 * One plant's resistances, one {@link Resistance} per {@link StressPhenomenon}, rolled at planting from its
 * strain's {@link Resistances} and kept on its record until it is harvested or set back to age 0. Hidden: felt
 * only as how often the plant stresses.
 *
 * <p>Saved as {@code {"dry_ground": "weak", "mildew": "resistant"}}: a phenomenon at neutral is left out, and a
 * phenomenon a record does not name (one added after the plant was rolled) reads neutral.
 *
 * @param steps the step against each phenomenon; a phenomenon the map leaves out is neutral
 */
public record ResistanceGenes(Map<StressPhenomenon, Resistance> steps) {

    /** A plant with no record to ask: neutral on everything, as every plant was before resistances. */
    public static final ResistanceGenes NEUTRAL = new ResistanceGenes(Map.of());

    public static final Codec<ResistanceGenes> CODEC = Keyed.codec(StressPhenomenon.class, Resistance.CODEC,
            phenomenon -> Resistance.NEUTRAL, (phenomenon, step) -> step != Resistance.NEUTRAL)
            .xmap(ResistanceGenes::new, ResistanceGenes::steps).codec();

    public ResistanceGenes {
        steps = Keyed.complete(StressPhenomenon.class, steps, phenomenon -> Resistance.NEUTRAL);
    }

    /** This plant's step against {@code phenomenon}. */
    public Resistance get(StressPhenomenon phenomenon) {
        return steps.get(phenomenon);
    }

    /** This plant with {@code step} against {@code phenomenon}: what a test or a command does. */
    public ResistanceGenes with(StressPhenomenon phenomenon, Resistance step) {
        EnumMap<StressPhenomenon, Resistance> changed = new EnumMap<>(steps);
        changed.put(phenomenon, step);
        return new ResistanceGenes(changed);
    }

    /** Every phenomenon at {@code step}. */
    public static ResistanceGenes all(Resistance step) {
        EnumMap<StressPhenomenon, Resistance> every = new EnumMap<>(StressPhenomenon.class);
        for (StressPhenomenon phenomenon : StressPhenomenon.values()) {
            every.put(phenomenon, step);
        }
        return new ResistanceGenes(every);
    }
}
