package com.warlonmhite.hempdustry.strain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.warlonmhite.hempdustry.strain.internal.FileFields;
import java.util.Map;

/**
 * A strain's quality sheet: the luck it brings to every harvest of its plants, and the odds of each
 * {@link QualityGene} a plant of it rolls at planting.
 *
 * <p>{@code baseLuck} stands in for the care a strain cannot take: the Nether's plants keep no light
 * record and stand on no Hydro Tray, so theirs is +2; Beldía, a hardy landrace grown for the sieve,
 * has −1. The odds are a weight per gene; they need not add up to 100.
 *
 * <p>Written in a strain's file as {@code "grow_quality": {"base_luck": 0, "poor": 20, "average": 50,
 * "good": 25, "elite": 5}}; every key is optional (a gene left out reads as {@link #DEFAULT}'s weight), and
 * a strain with no {@code grow_quality} reads as {@link #DEFAULT}.
 *
 * <p>A file and this constructor read a gene left out differently, on purpose: a file changes only the genes it
 * names, the rest keeping {@link #DEFAULT}'s weights, as every keyed sheet of a strain reads; the constructor takes
 * the map as the whole sheet. To change one gene of the default from code, start from {@code DEFAULT.weights()}.
 *
 * @param baseLuck the luck every harvest of the strain starts from
 * @param weights  the weight of each gene, one entry per gene; a gene the map leaves out weighs nothing
 */
public record GrowQuality(int baseLuck, Map<QualityGene, Integer> weights) {

    /** Purple Kush's sheet, and any strain's that names none. */
    public static final GrowQuality DEFAULT = new GrowQuality(0,
            Map.of(QualityGene.POOR, 20, QualityGene.AVERAGE, 50, QualityGene.GOOD, 25, QualityGene.ELITE, 5));

    public static final Codec<GrowQuality> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            FileFields.written(Codec.intRange(-64, 64), "base_luck", DEFAULT.baseLuck).forGetter(GrowQuality::baseLuck),
            Keyed.codec(QualityGene.class, Codec.intRange(0, 1000), DEFAULT::weight, Keyed::always)
                    .forGetter(GrowQuality::weights)
    ).apply(instance, GrowQuality::new));

    public GrowQuality {
        weights = Keyed.complete(QualityGene.class, weights, gene -> 0);
    }

    /** The weight of {@code gene}. */
    public int weight(QualityGene gene) {
        return weights.get(gene);
    }
}
