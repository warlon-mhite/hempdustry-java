package com.warlonmhite.hempdustry.strain.internal;

import com.warlonmhite.hempdustry.config.EffectPolicy;
import com.warlonmhite.hempdustry.item.custom.SmokeContents;
import com.warlonmhite.hempdustry.item.custom.SmokeContents.Entry;
import com.warlonmhite.hempdustry.strain.BudTier;
import com.warlonmhite.hempdustry.strain.Chemotype;
import com.warlonmhite.hempdustry.strain.EffectLabel;
import com.warlonmhite.hempdustry.strain.ModStrains;
import com.warlonmhite.hempdustry.strain.Potency;
import com.warlonmhite.hempdustry.strain.PotencyBand;
import com.warlonmhite.hempdustry.strain.Strain;
import com.warlonmhite.hempdustry.strain.Strain.SmokeEffect;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * How a bowl is dosed: what each entry of a {@link SmokeContents} weighs in THC and CBD, how long each kind of
 * effect lasts, and what a strain's effects come out at for a load.
 *
 * <p>An entry's THC is weighed in decarboxylated hemp: its chemotype's THC times what it would bake into, the
 * strain's base moved by its band (see {@link Entry#potency}), so a high bud is 5/4 of a mid one. The level rounds
 * down; the exact dose buys time ({@link #thcTicks}). CBD-labelled effects run on their own clock ({@link #cbdTicks}).
 * A tier scales the device's duration for its own entry ({@link Tiers#durationFactor}).
 */
public final class Dosing {
    /** The most a bowl's THC dose can be: a full green-out, and a bong of three THC-dominant buds. */
    public static final int MAX_THC_DOSE = 3;
    /** The most the exact dose may stretch a THC-labelled effect: twice the device's duration. */
    public static final double MAX_THC_STRETCH = 2.0;
    /** Ticks of a CBD-labelled smoke effect per whole unit of CBD smoked: one Regeneration I heal. */
    public static final int CBD_TICKS_PER_UNIT = 50;
    /** The longest a CBD-labelled smoke effect runs, whatever the device: three units' worth. */
    public static final int MAX_CBD_TICKS = 150;

    private Dosing() {
    }

    // --- An entry -------------------------------------------------------------------------------------------

    /** An entry of {@code count} of {@code strain}, stamped {@code stamp}, mid and Dank. */
    public static Entry entry(Holder<Strain> strain, int count, Chemotype stamp) {
        return new Entry(strain, count, stamp, 0, BudTier.DANK);
    }

    /** {@code entry} at {@code tier}. */
    public static Entry withTier(Entry entry, BudTier tier) {
        return new Entry(entry.strain(), entry.count(), entry.stamp(), entry.potency(), tier);
    }

    /** {@code entry} stamped {@code chemotype}, keeping everything else. */
    public static Entry withStamp(Entry entry, Chemotype chemotype) {
        return new Entry(entry.strain(), entry.count(), chemotype, entry.potency(), entry.tier());
    }

    /**
     * {@code entry} with its plant's buds' band offsets summing to {@code potency}; anything that is not a plant's
     * buds has none.
     */
    public static Entry withPotency(Entry entry, int potency) {
        return isPlant(entry)
                ? new Entry(entry.strain(), entry.count(), entry.stamp(), potency, entry.tier())
                : entry;
    }

    /**
     * Whether {@code entry} smokes outside the tiers: schwag, spoiled bud, which is the bottom of the
     * ladder rather than a rung of it, and smokes exactly as it did in 2.1 — no tier scales its effects
     * or the hit's odds.
     */
    public static boolean untiered(Entry entry) {
        return entry.strain().is(ModStrains.SCHWAG);
    }

    private static boolean isPlant(Entry entry) {
        return entry.strain().value().wildFlower().isPresent();
    }

    /**
     * The THC in {@code entry}, in halves of a THC-dominant mid bud's, times {@link Potency#DEFAULT_BASE}:
     * its chemotype's THC times what it is worth in decarboxylated hemp. A plant's buds are worth
     * what they would bake into, the strain's base each moved by its band, never less than one each;
     * anything else the default base per item, so schwag and hash weigh what they always did.
     */
    public static long thcWeight(Entry entry) {
        return worth(entry) * entry.chemotype().thcHalves();
    }

    /**
     * The CBD in {@code entry}, weighed as {@link #thcWeight} weighs its THC: the band moves the CBD
     * too, since a plant's total cannabinoids are polygenic and the chemotype only splits them.
     */
    public static long cbdWeight(Entry entry) {
        return worth(entry) * entry.chemotype().cbdHalves();
    }

    /** What {@code entry} is worth in decarboxylated hemp, the number both weights multiply. */
    private static long worth(Entry entry) {
        Strain value = entry.strain().value();
        int count = entry.count();
        return isPlant(entry)
                ? Math.max(count, (long) count * value.traits().potency().base() + entry.potency())
                : (long) count * Potency.DEFAULT_BASE;
    }

    /**
     * {@code entry}'s THC as an exact dose, unrounded: a mid THC-dominant bud is 1.0, a high one 1.25,
     * a balanced one 0.5. {@link #thcDose(Entry)} rounds it down for the level; the rest buys time.
     */
    public static double thcUnits(Entry entry) {
        return units(thcWeight(entry));
    }

    /** {@code entry}'s CBD as an exact dose, unrounded: a mid CBD-dominant bud is 1.0. */
    public static double cbdUnits(Entry entry) {
        return units(cbdWeight(entry));
    }

    /**
     * {@code entry}'s THC dose: its count weighed by its chemotype and its bands, rounded down, never
     * below one with any THC. For a THC-dominant mid bud it is the count, as before bands existed.
     */
    public static int thcDose(Entry entry) {
        return dose(thcWeight(entry), Integer.MAX_VALUE);
    }

    /** A THC weight as a dose: rounded down, at least one with any THC, at most {@code cap}. */
    private static int dose(long weight, int cap) {
        if (weight <= 0) {
            return 0;
        }
        long dose = weight / ((long) Chemotype.FULL_WEIGHT * Potency.DEFAULT_BASE);
        return (int) Math.min(cap, Math.max(1, dose));
    }

    /** A weight as an exact dose: one is a mid THC-dominant bud's worth of THC (or a CBD bud's of CBD). */
    private static double units(long weight) {
        return weight / (double) ((long) Chemotype.FULL_WEIGHT * Potency.DEFAULT_BASE);
    }

    // --- A load ---------------------------------------------------------------------------------------------

    /** A single-strain load of stamped matter (schwag, scorched hemp, hash); a plant's buds ignore the stamp. */
    public static SmokeContents load(Holder<Strain> strain, int count, Chemotype stamp) {
        return new SmokeContents(List.of(entry(strain, count, stamp)));
    }

    /** A single-strain load whose buds, if a plant's, have band offsets summing to {@code potency}. */
    public static SmokeContents load(Holder<Strain> strain, int count, Chemotype stamp, int potency) {
        return new SmokeContents(List.of(withPotency(entry(strain, count, stamp), potency)));
    }

    /** A single-strain load whose buds, if a plant's, are all of {@code band}. */
    public static SmokeContents load(Holder<Strain> strain, int count, Chemotype stamp, PotencyBand band) {
        return load(strain, count, stamp, count * band.offset());
    }

    /**
     * {@code contents} with its plant entries at {@code budTier} and its other entries at {@code resinTier},
     * each where given. A spliff or a moon rock keeps the two apart: the weakest of its buds, the weakest
     * of its hash.
     */
    public static SmokeContents withTiers(SmokeContents contents, Optional<BudTier> budTier,
                                          Optional<BudTier> resinTier) {
        List<Entry> tiered = new ArrayList<>(contents.entries().size());
        for (Entry entry : contents.entries()) {
            Optional<BudTier> tier = isPlant(entry) ? budTier : resinTier;
            tiered.add(tier.map(t -> withTier(entry, t)).orElse(entry));
        }
        return new SmokeContents(tiered);
    }

    /** {@code contents} with its plant's buds' band offsets summing to {@code potency}. */
    public static SmokeContents withPotency(SmokeContents contents, int potency) {
        List<Entry> banded = new ArrayList<>(contents.entries().size());
        for (Entry entry : contents.entries()) {
            banded.add(withPotency(entry, potency));
        }
        return new SmokeContents(banded);
    }

    /**
     * The dominant entry of {@code contents}, by count. {@code null} only when empty.
     *
     * <p><b>A tie goes to the plant.</b> The dose-2 hash spliff is one bud and one pinch, so the two
     * entries are level and "whichever came first" would decide the item's name, its tint and which
     * one {@link SmokeContents#hashAdditive} reports — from list order alone, which an NBT edit or a re-encode
     * could flip. The plant is the identity in every load that has one, so it wins the tie
     * explicitly rather than by construction.
     */
    public static Entry primaryEntry(SmokeContents contents) {
        Entry best = null;
        for (Entry entry : contents.entries()) {
            if (best == null || entry.count() > best.count()
                    || (entry.count() == best.count() && isPlant(entry) && !isPlant(best))) {
                best = entry;
            }
        }
        return best;
    }

    /**
     * The tier of the primary entry ({@link SmokeContents#primaryStrain}), which sets the odds a hit rolls once for
     * the whole load — its cough, its Nausea and its green-out — as it sets their strain factors. Empty when the
     * load is empty or its primary entry smokes outside the tiers (schwag): the odds are then 2.1's.
     */
    public static Optional<BudTier> primaryTier(SmokeContents contents) {
        Entry best = primaryEntry(contents);
        return best == null || untiered(best) ? Optional.empty() : Optional.of(best.tier());
    }

    /**
     * The THC in {@code contents} as a dose: each entry's units weighed by its chemotype's THC (a balanced
     * bud is half a THC-dominant one) and a bud's by its potency band (a high bud of base 4 bakes into
     * 5 decarboxylated hemp, so it is 5/4 of a mid one), summed and rounded down, <b>never below one
     * when anything in it has THC</b>, and never above {@link #MAX_THC_DOSE}. So a pipe of two
     * extra-high buds is a full green-out, and buds of mixed bands come out at their average. A load with no THC
     * is dose 0: no high, no green-out, only the smoke and the cough.
     */
    public static int thcDose(SmokeContents contents) {
        long weight = 0;
        for (Entry entry : contents.entries()) {
            weight += thcWeight(entry);
        }
        return dose(weight, MAX_THC_DOSE);
    }

    /**
     * How long a THC-labelled effect of an entry lasts: <b>the level rounds down, the exact dose buys
     * time</b>. With {@code units} the entry's exact THC dose D, its level ⌊D⌋ (at least one) and
     * {@code b} that level held to the buff cap, the device's duration stretches by
     * {@code 1 + (D - b) / 2}, at most {@link #MAX_THC_STRETCH} times. A mid bud, a mid pipe of two and
     * a mid bong of three (D = 3, b = 2, one and a half) last exactly what they always did; a high bud
     * lasts an eighth longer, a low one an eighth shorter, and a balanced bong of three, D = 1.5, a
     * quarter longer rather than a half: it has less THC in it. Vanilla's grammar, where glowstone buys
     * level and redstone buys time.
     */
    public static int thcTicks(int durationTicks, double units, int level, int buffCap) {
        int held = Math.min(level, buffCap);
        double stretch = Math.min(MAX_THC_STRETCH, 1.0 + (units - held) / 2.0);
        return (int) Math.round(durationTicks * stretch);
    }

    /**
     * How long a CBD-labelled smoke effect lasts, whatever the device: {@link #CBD_TICKS_PER_UNIT} per
     * unit of the exact CBD dose, at most {@link #MAX_CBD_TICKS}. Level I always. Edibles stay the
     * stronger CBD route (five to twenty seconds of Regeneration), as they last longer in life.
     */
    public static int cbdTicks(double units) {
        return (int) Math.min(MAX_CBD_TICKS, Math.round(CBD_TICKS_PER_UNIT * units));
    }

    /**
     * Every status effect one hit of {@code contents} applies on the hit ({@code onExhale} false) or with the
     * exhale ({@code onExhale} true), before the config's {@code EffectPolicy}. An effect with a {@code chance}
     * below one is rolled on {@code random}, once per call.
     *
     * <p><b>THC-labelled effects: the level rounds down, the exact dose buys time</b>
     * ({@link #thcTicks}). A bong of three mid buds still lasts 75 s instead of 50, and a band moves it:
     * a high bud lasts longer than a mid one, a balanced bong less long than a THC one.
     *
     * <p><b>Strain- and smoke-labelled effects: a bud past the buff cap buys time, not level</b>: each
     * one over {@link EffectPolicy#maxBuffLevel} adds half the device's duration, counted in buds and
     * never in THC, so a band never lengthens Beldía's Invisibility. Counted per entry, so a
     * two-and-one mix, each strain at its own count, has nothing past the cap.
     *
     * <p><b>An effect whose dose buys time</b> ({@link SmokeEffect#doseBuysTime}) takes half the device's
     * duration for every bud past the first instead, counted in buds as above: a bong of three lasts twice
     * the bong.
     *
     * <p><b>CBD-labelled effects</b> run at level I for {@link #cbdTicks} of the entry's CBD, whatever
     * the device and the tier.
     *
     * <p><b>Each entry's tier scales the device's duration for that entry alone</b>
     * ({@link Tiers#durationFactor}): Mids three quarters, Dank as 2.1, Top Shelf a quarter more, Epic
     * half again, applied before the rules above, so the band's stretch and its cap work on the tiered
     * duration.
     *
     * <p><b>A bud loaded with a concentrate goes one level past the cap</b> (see
     * {@link SmokeContents#buffBonus}): its scaling buffs rise by one and may reach {@code maxBuffLevel + 1}.
     * Every other entry, the concentrate's own strain-agnostic effects included, is held to the plain cap here,
     * so the raised cap {@code Smoking} hands the policy lifts only the bud.
     */
    public static List<MobEffectInstance> effects(SmokeContents contents, int durationTicks, boolean onExhale,
                                                  RandomGenerator random) {
        int load = contents.buffBonus();
        List<MobEffectInstance> out = new ArrayList<>();
        for (Entry entry : contents.entries()) {
            int bonus = isPlant(entry) ? load : 0;
            int cap = EffectPolicy.maxBuffLevel(bonus);
            int past = Math.max(0, entry.count() - cap);
            // The entry's tier scales the device's duration for its own effects; times the band's stretch,
            // an extra-high Epic bud lasts three times the device. A CBD effect keeps its own clock.
            int lasting = untiered(entry) ? durationTicks : Tiers.scaleDuration(entry.tier(), durationTicks);
            int thcDose = thcDose(entry);
            Burn burn = new Burn(entry.count(), thcDose, entry.chemotype(),
                    thcTicks(lasting, thcUnits(entry), thcDose, cap),
                    lasting + lasting * past / 2,
                    cbdTicks(cbdUnits(entry)),
                    lasting + lasting * Math.max(0, entry.count() - 1) / 2);
            for (MobEffectInstance effect : effects(entry.strain().value(), burn, onExhale, bonus, random)) {
                out.add(new MobEffectInstance(effect.getEffect(), effect.getDuration(),
                        EffectPolicy.amplifier(effect.getEffect(), effect.getAmplifier(), bonus)));
            }
        }
        return out;
    }

    // --- A strain's effects -----------------------------------------------------------------------------------

    /**
     * One entry of a bowl as its effects read it: how many units burn, the THC dose that sets a THC
     * effect's level, the chemotype, and how long each kind of effect lasts — {@code thcTicks} for a
     * THC-labelled one (the exact dose buys time, {@link #thcTicks}), {@code ticks} for a
     * strain- or smoke-labelled one (a bud past the buff cap buys time), {@code cbdTicks} for a
     * CBD-labelled one ({@link #cbdTicks}), and {@code doseTicks} for a strain- or smoke-labelled one
     * that {@link SmokeEffect#doseBuysTime} (each bud past the first buys half the device's duration).
     */
    public record Burn(int units, int thcDose, Chemotype chemotype, int thcTicks, int ticks, int cbdTicks,
                       int doseTicks) {
        /** As above, for a load where every bud past the first buys no time of its own ({@code doseTicks} = {@code ticks}). */
        public Burn(int units, int thcDose, Chemotype chemotype, int thcTicks, int ticks, int cbdTicks) {
            this(units, thcDose, chemotype, thcTicks, ticks, cbdTicks, ticks);
        }
    }

    /**
     * {@code strain}'s effects for {@code units} of material of {@code chemotype}: each effect reads what its
     * {@link EffectLabel} says moves it. A {@link EffectLabel#THC THC} effect scales with the THC dose
     * ({@link Chemotypes#thcDose}) and is not there at all without THC; a {@link EffectLabel#SMOKE
     * SMOKE} effect scales with the units burned; a {@link EffectLabel#STRAIN STRAIN} effect is flat;
     * a {@link EffectLabel#CBD CBD} effect is flat, lasts by the CBD smoked ({@link #cbdTicks}) and is not there
     * without CBD. For THC-dominant material the THC dose is the unit count, so every effect comes out exactly as it
     * did before chemotypes existed.
     */
    public static List<MobEffectInstance> effects(Strain strain, int units, Chemotype chemotype, int durationTicks,
                                                  boolean onExhale, int buffBonus, RandomGenerator random) {
        return effects(strain, units, Chemotypes.thcDose(chemotype, units), chemotype, durationTicks, onExhale,
                buffBonus, random);
    }

    /**
     * As above, with the THC dose given rather than read off the units: a bowl weighs a bud's THC by
     * its potency band as well as its chemotype ({@link #thcDose(Entry)}), so a pipe of two extra-high buds is a
     * dose of three. Every label lasts {@code durationTicks} but CBD's, which takes the smoked-CBD rule at the
     * units' worth of CBD.
     */
    public static List<MobEffectInstance> effects(Strain strain, int units, int thcDose, Chemotype chemotype,
                                                  int durationTicks, boolean onExhale, int buffBonus,
                                                  RandomGenerator random) {
        return effects(strain, new Burn(units, thcDose, chemotype, durationTicks, durationTicks,
                        cbdTicks(units * (double) chemotype.cbdHalves() / Chemotype.FULL_WEIGHT),
                        durationTicks + durationTicks * Math.max(0, units - 1) / 2),
                onExhale, buffBonus, random);
    }

    /** Fresh status-effect instances for one hit of {@code burn} of {@code strain}, each label at its own level and length. */
    public static List<MobEffectInstance> effects(Strain strain, Burn burn, boolean onExhale, int buffBonus,
                                                  RandomGenerator random) {
        int units = burn.units();
        int thcDose = burn.thcDose();
        Chemotype chemotype = burn.chemotype();
        List<SmokeEffect> smokeEffects = strain.smokeEffects();
        List<MobEffectInstance> out = new ArrayList<>(smokeEffects.size());
        for (SmokeEffect effect : smokeEffects) {
            if (effect.onExhale() != onExhale) {
                continue;
            }
            EffectLabel label = effect.label();
            // Before the chance draw: an effect the chemotype rules out never takes a random number,
            // so a THC-dominant load draws exactly what it always did.
            // Cannabinoid-free material has none of the strain's own effects either, a hash's or a
            // scorched bud's: only what any smoke does (schwag's Poison) is left.
            if ((label == EffectLabel.THC && thcDose == 0) || (label == EffectLabel.CBD && chemotype.cbdHalves() == 0)
                    || (label == EffectLabel.STRAIN && chemotype == Chemotype.CANNABINOID_FREE)) {
                continue;
            }
            // A certain effect draws nothing, so every strain written before chance existed takes
            // exactly the random numbers it always did.
            if (effect.chance() < 1.0F && random.nextFloat() >= effect.chance()) {
                continue;
            }
            int level = switch (label) {
                case THC -> thcDose;
                case SMOKE -> units;
                case STRAIN, CBD -> 1;
            };
            int amplifier = effect.baseAmplifier() + Math.max(0, level - 1);
            if (label.scales() && effect.effect().value().getCategory() == MobEffectCategory.BENEFICIAL) {
                amplifier += buffBonus;
            }
            amplifier = Math.min(effect.maxAmplifier(), amplifier);
            int ticks = switch (label) {
                case THC -> burn.thcTicks();
                case CBD -> burn.cbdTicks();
                case STRAIN, SMOKE -> effect.doseBuysTime() ? burn.doseTicks() : burn.ticks();
            };
            // At least a tick: a datapack's 0 or negative factor is a very short effect, never one
            // that silently does not exist.
            int duration = Math.max(1, Math.round(ticks * effect.durationFactor()));
            out.add(new MobEffectInstance(effect.effect(), duration, amplifier));
        }
        return out;
    }
}
