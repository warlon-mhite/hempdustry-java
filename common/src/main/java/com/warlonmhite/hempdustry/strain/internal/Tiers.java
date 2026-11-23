package com.warlonmhite.hempdustry.strain.internal;

import com.warlonmhite.hempdustry.balance.Genetics;
import com.warlonmhite.hempdustry.component.ModComponents;
import com.warlonmhite.hempdustry.strain.BudTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * What a {@link BudTier} does: how a harvest rolls one, and what each buys when smoked.
 *
 * <h2>One roll per harvest, in vanilla's loot-luck grammar</h2>
 *
 * Every bud of a harvest, Fortune's extras included, takes one tier drawn from its plant's grow luck
 * {@code L}: each tier weighs {@code max(⌊w + q × L⌋, 0)}, the formula vanilla's loot entries weigh a
 * pool by ({@code UniformContainerBase$EntryBase#getWeight}), with Mids 60 / −10, Dank 35 / +4, Top
 * Shelf 0 / +6 and Epic −6 / +2. So Mids fades as luck grows, Top Shelf appears from L 1 and Epic only
 * from L 4: genetics, care and setup together, never one act. A stressed or mildewed plant does not
 * roll: it gives Mids.
 *
 * <p>Those are the built-in numbers, and so are the factors below: every one of them is read from the world's
 * {@code genetics/default.json} ({@link Genetics.TierTable}), whose bounds keep a tier from reaching a level.
 *
 * <h2>What a tier buys when smoked</h2>
 *
 * Smoothness and length, never level: a tier scales how long its own entry's effects last
 * ({@link #durationFactor}) and the odds of the hit's cough, Nausea and green-out
 * ({@link #coughFactor}, {@link #nauseaFactor}, {@link #greenOutFactor}). Nothing in butter: the oven
 * boils off what a tier buys.
 *
 * <h2>Absent means Dank</h2>
 *
 * {@link #stamp} never writes Dank, so a new Dank bud stacks with an old one. A tier's name colour is vanilla's
 * {@code rarity}: Top Shelf rare (aqua), Epic epic (light purple), Mids and Dank the item's own.
 */
public final class Tiers {
    private Tiers() {
    }

    /**
     * {@code tier}'s weight at grow luck {@code luck}: vanilla's {@code max(⌊weight + quality × luck⌋, 0)}, with the
     * weight and quality {@code genetics} gives it.
     */
    public static int weightAt(Genetics genetics, BudTier tier, float luck) {
        Genetics.Tier numbers = genetics.tiers().of(tier);
        return Math.max(Mth.floor((float) numbers.weight() + (float) numbers.quality() * luck), 0);
    }

    /**
     * One harvest's tier at grow luck {@code luck}: exactly one draw on {@code random}, whatever it
     * decides, so the caller always knows how many it took.
     */
    public static BudTier roll(Genetics genetics, float luck, RandomSource random) {
        int total = 0;
        for (BudTier tier : BudTier.values()) {
            total += weightAt(genetics, tier, luck);
        }
        // Mids gains weight as luck falls and Dank as it rises, so the built-in total is never zero; a file that
        // weighs every tier at nothing still takes its one draw, and gives Mids.
        int pick = random.nextInt(Math.max(1, total));
        for (BudTier tier : BudTier.values()) {
            pick -= weightAt(genetics, tier, luck);
            if (pick < 0) {
                return tier;
            }
        }
        return BudTier.MIDS;
    }

    /** The tier of rank {@code rank} ({@link BudTier#rank}), held to the four. */
    public static BudTier ofRank(int rank) {
        BudTier[] tiers = BudTier.values();
        return tiers[Mth.clamp(rank, 0, tiers.length - 1)];
    }

    /** The weaker of {@code a} and {@code b}. */
    public static BudTier weaker(BudTier a, BudTier b) {
        return a.rank() <= b.rank() ? a : b;
    }

    /** What {@code tier} multiplies the device's cough chance by: built in, Mids half again, Epic none. */
    public static float coughFactor(Genetics genetics, BudTier tier) {
        return genetics.tiers().of(tier).cough();
    }

    /** What {@code tier} multiplies the device's duration by, for its own entry's effects: built in, ×0.75 to ×1.5. */
    public static float durationFactor(Genetics genetics, BudTier tier) {
        return genetics.tiers().of(tier).duration();
    }

    /** What {@code tier} multiplies the device's Nausea chance by: built in, Mids as 2.1, Epic none. */
    public static float nauseaFactor(Genetics genetics, BudTier tier) {
        return genetics.tiers().of(tier).nausea();
    }

    /** What {@code tier} multiplies the green-out chance by: built in, Mids as 2.1, Epic a quarter. */
    public static float greenOutFactor(Genetics genetics, BudTier tier) {
        return genetics.tiers().of(tier).greenOut();
    }

    /** {@code ticks} lasting as long as {@code tier} makes them. */
    public static int scaleDuration(Genetics genetics, BudTier tier, int ticks) {
        return Math.round(ticks * durationFactor(genetics, tier));
    }

    /**
     * A one-in-{@code oneIn} chance multiplied by {@code chanceFactor}, as a one-in-N again: zero (never)
     * stays zero, and so does any chance multiplied by zero.
     */
    public static int scaleOneIn(int oneIn, float chanceFactor) {
        if (oneIn <= 0 || chanceFactor <= 0.0F) {
            return 0;
        }
        return Math.max(1, Math.round(oneIn / chanceFactor));
    }

    /**
     * Writes {@code tier} on {@code stack} with its rarity; Dank writes nothing, and Mids and Dank
     * leave the name the item's own colour. Returns the stack. See {@link ModComponents#putDefaulted}.
     */
    public static ItemStack stamp(ItemStack stack, BudTier tier) {
        ModComponents.putDefaulted(stack, ModComponents.BUD_TIER, tier, BudTier.DANK);
        return stampRarity(stack, tier);
    }

    /** Names {@code stack} in {@code tier}'s rarity: Top Shelf rare, Epic epic, Mids and Dank the item's own. */
    public static ItemStack stampRarity(ItemStack stack, BudTier tier) {
        Rarity own = stack.getPrototype().getOrDefault(DataComponents.RARITY, Rarity.COMMON);
        Rarity rarity = switch (tier) {
            case MIDS, DANK -> own;
            case TOP_SHELF -> Rarity.RARE;
            case EPIC -> Rarity.EPIC;
        };
        ModComponents.putDefaulted(stack, DataComponents.RARITY, rarity, own);
        return stack;
    }

    /** The tooltip line, grey whatever the tier: the colour is the name's. */
    public static Component tooltipLine(BudTier tier) {
        return Component.translatable(tier.getTranslationKey()).withStyle(ChatFormatting.GRAY);
    }
}
