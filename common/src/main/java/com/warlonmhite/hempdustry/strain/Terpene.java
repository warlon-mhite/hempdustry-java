package com.warlonmhite.hempdustry.strain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.warlonmhite.hempdustry.Hempdustry;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * A terpene: the smell a strain is known for, and the colour its smoke takes. A datapack registry,
 * {@code data/<namespace>/hempdustry/terpene/<id>.json}, synced to clients like the strains, so a pack
 * may add one or recolour one.
 *
 * <h2>A signature, not the dominant one</h2>
 *
 * A strain names one terpene: the note its smell is known for. Lab tests find only myrcene,
 * caryophyllene, limonene, terpinolene and sometimes pinene really dominating a profile, so most
 * strains' signature is not their most abundant terpene, and that is how growers describe them too.
 *
 * <h2>No effect</h2>
 *
 * The entourage effect is unproven, so a terpene changes nothing a strain does: it is a smell, a word
 * on the tooltip and a colour in the smoke. The colour follows the smell (citrus yellow, lavender
 * violet, pine green), never strength.
 *
 * @param translationKey lang key of its name, e.g. {@code hempdustry.terpene.limonene}
 * @param color          its colour, RGB; the smoke mixes a third of it into its grey, vapour a quarter
 *                       into its white
 */
public record Terpene(String translationKey, int color) {

    public static final ResourceKey<Registry<Terpene>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "terpene"));

    public static final Codec<Terpene> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("translation_key").forGetter(Terpene::translationKey),
            Codec.INT.fieldOf("color").forGetter(Terpene::color)
    ).apply(instance, Terpene::new));

    public static final ResourceKey<Terpene> LIMONENE = key("limonene");
    public static final ResourceKey<Terpene> LINALOOL = key("linalool");
    public static final ResourceKey<Terpene> MYRCENE = key("myrcene");
    public static final ResourceKey<Terpene> CARYOPHYLLENE = key("caryophyllene");
    public static final ResourceKey<Terpene> PINENE = key("pinene");
    public static final ResourceKey<Terpene> EUCALYPTOL = key("eucalyptol");
    public static final ResourceKey<Terpene> TERPINOLENE = key("terpinolene");
    public static final ResourceKey<Terpene> GERANIOL = key("geraniol");
    /**
     * Hops' terpene, the fibre family's: Industrial Hemp and Ditchweed, its feral descendant, which are
     * no strain. No smoking strain names it.
     */
    public static final ResourceKey<Terpene> HUMULENE = key("humulene");
    /** A spare, woody and floral, for a strain to come. No 2.2 strain names it. */
    public static final ResourceKey<Terpene> NEROLIDOL = key("nerolidol");
    /**
     * Camphor's terpene, cold, earthy and a little minty: the Ancient City's ice box. Echo OG's, and the
     * eleventh.
     */
    public static final ResourceKey<Terpene> BORNEOL = key("borneol");
    /**
     * Sweet, tropical and green, a smell of basil and mango: the ocean strain's, Maui Wowie's, and the
     * twelfth. Its colour is the sea's, not its smell's: every green band was taken.
     */
    public static final ResourceKey<Terpene> OCIMENE = key("ocimene");

    private static ResourceKey<Terpene> key(String name) {
        return ResourceKey.create(REGISTRY_KEY, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, name));
    }
}
