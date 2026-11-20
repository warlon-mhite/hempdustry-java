package com.warlonmhite.hempdustry.strain;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * How hardy one plant is against one {@link StressPhenomenon}, as one of five steps rolled at planting
 * from its strain's odds ({@link ResistanceOdds}), weakest first. Hidden: no seed, plant or bud shows it; it is
 * felt only as how often the plant stresses. A resistant plant is never invincible: it still needs water.
 * {@link #score} is a step's place on the hardiness scale.
 */
public enum Resistance implements StringRepresentable {
    VERY_WEAK("very_weak", -2),
    WEAK("weak", -1),
    NEUTRAL("neutral", 0),
    RESISTANT("resistant", 1),
    VERY_RESISTANT("very_resistant", 2);

    public static final Codec<Resistance> CODEC = StringRepresentable.fromEnum(Resistance::values);

    private final String name;
    private final int score;

    Resistance(String name, int score) {
        this.name = name;
        this.score = score;
    }

    /** This step's place on the hardiness scale: very weak −2 to very resistant +2. */
    public int score() {
        return score;
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
