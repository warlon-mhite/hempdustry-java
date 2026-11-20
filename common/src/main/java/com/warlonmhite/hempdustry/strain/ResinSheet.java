package com.warlonmhite.hempdustry.strain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;

/**
 * A strain's resin: the weight of each {@link ResinStep} a plant of it rolls at planting, and the
 * {@link ResinTexture} that decides which route a rich plant's bonus pays. The weights need not add up to 100.
 *
 * <p>Written in a strain's {@code plant} object as
 * {@code "resin": {"rich": 20, "very_rich": 80, "texture": "dry"}}: a weight left out is 0, a texture left out is
 * even. Odds that are all zero roll average. A strain with no {@code resin} always rolls average, even: the plant
 * every strain grew before resin, so a datapack strain written for 2.1 sieves and rubs exactly as it did.
 *
 * @param weights the weight of each step, one entry per step; a step the map leaves out weighs nothing
 * @param texture which route a rich plant's bonus pays
 */
public record ResinSheet(Map<ResinStep, Integer> weights, ResinTexture texture) {

    /** Always average, even: a strain that names no resin. */
    public static final ResinSheet DEFAULT = new ResinSheet(Map.of(ResinStep.AVERAGE, 100), ResinTexture.EVEN);

    public static final Codec<ResinSheet> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Keyed.codec(ResinStep.class, Codec.intRange(0, 1000), step -> 0, Keyed::notZero)
                    .forGetter(ResinSheet::weights),
            FileFields.written(ResinTexture.CODEC, "texture", ResinTexture.EVEN).forGetter(ResinSheet::texture)
    ).apply(instance, ResinSheet::new));

    public ResinSheet {
        weights = Keyed.complete(ResinStep.class, weights, step -> 0);
    }

    /** The weight of {@code step}. */
    public int weight(ResinStep step) {
        return weights.get(step);
    }
}
