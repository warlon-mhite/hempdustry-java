package com.warlonmhite.hempdustry.balance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.warlonmhite.hempdustry.Hempdustry;
import com.warlonmhite.hempdustry.platform.Services;
import com.warlonmhite.hempdustry.strain.BudTier;
import com.warlonmhite.hempdustry.strain.internal.FileFields;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * What a plant's genetics are worth, as data: {@code data/<namespace>/hempdustry/genetics/default.json}. The numbers
 * only; what each does, and in which order, stays in the rules that read them ({@code strain.internal}'s
 * {@code Tiers}, {@code Bands}, {@code Resins} and {@code Hardiness}).
 *
 * <ul>
 *   <li>{@code tiers}: for each bud tier, its weight in a harvest's roll and what each point of grow luck adds to it
 *       (vanilla's loot-luck grammar), then what it multiplies when smoked: the cough, Nausea and green-out odds, and
 *       how long its own entry's effects last.</li>
 *   <li>{@code band_offsets}: the decarboxylated hemp a low, high or extra-high bud adds to (or takes from) a mid
 *       one's.</li>
 *   <li>{@code resin}: what each resin step multiplies a bud's rate at the Sifting Box by, a ripe plant's odds of a
 *       charas when it is rubbed, and how much of a rich plant's bonus each texture sends to each route.</li>
 *   <li>{@code resistance}: what each resistance step multiplies a cause of stress by, from which age a lost light
 *       stresses a plant of each step, and how often a resistant plant shrugs a lost light off.</li>
 * </ul>
 *
 * <h2>What stays fixed: the middle of each ladder</h2>
 *
 * A mid band, an average resin step and a neutral resistance are what an item or a plant from before genetics
 * reads as, so they are not in the file: mid bakes as a bud always did
 * (offset 0), average sieves at the tags' own rate (×1) and rubs at {@code Defoliation}'s 1 in 4, neutral keeps the
 * odds as they are (×1) and loses its light from flowering (age 4), and neither ever shrugs a loss off. A file moves
 * the rungs around them.
 *
 * <h2>The bounds, and why</h2>
 *
 * <b>A tier buys smoothness and length, never level.</b> Nothing in {@code tiers} reaches an amplifier: the weights
 * pick a tier, and the four factors scale odds and one entry's length. So the bounds only keep each number meaning
 * what it says: a smoking factor from 0 (never) to ×4, a length from ×¼ to ×2 (a tier never takes an effect away,
 * and never stretches it past what the band's own stretch may, {@code Dosing.MAX_THC_STRETCH}). The length is a
 * factor on the device's duration that the dose then stretches, so a longer tier never makes a stronger dose
 * shorter: duration still never shrinks as the amplifier rises. A green-out factor scales a chance, and a chance of
 * never stays never, so dose 1 still never greens you out. The bowl's caps ({@code Dosing}'s dose and stretch caps,
 * the config's {@code maxLevel}) stay code, so no number here lifts an effect past them.
 *
 * <p>A band does buy level, as it always has (a high bud is 5/4 of a mid one), so its offsets are held tight: low from
 * −3 to 0 (a bud is never worth less than one decarboxylated hemp anyway), high and extra-high from 0 to +4 (twice
 * a base-4 bud at most); the bowl's dose cap still holds over them. Each resin and resistance step stays on its own
 * side of the middle: a low step never sieves or rubs better than average, a rich one never worse; a weak plant is
 * never hardier than a neutral one, a resistant one never frailer; a weak plant loses its light from age 1 to 4, a
 * resistant one from 4 to 7. Within a side the order is the pack's, so a partial file always loads.
 *
 * <p><b>Every key is optional</b>, inside the objects too, and reads as the built-in number when it is left out
 * ({@link FileFields#written}); a number out of its range refuses the file. The mod's own file is written in full.
 *
 * @param tiers        each bud tier's weight and what it buys
 * @param bandOffsets  the oven's offset for each band beside mid
 * @param resin        the resin steps' rates and the textures' shares
 * @param resistance   the resistance steps' stress numbers
 */
public record Genetics(TierTable tiers, BandOffsets bandOffsets, Resin resin, Resistance resistance) {

    public static final ResourceKey<Registry<Genetics>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "genetics"));

    public static final ResourceKey<Genetics> DEFAULT =
            ResourceKey.create(REGISTRY_KEY, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "default"));

    /** The ranges the parts share, in a holder of their own so that a part's class never reads this one half-built. */
    private static final class Ranges {
        /** A tier's factor on a chance: from never (0) to four times as likely. */
        static final Codec<Float> CHANCE = Codec.floatRange(0.0F, 4.0F);
        /** A tier's factor on its entry's length: never none, never more than the band's stretch cap. */
        static final Codec<Float> LENGTH = Codec.floatRange(0.25F, 2.0F);
        /** A weight in a harvest's roll, and what a point of luck adds to it. */
        static final Codec<Integer> WEIGHT = Codec.intRange(-1024, 1024);
        static final Codec<Integer> QUALITY = Codec.intRange(-64, 64);
        /** A share of a rich plant's bonus. */
        static final Codec<Float> SHARE = Codec.floatRange(0.0F, 1.0F);
        /** Below the middle of a ladder, and above it. */
        static final Codec<Float> UNDER_ONE = Codec.floatRange(0.0F, 1.0F);
        static final Codec<Float> SIEVE_OVER_ONE = Codec.floatRange(1.0F, 4.0F);
        static final Codec<Float> STRESS_OVER_ONE = Codec.floatRange(1.0F, 8.0F);
    }

    /**
     * One bud tier's numbers: {@code weight} and {@code quality} in a harvest's roll, as vanilla weighs a loot entry
     * ({@code max(⌊weight + quality × luck⌋, 0)}), then what it multiplies when smoked: the device's {@code cough},
     * {@code nausea} and {@code green_out} odds, and the {@code duration} of its own entry's effects.
     */
    public record Tier(int weight, int quality, float cough, float duration, float nausea, float greenOut) {
        /** A tier's codec, each key it leaves out read from {@code fallback}. */
        static Codec<Tier> codec(Tier fallback) {
            return RecordCodecBuilder.create(instance -> instance.group(
                    FileFields.written(Ranges.WEIGHT, "weight", fallback.weight).forGetter(Tier::weight),
                    FileFields.written(Ranges.QUALITY, "quality", fallback.quality).forGetter(Tier::quality),
                    FileFields.written(Ranges.CHANCE, "cough", fallback.cough).forGetter(Tier::cough),
                    FileFields.written(Ranges.LENGTH, "duration", fallback.duration).forGetter(Tier::duration),
                    FileFields.written(Ranges.CHANCE, "nausea", fallback.nausea).forGetter(Tier::nausea),
                    FileFields.written(Ranges.CHANCE, "green_out", fallback.greenOut).forGetter(Tier::greenOut)
            ).apply(instance, Tier::new));
        }
    }

    /**
     * The four tiers. Mids fades as luck grows (60, −10 a point), Dank holds (35, +4), Top Shelf appears from luck 1
     * (0, +6) and Epic from luck 4 (−6, +2). Mids coughs half again and lasts three quarters; Dank is 2.1's hit with
     * a quarter off its Nausea and green-out; Top Shelf halves all three and lasts a quarter more; Epic never coughs
     * or retches, greens out at a quarter and lasts half again.
     */
    public record TierTable(Tier mids, Tier dank, Tier topShelf, Tier epic) {
        public static final TierTable DEFAULT = new TierTable(
                new Tier(60, -10, 1.5F, 0.75F, 1.0F, 1.0F),
                new Tier(35, 4, 1.0F, 1.0F, 0.75F, 0.75F),
                new Tier(0, 6, 0.5F, 1.25F, 0.5F, 0.5F),
                new Tier(-6, 2, 0.0F, 1.5F, 0.0F, 0.25F));
        public static final Codec<TierTable> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                FileFields.written(Tier.codec(DEFAULT.mids), "mids", DEFAULT.mids).forGetter(TierTable::mids),
                FileFields.written(Tier.codec(DEFAULT.dank), "dank", DEFAULT.dank).forGetter(TierTable::dank),
                FileFields.written(Tier.codec(DEFAULT.topShelf), "top_shelf", DEFAULT.topShelf).forGetter(TierTable::topShelf),
                FileFields.written(Tier.codec(DEFAULT.epic), "epic", DEFAULT.epic).forGetter(TierTable::epic)
        ).apply(instance, TierTable::new));

        /** {@code tier}'s numbers. */
        public Tier of(BudTier tier) {
            return switch (tier) {
                case MIDS -> mids;
                case DANK -> dank;
                case TOP_SHELF -> topShelf;
                case EPIC -> epic;
            };
        }
    }

    /** The decarboxylated hemp each band beside mid adds to a mid bud's: low −1, high +1, extra-high +2. */
    public record BandOffsets(int low, int high, int extraHigh) {
        public static final BandOffsets DEFAULT = new BandOffsets(-1, 1, 2);
        public static final Codec<BandOffsets> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                FileFields.written(Codec.intRange(-3, 0), "low", DEFAULT.low).forGetter(BandOffsets::low),
                FileFields.written(Codec.intRange(0, 4), "high", DEFAULT.high).forGetter(BandOffsets::high),
                FileFields.written(Codec.intRange(0, 4), "extra_high", DEFAULT.extraHigh).forGetter(BandOffsets::extraHigh)
        ).apply(instance, BandOffsets::new));
    }

    /** A number for each resin step beside average: {@code very_low}, {@code low}, {@code rich}, {@code very_rich}. */
    public record Steps(float veryLow, float low, float rich, float veryRich) {
        static Codec<Steps> codec(Steps fallback, Codec<Float> under, Codec<Float> over) {
            return RecordCodecBuilder.create(instance -> instance.group(
                    FileFields.written(under, "very_low", fallback.veryLow).forGetter(Steps::veryLow),
                    FileFields.written(under, "low", fallback.low).forGetter(Steps::low),
                    FileFields.written(over, "rich", fallback.rich).forGetter(Steps::rich),
                    FileFields.written(over, "very_rich", fallback.veryRich).forGetter(Steps::veryRich)
            ).apply(instance, Steps::new));
        }
    }

    /** A ripe plant's odds of a charas when rubbed, for each resin step beside average, each as one in this many. */
    public record RubOdds(int veryLowOneIn, int lowOneIn, int richOneIn, int veryRichOneIn) {
        /** Average's odds, which stay {@code Defoliation}'s: a low step never rubs better, a rich one never worse. */
        public static final int AVERAGE_ONE_IN = 4;
        public static final RubOdds DEFAULT = new RubOdds(8, 6, 3, 2);
        private static final Codec<Integer> UNDER = Codec.intRange(AVERAGE_ONE_IN, 64);
        private static final Codec<Integer> OVER = Codec.intRange(1, AVERAGE_ONE_IN);
        public static final Codec<RubOdds> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                FileFields.written(UNDER, "very_low", DEFAULT.veryLowOneIn).forGetter(RubOdds::veryLowOneIn),
                FileFields.written(UNDER, "low", DEFAULT.lowOneIn).forGetter(RubOdds::lowOneIn),
                FileFields.written(OVER, "rich", DEFAULT.richOneIn).forGetter(RubOdds::richOneIn),
                FileFields.written(OVER, "very_rich", DEFAULT.veryRichOneIn).forGetter(RubOdds::veryRichOneIn)
        ).apply(instance, RubOdds::new));
    }

    /** How much of a rich plant's bonus each texture sends to a route: 0 to 1. */
    public record Shares(float dry, float even, float sticky) {
        static Codec<Shares> codec(Shares fallback) {
            return RecordCodecBuilder.create(instance -> instance.group(
                    FileFields.written(Ranges.SHARE, "dry", fallback.dry).forGetter(Shares::dry),
                    FileFields.written(Ranges.SHARE, "even", fallback.even).forGetter(Shares::even),
                    FileFields.written(Ranges.SHARE, "sticky", fallback.sticky).forGetter(Shares::sticky)
            ).apply(instance, Shares::new));
        }
    }

    /**
     * The resin steps: {@code sieve}, what a bud's step multiplies its rate at the Sifting Box by (very low ×0.5, low
     * ×0.75, rich ×1.5, very rich ×2); {@code rub_one_in}, a ripe plant's charas odds (1 in 8, 6, 3, 2); and what share
     * of a rich plant's bonus each texture sends to the sieve ({@code sieve_share}: dry all, even and sticky half) and
     * to the rub ({@code rub_share}: dry none, even half, sticky all). A low step costs in full on both routes.
     */
    public record Resin(Steps sieve, RubOdds rubOneIn, Shares sieveShare, Shares rubShare) {
        public static final Steps SIEVE = new Steps(0.5F, 0.75F, 1.5F, 2.0F);
        public static final Shares SIEVE_SHARE = new Shares(1.0F, 0.5F, 0.5F);
        public static final Shares RUB_SHARE = new Shares(0.0F, 0.5F, 1.0F);
        public static final Resin DEFAULT = new Resin(SIEVE, RubOdds.DEFAULT, SIEVE_SHARE, RUB_SHARE);
        public static final Codec<Resin> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                FileFields.written(Steps.codec(SIEVE, Ranges.UNDER_ONE, Ranges.SIEVE_OVER_ONE), "sieve", SIEVE)
                        .forGetter(Resin::sieve),
                FileFields.written(RubOdds.CODEC, "rub_one_in", RubOdds.DEFAULT).forGetter(Resin::rubOneIn),
                FileFields.written(Shares.codec(SIEVE_SHARE), "sieve_share", SIEVE_SHARE).forGetter(Resin::sieveShare),
                FileFields.written(Shares.codec(RUB_SHARE), "rub_share", RUB_SHARE).forGetter(Resin::rubShare)
        ).apply(instance, Resin::new));
    }

    /** A number for each resistance step beside neutral. */
    public record Hardy<T>(T veryWeak, T weak, T resistant, T veryResistant) {
        static <T> Codec<Hardy<T>> codec(Hardy<T> fallback, Codec<T> weakSide, Codec<T> resistantSide) {
            return RecordCodecBuilder.create(instance -> instance.group(
                    FileFields.written(weakSide, "very_weak", fallback.veryWeak).forGetter(Hardy::veryWeak),
                    FileFields.written(weakSide, "weak", fallback.weak).forGetter(Hardy::weak),
                    FileFields.written(resistantSide, "resistant", fallback.resistant).forGetter(Hardy::resistant),
                    FileFields.written(resistantSide, "very_resistant", fallback.veryResistant).forGetter(Hardy::veryResistant)
            ).apply(instance, Hardy::new));
        }
    }

    /**
     * What a plant's resistance step does to its stress: {@code stress_factor}, what it multiplies a cause's chance by
     * (very weak ×2, weak ×1.5, resistant ×½, very resistant ×¼); {@code light_loss_age}, the age from which a lost
     * light stresses it (weak and very weak 3, resistant and very resistant 4, as neutral); and
     * {@code ignores_lost_light}, how often a resistant plant shrugs a lost light off (resistant 1 time in 2, very
     * resistant 3 in 4; a weak one never does).
     */
    public record Resistance(Hardy<Float> stressFactor, Hardy<Integer> lightLossAge, Ignores ignoresLostLight) {
        /** The age from which a lost light stresses a neutral plant: flowering, as {@code GrowLight#FLOWERING_AGE}. */
        public static final int NEUTRAL_LIGHT_LOSS_AGE = 4;
        public static final Hardy<Float> STRESS_FACTOR = new Hardy<>(2.0F, 1.5F, 0.5F, 0.25F);
        public static final Hardy<Integer> LIGHT_LOSS_AGE = new Hardy<>(3, 3, 4, 4);
        public static final Resistance DEFAULT = new Resistance(STRESS_FACTOR, LIGHT_LOSS_AGE, Ignores.DEFAULT);
        public static final Codec<Resistance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                FileFields.written(Hardy.codec(STRESS_FACTOR, Ranges.STRESS_OVER_ONE, Ranges.UNDER_ONE), "stress_factor",
                        STRESS_FACTOR).forGetter(Resistance::stressFactor),
                FileFields.written(Hardy.codec(LIGHT_LOSS_AGE, Codec.intRange(1, NEUTRAL_LIGHT_LOSS_AGE),
                        Codec.intRange(NEUTRAL_LIGHT_LOSS_AGE, 7)), "light_loss_age", LIGHT_LOSS_AGE)
                        .forGetter(Resistance::lightLossAge),
                FileFields.written(Ignores.CODEC, "ignores_lost_light", Ignores.DEFAULT).forGetter(Resistance::ignoresLostLight)
        ).apply(instance, Resistance::new));
    }

    /** How often a resistant and a very resistant plant shrug a lost light off: 0 to 1. */
    public record Ignores(float resistant, float veryResistant) {
        public static final Ignores DEFAULT = new Ignores(0.5F, 0.75F);
        public static final Codec<Ignores> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                FileFields.written(Ranges.SHARE, "resistant", DEFAULT.resistant).forGetter(Ignores::resistant),
                FileFields.written(Ranges.SHARE, "very_resistant", DEFAULT.veryResistant).forGetter(Ignores::veryResistant)
        ).apply(instance, Ignores::new));
    }

    /** The 2.2 genetics, as the rules had them in code before this file. */
    public static final Genetics BUILT_IN = new Genetics(TierTable.DEFAULT, BandOffsets.DEFAULT, Resin.DEFAULT,
            Resistance.DEFAULT);

    public static final Codec<Genetics> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            FileFields.written(TierTable.CODEC, "tiers", BUILT_IN.tiers).forGetter(Genetics::tiers),
            FileFields.written(BandOffsets.CODEC, "band_offsets", BUILT_IN.bandOffsets).forGetter(Genetics::bandOffsets),
            FileFields.written(Resin.CODEC, "resin", BUILT_IN.resin).forGetter(Genetics::resin),
            FileFields.written(Resistance.CODEC, "resistance", BUILT_IN.resistance).forGetter(Genetics::resistance)
    ).apply(instance, Genetics::new));

    public static void bootstrap(BootstrapContext<Genetics> registerable) {
        registerable.register(DEFAULT, BUILT_IN);
    }

    /** The genetics in this world. */
    public static Genetics of(HolderLookup.Provider registries) {
        return ModBalance.entry(registries, DEFAULT, BUILT_IN);
    }

    /** The genetics in this world, or the built-in ones with no world to ask (nothing is crafting). */
    public static Genetics orBuiltIn(@Nullable HolderLookup.Provider registries) {
        return registries == null ? BUILT_IN : of(registries);
    }

    @Nullable
    private static volatile MinecraftServer running;

    /**
     * Set by the client: the registries of the world it is in when asked on its own thread, else {@code null}. A
     * client in another's world holds that server's genetics only in what the server synced to it, and its own
     * integrated server's registries may be read on that server's thread alone.
     */
    public static Supplier<HolderLookup.Provider> clientRegistries = () -> null;

    /**
     * The genetics of the world the calling thread runs, for the few calls that reach a rule without a world in hand,
     * an addon's call through {@code SmokeContents#effects} among them: on the client's thread the synced ones of the
     * world it is in, on the running server's thread that server's, and the built-in ones anywhere else (another
     * thread, a client in no world, no server running).
     */
    public static Genetics running() {
        HolderLookup.Provider client = clientRegistries.get();
        if (client != null) {
            return of(client);
        }
        MinecraftServer server = running;
        return server != null && server.isSameThread() ? of(server.registryAccess()) : BUILT_IN;
    }

    /** Keeps {@link #running} to the server that is running, from start to stop. */
    static void trackRunningServer() {
        Services.EVENTS.onServerStarted(server -> running = server);
        Services.EVENTS.onServerStopped(server -> running = null);
    }
}
