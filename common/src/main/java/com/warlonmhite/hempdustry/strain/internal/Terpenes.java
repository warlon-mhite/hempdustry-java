package com.warlonmhite.hempdustry.strain.internal;

import com.warlonmhite.hempdustry.block.ModBlocks;
import com.warlonmhite.hempdustry.block.custom.IndustrialHempCropBlock;
import com.warlonmhite.hempdustry.strain.Strain;
import com.warlonmhite.hempdustry.strain.Terpene;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;

/** The mod's own terpenes, and how a terpene's colour reaches smoke, vapour, a plant's aroma and a tooltip. */
public final class Terpenes {
    /** The share of a terpene's colour that goes into smoke, and into vapour. */
    public static final float SMOKE_SHARE = 0.33F;
    public static final float VAPOUR_SHARE = 0.25F;

    private Terpenes() {
    }

    /**
     * The twelve, each coloured from its smell: limonene the lemon, linalool lavender, myrcene ripe mango,
     * caryophyllene black pepper's warm red, pinene the pine needle, eucalyptol the cool blue of
     * eucalyptus, terpinolene green apple and lilac's fresh green, geraniol rose pink, humulene the
     * dried hop, nerolidol bark, borneol the cold of an ice box: vanilla sculk's own deep teal, darker
     * than eucalyptol's blue. Ocimene alone is coloured by place, not by smell: the open sea's blue, the one
     * band no other terpene was near, where its sweet green smell would have read as pinene's in the smoke.
     */
    public static void bootstrap(BootstrapContext<Terpene> context) {
        register(context, Terpene.LIMONENE, 0xF5D547);
        register(context, Terpene.LINALOOL, 0xB9A0F2);
        register(context, Terpene.MYRCENE, 0xE8923A);
        register(context, Terpene.CARYOPHYLLENE, 0xD0603F);
        register(context, Terpene.PINENE, 0x6DB360);
        register(context, Terpene.EUCALYPTOL, 0x3FA7C4);
        register(context, Terpene.TERPINOLENE, 0xA7D84E);
        register(context, Terpene.GERANIOL, 0xF592B8);
        register(context, Terpene.HUMULENE, 0xB3AE5C);
        register(context, Terpene.NEROLIDOL, 0xB0835A);
        register(context, Terpene.BORNEOL, 0x009295);
        register(context, Terpene.OCIMENE, 0x4A7BD8);
    }

    private static void register(BootstrapContext<Terpene> context, ResourceKey<Terpene> key, int color) {
        context.register(key, new Terpene("hempdustry.terpene." + key.identifier().getPath(), color));
    }

    /** The terpene the world loaded under {@code key}, if it defines one. */
    public static Optional<Terpene> resolve(HolderLookup.Provider registries, ResourceKey<Terpene> key) {
        return registries.lookup(Terpene.REGISTRY_KEY).flatMap(lookup -> lookup.get(key)).map(Holder::value);
    }

    /** The terpene the world loaded under {@code strain}'s terpene key, if both exist. */
    public static Optional<Terpene> of(Strain strain, HolderLookup.Provider registries) {
        return strain.traits().terpene().flatMap(key -> resolve(registries, key));
    }

    /** The colour {@code strain}'s smoke takes: its own smoke colour, else its terpene's, else none (plain smoke). */
    public static Optional<Integer> smokeTint(Strain strain, HolderLookup.Provider registries) {
        return strain.traits().smokeColor().or(() -> of(strain, registries).map(Terpene::color));
    }

    /**
     * The terpene a plant of {@code block} smells of: its strain's, or humulene for the fibre family
     * (Industrial Hemp and Ditchweed), which flowers and smells like any hemp but is grown for stalk and
     * seed, so it has no strain record. Empty for anything else, or a terpene the world does not define.
     */
    public static Optional<Terpene> ofPlant(Block block, HolderLookup.Provider registries) {
        Optional<Holder.Reference<Strain>> strain = Strains.ofPlant(block, registries);
        if (strain.isPresent()) {
            return of(strain.get().value(), registries);
        }
        return isFibreHemp(block) ? resolve(registries, Terpene.HUMULENE) : Optional.empty();
    }

    private static boolean isFibreHemp(Block block) {
        return block instanceof IndustrialHempCropBlock || block == ModBlocks.DITCHWEED;
    }

    /** The tooltip line: its name, in its own colour. */
    public static Component tooltipLine(Terpene terpene) {
        return Component.translatable(terpene.translationKey())
                .withStyle(style -> style.withColor(TextColor.fromRgb(terpene.color())));
    }

    /** {@code color} mixed {@code share} of the way into {@code base}, both RGB. */
    public static int mix(int base, int color, float share) {
        int r = Math.round(((base >> 16) & 0xFF) * (1 - share) + ((color >> 16) & 0xFF) * share);
        int g = Math.round(((base >> 8) & 0xFF) * (1 - share) + ((color >> 8) & 0xFF) * share);
        int b = Math.round((base & 0xFF) * (1 - share) + (color & 0xFF) * share);
        return (r << 16) | (g << 8) | b;
    }
}
