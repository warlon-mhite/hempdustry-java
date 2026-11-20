package com.warlonmhite.hempdustry.strain;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * How well one plant is made to grow, as one of four steps rolled at planting from its strain's odds
 * ({@link GrowQuality}): poor, average, good and elite, weakest first. Hidden: no seed, plant or bud shows it. It
 * is felt only at the harvest, where it joins the plant's grow luck and moves the odds of each {@link BudTier}.
 */
public enum QualityGene implements StringRepresentable {
    POOR("poor"),
    AVERAGE("average"),
    GOOD("good"),
    ELITE("elite");

    public static final Codec<QualityGene> CODEC = StringRepresentable.fromEnum(QualityGene::values);

    private final String name;

    QualityGene(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
