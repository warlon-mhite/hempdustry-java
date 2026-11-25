package com.warlonmhite.hempdustry.strain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.warlonmhite.hempdustry.strain.internal.FileFields;
import java.util.Map;

/**
 * A strain's potency sheet: what one of its buds is worth at the oven, and the odds of each
 * {@link PotencyBand} a plant of it rolls.
 *
 * <p>{@code base} is a mid bud's decarboxylated hemp, the number the bowl weighs a bud by (a bud of
 * base 4 is one dose of THC); every strain the mod ships has 4, which is what a bud baked into in 2.1.
 * The odds are a weight per band; they need not add up to 100. Extra-high is for breeders: rare or
 * impossible on a native plant.
 *
 * <p>Written in a strain's file as {@code "potency": {"base": 4, "low": 25, "mid": 50, "high": 25,
 * "extra_high": 0}}; every key is optional (a band left out reads as {@link #DEFAULT}'s weight), and a
 * strain with no {@code potency} at all reads as {@link #DEFAULT}.
 *
 * <p>A file and this constructor read a band left out differently, on purpose: a file changes only the bands it
 * names, the rest keeping {@link #DEFAULT}'s weights, as every keyed sheet of a strain reads; the constructor takes
 * the map as the whole sheet, so {@code new Potency(4, Map.of(PotencyBand.HIGH, 1))} rolls high every time. To change
 * one band of the default from code, start from {@code DEFAULT.weights()}.
 *
 * @param base    a mid bud's decarboxylated hemp
 * @param weights the weight of each band, one entry per band; a band the map leaves out weighs nothing
 */
public record Potency(int base, Map<PotencyBand, Integer> weights) {

    /** A mid bud's decarboxylated hemp, for every strain that names none. */
    public static final int DEFAULT_BASE = 4;

    /** Purple Kush's and Lemon Haze's sheet, and any strain's that names none. */
    public static final Potency DEFAULT = new Potency(DEFAULT_BASE,
            Map.of(PotencyBand.LOW, 25, PotencyBand.MID, 50, PotencyBand.HIGH, 25));

    public static final Codec<Potency> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            FileFields.written(Codec.intRange(1, 64), "base", DEFAULT.base).forGetter(Potency::base),
            Keyed.codec(PotencyBand.class, Codec.intRange(0, 1000), DEFAULT::weight, Keyed::always)
                    .forGetter(Potency::weights)
    ).apply(instance, Potency::new));

    public Potency {
        weights = Keyed.complete(PotencyBand.class, weights, band -> 0);
    }

    /** The weight of {@code band}. */
    public int weight(PotencyBand band) {
        return weights.get(band);
    }
}
