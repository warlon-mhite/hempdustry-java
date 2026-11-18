package com.warlonmhite.hempdustry.strain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.util.RandomSource;

/**
 * A strain's resistance sheet: for each {@link StressPhenomenon}, the odds of each {@link Resistance} step a
 * plant of it rolls at planting.
 *
 * <p>Written in a strain's {@code plant} object as {@code "resistances": {"dry_ground": "neutral",
 * "overwatering": "resistant", "light": "neutral", "mildew": "weak"}}, each phenomenon as its most likely step
 * or as five weights ({@link ResistanceOdds}). <b>A phenomenon the file does not name takes the shared spread around
 * neutral</b>, as {@code "light": "neutral"} does, and a strain with no {@code resistances} takes it on all four: a
 * datapack strain written for 2.1 varies plant to plant like every strain. A strain that wants every plant neutral,
 * as before resistances, writes {@code "light": {"neutral": 100}}. A sheet is written back with all four named.
 */
public record Resistances(ResistanceOdds dryGround, ResistanceOdds overwatering, ResistanceOdds light,
                          ResistanceOdds mildew) {

    /** The odds of a phenomenon a file leaves out: the shared spread around neutral. */
    private static final ResistanceOdds UNNAMED = ResistanceOdds.spread(Resistance.NEUTRAL);

    /** The spread around neutral on all four: a strain that names no resistances. */
    public static final Resistances DEFAULT = new Resistances(UNNAMED, UNNAMED, UNNAMED, UNNAMED);

    public static final Codec<Resistances> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            phenomenon("dry_ground").forGetter(Resistances::dryGround),
            phenomenon("overwatering").forGetter(Resistances::overwatering),
            phenomenon("light").forGetter(Resistances::light),
            phenomenon("mildew").forGetter(Resistances::mildew)
    ).apply(instance, Resistances::new));

    /**
     * One phenomenon's key: read as the spread around neutral when left out, always written, so a file written
     * from a sheet names all four even where they are neutral.
     */
    private static MapCodec<ResistanceOdds> phenomenon(String key) {
        return ResistanceOdds.CODEC.optionalFieldOf(key).xmap(odds -> odds.orElse(UNNAMED), Optional::of);
    }

    /** A sheet of most likely steps, each spread by the shared spread: how every built-in strain is written. */
    public static Resistances of(Resistance dryGround, Resistance overwatering, Resistance light, Resistance mildew) {
        return new Resistances(ResistanceOdds.spread(dryGround), ResistanceOdds.spread(overwatering),
                ResistanceOdds.spread(light), ResistanceOdds.spread(mildew));
    }

    /** The odds for {@code phenomenon}. */
    public ResistanceOdds odds(StressPhenomenon phenomenon) {
        return switch (phenomenon) {
            case DRY_GROUND -> dryGround;
            case OVERWATERING -> overwatering;
            case LIGHT -> light;
            case MILDEW -> mildew;
        };
    }

    /** One plant's four steps, each drawn on its own from its odds. */
    public ResistanceGenes roll(RandomSource random) {
        return new ResistanceGenes(dryGround.roll(random), overwatering.roll(random), light.roll(random),
                mildew.roll(random));
    }
}
