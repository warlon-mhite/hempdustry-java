package com.warlonmhite.hempdustry.balance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.warlonmhite.hempdustry.Hempdustry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;

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
 */
public record GrowClimate(int onsetOneIn, int spreadOneIn, Air air, Humidity humidity, BiomeRule biome, int wetReach,
                          float trimmed, Fan fan, Drier dehumidifier) {

    public static final ResourceKey<Registry<GrowClimate>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "grow_climate"));

    public static final ResourceKey<GrowClimate> DEFAULT =
            ResourceKey.create(REGISTRY_KEY, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "default"));

    private static final Codec<Float> FACTOR = Codec.floatRange(0.0F, 64.0F);

    /** The factor for each air: {@code open} sky, {@code stirred} under glass, {@code still} in a sealed room. */
    public record Air(float open, float stirred, float still) {
        public static final Codec<Air> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                FACTOR.fieldOf("open").forGetter(Air::open),
                FACTOR.fieldOf("stirred").forGetter(Air::stirred),
                FACTOR.fieldOf("still").forGetter(Air::still)
        ).apply(instance, Air::new));
    }

    /** The factor for each humidity step. */
    public record Humidity(float dry, float normal, float damp, float soaked) {
        public static final Codec<Humidity> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                FACTOR.fieldOf("dry").forGetter(Humidity::dry),
                FACTOR.fieldOf("normal").forGetter(Humidity::normal),
                FACTOR.fieldOf("damp").forGetter(Humidity::damp),
                FACTOR.fieldOf("soaked").forGetter(Humidity::soaked)
        ).apply(instance, Humidity::new));
    }

    /**
     * A biome's step: one drier below {@code dryBelow} rainfall ({@code downfall} in a biome file), one wetter from
     * {@code wetFrom} rainfall in a biome at least {@code warmFrom} warm. The warmth keeps a snowy peak out: cold
     * slows the fungus as much as the wet feeds it.
     */
    public record BiomeRule(float dryBelow, float wetFrom, float warmFrom) {
        public static final Codec<BiomeRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.fieldOf("dry_below_downfall").forGetter(BiomeRule::dryBelow),
                Codec.FLOAT.fieldOf("wet_from_downfall").forGetter(BiomeRule::wetFrom),
                Codec.FLOAT.fieldOf("wet_from_temperature").forGetter(BiomeRule::warmFrom)
        ).apply(instance, BiomeRule::new));
    }

    /**
     * A Grow Fan's wind, from where it stands: a lane {@code ahead} blocks long the way its grille faces, from the
     * block in front of it, and {@code side} blocks to each side of the fan's line; the plants it blows on stand at
     * most {@code below} blocks under it and {@code above} blocks over it (each plant by its root). A box, as every
     * reach in the game is, laid out ahead of the fan instead of around it.
     */
    public record Fan(int ahead, int side, int below, int above) {
        private static final Codec<Integer> BLOCKS = Codec.intRange(0, 8);
        public static final Codec<Fan> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 16).fieldOf("ahead").forGetter(Fan::ahead),
                BLOCKS.fieldOf("side").forGetter(Fan::side),
                BLOCKS.fieldOf("below").forGetter(Fan::below),
                BLOCKS.fieldOf("above").forGetter(Fan::above)
        ).apply(instance, Fan::new));
    }

    /**
     * A device's reach, from where it stands: {@code reach} blocks each way across, the plants it reaches standing
     * at most {@code below} blocks under it and {@code above} blocks over it (each plant by its root).
     */
    public record Reach(int reach, int below, int above) {
        private static final Codec<Integer> BLOCKS = Codec.intRange(0, 8);
        public static final Codec<Reach> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BLOCKS.fieldOf("reach").forGetter(Reach::reach),
                BLOCKS.fieldOf("below").forGetter(Reach::below),
                BLOCKS.fieldOf("above").forGetter(Reach::above)
        ).apply(instance, Reach::new));
    }

    /**
     * A Dehumidifier: its reach, as a fan's; the humidity steps it takes off a roofed plant there ({@code dries});
     * and the growth steps of the plants it dries that make one level of condensate ({@code steps_per_level}).
     */
    public record Drier(int reach, int below, int above, int dries, int stepsPerLevel) {
        private static final Codec<Integer> BLOCKS = Codec.intRange(0, 8);
        public static final Codec<Drier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BLOCKS.fieldOf("reach").forGetter(Drier::reach),
                BLOCKS.fieldOf("below").forGetter(Drier::below),
                BLOCKS.fieldOf("above").forGetter(Drier::above),
                Codec.intRange(0, 3).fieldOf("dries").forGetter(Drier::dries),
                ExtraCodecs.POSITIVE_INT.fieldOf("steps_per_level").forGetter(Drier::stepsPerLevel)
        ).apply(instance, Drier::new));

        /** Its reach alone. */
        public Reach area() {
            return new Reach(reach, below, above);
        }
    }

    public static final Codec<GrowClimate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.POSITIVE_INT.fieldOf("onset_one_in").forGetter(GrowClimate::onsetOneIn),
            ExtraCodecs.POSITIVE_INT.fieldOf("spread_one_in").forGetter(GrowClimate::spreadOneIn),
            Air.CODEC.fieldOf("air").forGetter(GrowClimate::air),
            Humidity.CODEC.fieldOf("humidity").forGetter(GrowClimate::humidity),
            BiomeRule.CODEC.fieldOf("biome").forGetter(GrowClimate::biome),
            Codec.intRange(0, 4).fieldOf("wet_reach").forGetter(GrowClimate::wetReach),
            FACTOR.fieldOf("trimmed").forGetter(GrowClimate::trimmed),
            Fan.CODEC.fieldOf("fan").forGetter(GrowClimate::fan),
            Drier.CODEC.fieldOf("dehumidifier").forGetter(GrowClimate::dehumidifier)
    ).apply(instance, GrowClimate::new));

    /**
     * The 2.2 climate. A field catches mildew at 1 in 1,500 a step and passes it to a neighbour at 1 in 100, as
     * 2.1's open sky did; glass triples both, a sealed room six times them, and a swamp's hydro room 24 times.
     * A trim takes a quarter off: real but modest, as leaf removal is in a vineyard. A fan blows a lane 9 blocks long
     * and 5 wide ahead of its grille, so two side by side cover a 9 x 9 plot from one edge (the plot one water source
     * keeps farmland wet across, and one Dehumidifier dries), 45 plants a fan, about the 49 of a square around it;
     * it reaches from a plant one block above it (one on the floor beside a pot) to three below it (one hung from the
     * ceiling over a field). A Dehumidifier dries a 9 x 9 room two steps, two blocks up and down, and 64 dried steps
     * make a level of its tank: a 9 x 9 grow of 81 plants, seven steps each, empties it about once.
     */
    public static final GrowClimate BUILT_IN = new GrowClimate(
            1500, 100,
            new Air(1.0F, 3.0F, 6.0F),
            new Humidity(0.5F, 1.0F, 2.0F, 4.0F),
            new BiomeRule(0.3F, 0.85F, 0.5F),
            2,
            0.75F,
            new Fan(9, 2, 3, 1),
            new Drier(4, 2, 2, 2, 64));

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

    /** The climate in this world. */
    public static GrowClimate of(HolderLookup.Provider registries) {
        return ModBalance.entry(registries, DEFAULT, BUILT_IN);
    }
}
