package com.warlonmhite.hempdustry.balance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.warlonmhite.hempdustry.Hempdustry;
import com.warlonmhite.hempdustry.strain.internal.FileFields;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;

/**
 * How powdery mildew reads a plant's climate, as data: {@code data/<namespace>/hempdustry/grow_climate/default.json}.
 *
 * <p>A growth step brings mildew at one in {@code onset_one_in}, and a mildewed plant reaches the neighbour it
 * picks at one in {@code spread_one_in}, each times the plant's <b>air</b> and its <b>humidity</b>, then its mildew resistance, then
 * {@code trimmed} on a plant trimmed in a window. Air is one of three ({@code open} under the sky, {@code stirred}
 * under a roof the sun still reaches, {@code still} in a sealed room); humidity one of four steps, from normal up
 * or down by the biome's rainfall and the water around the plant. The steps themselves, and what moves a plant
 * between them, stay code.
 *
 * <p>The mod ships its own numbers as that file, so a world that never touches it plays as built; the
 * {@code world.mildew} switch in the config still turns the whole thing off.
 *
 * <p>The same file holds the rest of a plant's luck with its keeping: the odds of each cause of plant stress beyond
 * the light ({@code stress}), and the share of bone meal that does nothing ({@code bone_meal_fails_one_in}).
 *
 * <p><b>Every key is optional</b>, inside the objects too, and reads as the built-in number when it is left out
 * ({@link FileFields#written}): a datapack that changes one number writes that one number, and a key added in a
 * later version leaves an older pack's file loading. The mod's own file is written in full.
 *
 * @param onsetOneIn   the odds a growth step brings mildew, before the climate, as one in this many
 * @param spreadOneIn  the odds a mildewed plant reaches the neighbour it picked, before that neighbour's climate,
 *                     as one in this many
 * @param air          the factor for each air
 * @param humidity     the factor for each humidity step
 * @param biome        where a biome's rainfall and warmth move the humidity
 * @param wetReach     how far, in blocks, standing water around a roofed plant makes its room wet
 * @param trimmed      the factor for a plant trimmed in either window: a thinner canopy
 * @param fan          how far ahead of it, and how wide, a running Grow Fan blows
 * @param dehumidifier how far a running Dehumidifier dries the air, by how much, and what fills its tank
 * @param stress       the odds of each cause of plant stress beyond the light, against a neutral plant
 * @param boneMealFailsOneIn the share of bone meal on a hemp plant that does nothing, though it is still used up,
 *                     as one in this many
 */
public record GrowClimate(int onsetOneIn, int spreadOneIn, Air air, Humidity humidity, BiomeRule biome, int wetReach,
                          float trimmed, Fan fan, Drier dehumidifier, Stress stress, int boneMealFailsOneIn) {

    public static final ResourceKey<Registry<GrowClimate>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "grow_climate"));

    public static final ResourceKey<GrowClimate> DEFAULT =
            ResourceKey.create(REGISTRY_KEY, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "default"));

    /**
     * The ranges the parts share. A holder of their own, so that a part's class can load before this one's
     * without reaching into it half-built: each part's {@code DEFAULT} is built before its codec, which reads it.
     */
    private static final class Ranges {
        static final Codec<Float> FACTOR = Codec.floatRange(0.0F, 64.0F);
        static final Codec<Integer> BLOCKS = Codec.intRange(0, 8);
        /** Odds as one in this many: from always (1) to all but never. */
        static final Codec<Integer> ONE_IN = Codec.intRange(1, 1_000_000);
    }

    /** The factor for each air: {@code open} sky, {@code stirred} under glass, {@code still} in a sealed room. */
    public record Air(float open, float stirred, float still) {
        public static final Air DEFAULT = new Air(1.0F, 3.0F, 6.0F);
        public static final Codec<Air> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                FileFields.written(Ranges.FACTOR, "open", DEFAULT.open).forGetter(Air::open),
                FileFields.written(Ranges.FACTOR, "stirred", DEFAULT.stirred).forGetter(Air::stirred),
                FileFields.written(Ranges.FACTOR, "still", DEFAULT.still).forGetter(Air::still)
        ).apply(instance, Air::new));
    }

    /** The factor for each humidity step. */
    public record Humidity(float dry, float normal, float damp, float soaked) {
        public static final Humidity DEFAULT = new Humidity(0.5F, 1.0F, 2.0F, 4.0F);
        public static final Codec<Humidity> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                FileFields.written(Ranges.FACTOR, "dry", DEFAULT.dry).forGetter(Humidity::dry),
                FileFields.written(Ranges.FACTOR, "normal", DEFAULT.normal).forGetter(Humidity::normal),
                FileFields.written(Ranges.FACTOR, "damp", DEFAULT.damp).forGetter(Humidity::damp),
                FileFields.written(Ranges.FACTOR, "soaked", DEFAULT.soaked).forGetter(Humidity::soaked)
        ).apply(instance, Humidity::new));
    }

    /**
     * A biome's step: one drier below {@code dryBelow} rainfall ({@code downfall} in a biome file), one wetter from
     * {@code wetFrom} rainfall in a biome at least {@code warmFrom} warm. The warmth keeps a snowy peak out: cold
     * slows the fungus as much as the wet feeds it.
     */
    public record BiomeRule(float dryBelow, float wetFrom, float warmFrom) {
        public static final BiomeRule DEFAULT = new BiomeRule(0.3F, 0.85F, 0.5F);
        public static final Codec<BiomeRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                FileFields.written(Codec.FLOAT, "dry_below_downfall", DEFAULT.dryBelow).forGetter(BiomeRule::dryBelow),
                FileFields.written(Codec.FLOAT, "wet_from_downfall", DEFAULT.wetFrom).forGetter(BiomeRule::wetFrom),
                FileFields.written(Codec.FLOAT, "wet_from_temperature", DEFAULT.warmFrom).forGetter(BiomeRule::warmFrom)
        ).apply(instance, BiomeRule::new));
    }

    /**
     * A Grow Fan's wind, from where it stands: a lane {@code ahead} blocks long the way its grille faces, from the
     * block in front of it, and {@code side} blocks to each side of the fan's line; the plants it blows on stand at
     * most {@code below} blocks under it and {@code above} blocks over it (each plant by its root). A box, as every
     * reach in the game is, laid out ahead of the fan instead of around it.
     */
    public record Fan(int ahead, int side, int below, int above) {
        public static final Fan DEFAULT = new Fan(9, 2, 3, 1);
        public static final Codec<Fan> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                FileFields.written(Codec.intRange(1, 16), "ahead", DEFAULT.ahead).forGetter(Fan::ahead),
                FileFields.written(Ranges.BLOCKS, "side", DEFAULT.side).forGetter(Fan::side),
                FileFields.written(Ranges.BLOCKS, "below", DEFAULT.below).forGetter(Fan::below),
                FileFields.written(Ranges.BLOCKS, "above", DEFAULT.above).forGetter(Fan::above)
        ).apply(instance, Fan::new));
    }

    /**
     * A device's reach, from where it stands: {@code reach} blocks each way across, the plants it reaches standing
     * at most {@code below} blocks under it and {@code above} blocks over it (each plant by its root).
     */
    public record Reach(int reach, int below, int above) {
        public static final Codec<Reach> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Ranges.BLOCKS.fieldOf("reach").forGetter(Reach::reach),
                Ranges.BLOCKS.fieldOf("below").forGetter(Reach::below),
                Ranges.BLOCKS.fieldOf("above").forGetter(Reach::above)
        ).apply(instance, Reach::new));
    }

    /**
     * A Dehumidifier: its reach, as a fan's; the humidity steps it takes off a roofed plant there ({@code dries});
     * and the growth steps of the plants it dries that make one level of condensate ({@code steps_per_level}).
     */
    public record Drier(int reach, int below, int above, int dries, int stepsPerLevel) {
        public static final Drier DEFAULT = new Drier(4, 2, 2, 2, 64);
        public static final Codec<Drier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                FileFields.written(Ranges.BLOCKS, "reach", DEFAULT.reach).forGetter(Drier::reach),
                FileFields.written(Ranges.BLOCKS, "below", DEFAULT.below).forGetter(Drier::below),
                FileFields.written(Ranges.BLOCKS, "above", DEFAULT.above).forGetter(Drier::above),
                FileFields.written(Codec.intRange(0, 3), "dries", DEFAULT.dries).forGetter(Drier::dries),
                FileFields.written(ExtraCodecs.POSITIVE_INT, "steps_per_level", DEFAULT.stepsPerLevel)
                        .forGetter(Drier::stepsPerLevel)
        ).apply(instance, Drier::new));

        /** Its reach alone. */
        public Reach area() {
            return new Reach(reach, below, above);
        }
    }

    /**
     * The odds of each cause of stress beyond the light against a flowering plant of neutral resistance, each as one
     * in this many: bone meal on a plant in a fed Grow Pot or Hydro Tray ({@code overfed}), a bottle poured into a
     * pot that is still wet ({@code overwatered}), a growth step in a dry pot or tray ({@code dry_bed}), one in a
     * watered tray with its pump off ({@code stagnant}), and one on bone-dry farmland ({@code dry_field}). A plant's
     * own resistance to the cause's phenomenon then scales each, as it scales the built-in odds.
     */
    public record Stress(int overfedOneIn, int overwateredOneIn, int dryBedOneIn, int stagnantOneIn, int dryFieldOneIn) {
        public static final Stress DEFAULT = new Stress(9, 4, 3, 6, 20);
        public static final Codec<Stress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                FileFields.written(Ranges.ONE_IN, "overfed_one_in", DEFAULT.overfedOneIn).forGetter(Stress::overfedOneIn),
                FileFields.written(Ranges.ONE_IN, "overwatered_one_in", DEFAULT.overwateredOneIn)
                        .forGetter(Stress::overwateredOneIn),
                FileFields.written(Ranges.ONE_IN, "dry_bed_one_in", DEFAULT.dryBedOneIn).forGetter(Stress::dryBedOneIn),
                FileFields.written(Ranges.ONE_IN, "stagnant_one_in", DEFAULT.stagnantOneIn).forGetter(Stress::stagnantOneIn),
                FileFields.written(Ranges.ONE_IN, "dry_field_one_in", DEFAULT.dryFieldOneIn).forGetter(Stress::dryFieldOneIn)
        ).apply(instance, Stress::new));

        /** Bone meal on a flowering plant in a fed bed. */
        public float overfed() {
            return 1.0F / overfedOneIn;
        }

        /** A bottle poured into a Grow Pot that is still wet. */
        public float overwatered() {
            return 1.0F / overwateredOneIn;
        }

        /** A growth step in a dry Grow Pot or Hydro Tray. */
        public float dryBed() {
            return 1.0F / dryBedOneIn;
        }

        /** A growth step in a watered Hydro Tray with its pump off. */
        public float stagnant() {
            return 1.0F / stagnantOneIn;
        }

        /** A growth step on farmland at moisture 0. */
        public float dryField() {
            return 1.0F / dryFieldOneIn;
        }
    }

    /**
     * The 2.2 climate. A field catches mildew at 1 in 1,500 a step and passes it to a neighbour at 1 in 100, as
     * 2.1's open sky did; glass triples both, a sealed room six times them, and a swamp's hydro room 24 times.
     * A trim takes a quarter off: real but modest, as leaf removal is in a vineyard. A fan blows a lane 9 blocks long
     * and 5 wide ahead of its grille, so two side by side cover a 9 x 9 plot from one edge (the plot one water source
     * keeps farmland wet across, and one Dehumidifier dries), 45 plants a fan, about the 49 of a square around it;
     * it reaches from a plant one block above it (one on the floor beside a pot) to three below it (one hung from the
     * ceiling over a field). A Dehumidifier dries a 9 x 9 room two steps, two blocks up and down, and 64 dried steps
     * make a level of its tank: a 9 x 9 grow of 81 plants, seven steps each, empties it about once.
     *
     * <p>Stress and bone meal are 2.1's numbers, as {@code PlantStress} has them: a bed carries the risk and a field
     * barely does, and a third of bone meal on hemp is wasted, so it nudges a plant rather than rushing it.
     */
    public static final GrowClimate BUILT_IN = new GrowClimate(
            1500, 100,
            Air.DEFAULT,
            Humidity.DEFAULT,
            BiomeRule.DEFAULT,
            2,
            0.75F,
            Fan.DEFAULT,
            Drier.DEFAULT,
            Stress.DEFAULT,
            3);

    public static final Codec<GrowClimate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            FileFields.written(ExtraCodecs.POSITIVE_INT, "onset_one_in", BUILT_IN.onsetOneIn).forGetter(GrowClimate::onsetOneIn),
            FileFields.written(ExtraCodecs.POSITIVE_INT, "spread_one_in", BUILT_IN.spreadOneIn).forGetter(GrowClimate::spreadOneIn),
            FileFields.written(Air.CODEC, "air", BUILT_IN.air).forGetter(GrowClimate::air),
            FileFields.written(Humidity.CODEC, "humidity", BUILT_IN.humidity).forGetter(GrowClimate::humidity),
            FileFields.written(BiomeRule.CODEC, "biome", BUILT_IN.biome).forGetter(GrowClimate::biome),
            FileFields.written(Codec.intRange(0, 4), "wet_reach", BUILT_IN.wetReach).forGetter(GrowClimate::wetReach),
            FileFields.written(Ranges.FACTOR, "trimmed", BUILT_IN.trimmed).forGetter(GrowClimate::trimmed),
            FileFields.written(Fan.CODEC, "fan", BUILT_IN.fan).forGetter(GrowClimate::fan),
            FileFields.written(Drier.CODEC, "dehumidifier", BUILT_IN.dehumidifier).forGetter(GrowClimate::dehumidifier),
            FileFields.written(Stress.CODEC, "stress", BUILT_IN.stress).forGetter(GrowClimate::stress),
            FileFields.written(Ranges.ONE_IN, "bone_meal_fails_one_in", BUILT_IN.boneMealFailsOneIn)
                    .forGetter(GrowClimate::boneMealFailsOneIn)
    ).apply(instance, GrowClimate::new));

    public static void bootstrap(BootstrapContext<GrowClimate> registerable) {
        registerable.register(DEFAULT, BUILT_IN);
    }

    /** The odds a growth step brings mildew, before the climate. */
    public float onset() {
        return 1.0F / onsetOneIn;
    }

    /** The odds a mildewed plant reaches the neighbour it picked, before that neighbour's climate. */
    public float spread() {
        return 1.0F / spreadOneIn;
    }

    /**
     * Whether one bone meal on a hemp plant grows it: all but one in {@code bone_meal_fails_one_in} do. The bone meal
     * is used up either way ({@code BoneMealItem} spends it whatever the block answers), so a miss is a wasted one.
     */
    public boolean boneMealTakes(RandomSource random) {
        return random.nextInt(boneMealFailsOneIn) != 0;
    }

    /** The climate in this world. */
    public static GrowClimate of(HolderLookup.Provider registries) {
        return ModBalance.entry(registries, DEFAULT, BUILT_IN);
    }
}
