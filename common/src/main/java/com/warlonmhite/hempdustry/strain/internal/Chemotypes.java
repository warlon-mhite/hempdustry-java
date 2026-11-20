package com.warlonmhite.hempdustry.strain.internal;

import com.warlonmhite.hempdustry.block.ModBlocks;
import com.warlonmhite.hempdustry.block.custom.IndustrialHempCropBlock;
import com.warlonmhite.hempdustry.component.ModComponents;
import com.warlonmhite.hempdustry.strain.Chemotype;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** What a {@link Chemotype} does at the oven, the Infuser and the bowl, and how a batch of mixed matter reads. */
public final class Chemotypes {
    private Chemotypes() {
    }

    /**
     * Whether matter of {@code chemotype} can become an edible. The two chemotypes that make neither THC nor CBD are
     * turned away at the oven and the Infuser, the two doors into one; everywhere else they work.
     */
    public static boolean infusable(Chemotype chemotype) {
        return chemotype.thcHalves() > 0 || chemotype.cbdHalves() > 0;
    }

    /**
     * The THC dose of {@code units} of {@code chemotype}: a whole number of THC-dominant units' worth, rounded down,
     * but never below one when there is any THC in it at all. So a balanced bud is a dose of one, as is a pipe of
     * two, and a bong of three balanced buds is still one.
     */
    public static int thcDose(Chemotype chemotype, int units) {
        if (units <= 0 || chemotype.thcHalves() == 0) {
            return 0;
        }
        return Math.max(1, units * chemotype.thcHalves() / Chemotype.FULL_WEIGHT);
    }

    /**
     * What a growing plant makes: its strain's chemotype, the strain found by the crop its seeds
     * plant or by its wild flower. Industrial Hemp has no strain record and is CBD-dominant, as real
     * fibre hemp is; Ditchweed, its feral descendant, is balanced, as its drops are; anything else
     * unknown reads THC-dominant.
     */
    public static Chemotype ofPlant(Block block, HolderLookup.Provider registries) {
        if (block instanceof IndustrialHempCropBlock) {
            return Chemotype.CBD_DOMINANT;
        }
        if (block == ModBlocks.DITCHWEED) {
            return Chemotype.BALANCED;
        }
        return Strains.ofPlant(block, registries).map(strain -> strain.value().traits().chemotype())
                .orElse(Chemotype.THC_DOMINANT);
    }

    /**
     * Writes {@code chemotype} on {@code stack}; THC-dominant writes nothing, so the stack still merges
     * with everything 2.1 made. Returns the stack. See {@link ModComponents#putDefaulted}.
     */
    public static ItemStack stamp(ItemStack stack, Chemotype chemotype) {
        return ModComponents.putDefaulted(stack, ModComponents.CHEMOTYPE, chemotype, Chemotype.THC_DOMINANT);
    }

    /**
     * The chemotype of a batch: what an Infuser, a sieve or a bhang makes of mixed material.
     *
     * <p>The batch's share of THC among its cannabinoids decides: two thirds or more is THC-dominant,
     * a third or less CBD-dominant, anything between balanced — so four THC-dominant hemp and four
     * CBD-dominant make a balanced batch, as a dosage would. A batch of one chemotype is that
     * chemotype, the two that make neither included; material with neither cannabinoid only dilutes
     * a mixed batch, and a mix of nothing but those two is CBG-dominant, since only the cannabinoid-free
     * one has nothing at all.
     *
     * @param counts how many units of each chemotype, indexed by {@link Chemotype#ordinal()}
     */
    public static Chemotype dosage(int[] counts) {
        Chemotype only = null;
        int kinds = 0;
        long thc = 0;
        long cbd = 0;
        for (Chemotype chemotype : Chemotype.values()) {
            int count = counts[chemotype.ordinal()];
            if (count <= 0) {
                continue;
            }
            only = chemotype;
            kinds++;
            thc += (long) count * chemotype.thcHalves();
            cbd += (long) count * chemotype.cbdHalves();
        }
        if (kinds == 0) {
            return Chemotype.THC_DOMINANT;
        }
        if (kinds == 1) {
            return only;
        }
        return thc + cbd == 0 ? Chemotype.CBG_DOMINANT : ofShares(thc, cbd);
    }

    /**
     * The chemotype of material carrying {@code thc} and {@code cbd} (any unit, the same for both),
     * by the dosage rule: two thirds THC or more is THC-dominant, a third or less CBD-dominant,
     * between balanced. Nothing at all reads THC-dominant, the absent default.
     */
    public static Chemotype ofShares(long thc, long cbd) {
        long total = thc + cbd;
        if (total <= 0) {
            return Chemotype.THC_DOMINANT;
        }
        if (3 * thc >= 2 * total) {
            return Chemotype.THC_DOMINANT;
        }
        return 3 * thc <= total ? Chemotype.CBD_DOMINANT : Chemotype.BALANCED;
    }

    /**
     * Marks a cannabutter with what its batch was: its chemotype word, and its CBD sum beside the THC
     * sum its {@code strength} already is. THC-dominant and no CBD write nothing, so butter made the
     * 2.1 way is still the stack 2.1 made.
     */
    public static ItemStack stampButter(ItemStack butter, Chemotype chemotype, int cbdStrength) {
        stamp(butter, chemotype);
        if (cbdStrength > 0) {
            butter.set(ModComponents.CBD_STRENGTH, cbdStrength);
        } else {
            butter.remove(ModComponents.CBD_STRENGTH);
        }
        return butter;
    }

    /** The grey tooltip line. */
    public static Component tooltipLine(Chemotype chemotype) {
        return Component.translatable(chemotype.getTranslationKey()).withStyle(ChatFormatting.GRAY);
    }
}
