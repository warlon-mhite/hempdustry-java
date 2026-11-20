package com.warlonmhite.hempdustry.strain;

import com.mojang.serialization.Codec;
import com.warlonmhite.hempdustry.component.ModComponents;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

/**
 * What a plant makes: THC, CBD, both, or neither. The five chemotypes are the real ones, named the
 * way the literature names them (de Meijer et al. 2003 and 2009), and never numbered in front of the
 * player.
 *
 * <h2>Mostly one gene</h2>
 *
 * Chemotype is the B locus: THCA synthase and CBDA synthase, co-dominant. Two THC copies make a
 * THC-dominant plant, two CBD copies a CBD-dominant one, one of each a balanced one. CBG-dominant
 * plants make neither enzyme's product (the French fibre cultivar Santhica 27), and cannabinoid-free
 * lines were bred out of fibre hemp. So a strain has one chemotype, and it is the strain's, never
 * rolled per plant.
 *
 * <h2>The weights</h2>
 *
 * How much THC and CBD a unit of the material carries, counted in halves so that every sum stays a
 * whole number: THC-dominant is 2 halves of THC, balanced 1 of each, CBD-dominant 2 of CBD. The two
 * that make neither weigh nothing in both, which is why the oven and the Infuser refuse them: they
 * would make an edible that does nothing.
 *
 * <h2>Absent means THC-dominant</h2>
 *
 * Everything 2.1 made — every leaf, schwag, decarboxylated hemp and hash in every world — was THC by
 * definition, so an item with no {@code hempdustry:chemotype} component reads THC-dominant, and
 * that value is never written. A new THC-dominant leaf therefore still stacks with an old
 * one: only the chemotypes 2.1 never had cost a slot.
 *
 * <p>The stamped items carry THC-dominant on their prototype as well, the way vanilla's potions carry
 * an empty {@code potion_contents}: a stack that names the default explicitly (a {@code /give}, a
 * datapack's {@code set_components}, another mod's copy) then loses it on the way in, as vanilla drops
 * any value equal to the prototype's, and still stacks with a plain one.
 */
public enum Chemotype implements StringRepresentable {
    THC_DOMINANT("thc_dominant", 2, 0),
    BALANCED("balanced", 1, 1),
    CBD_DOMINANT("cbd_dominant", 0, 2),
    CBG_DOMINANT("cbg_dominant", 0, 0),
    CANNABINOID_FREE("cannabinoid_free", 0, 0);

    /** Halves of THC or CBD in one unit of a material of the strongest chemotype. */
    public static final int FULL_WEIGHT = 2;

    public static final Codec<Chemotype> CODEC = StringRepresentable.fromEnum(Chemotype::values);
    /**
     * Ordinal-based: this only travels over the wire, never to disk. An id out of range (a bad
     * creative packet) reads THC-dominant, the absent default, rather than throwing.
     */
    public static final StreamCodec<ByteBuf, Chemotype> PACKET_CODEC = ByteBufCodecs.idMapper(
            ByIdMap.continuous(Chemotype::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO), Chemotype::ordinal);

    private final String name;
    private final int thcHalves;
    private final int cbdHalves;

    Chemotype(String name, int thcHalves, int cbdHalves) {
        this.name = name;
        this.thcHalves = thcHalves;
        this.cbdHalves = cbdHalves;
    }

    /** THC in one unit, in halves: 2, 1 or 0. */
    public int thcHalves() {
        return thcHalves;
    }

    /** CBD in one unit, in halves: 0, 1 or 2. */
    public int cbdHalves() {
        return cbdHalves;
    }

    /** Whether it carries any THC at all. */
    public boolean hasThc() {
        return thcHalves > 0;
    }

    /** The item's stamp, or THC-dominant when it carries none. */
    public static Chemotype of(ItemStack stack) {
        return stack.getOrDefault(ModComponents.CHEMOTYPE, THC_DOMINANT);
    }

    /**
     * What a piece of hemp matter is, whatever it is: a plant's bud is its strain's chemotype (a bud
     * never carries a stamp), anything else its stamp. This is the question every machine asks of
     * what goes into it.
     */
    public static Chemotype ofMatter(ItemStack stack, HolderLookup.Provider registries) {
        // A bud's item carries no chemotype, not even on its prototype: its strain answers.
        if (!stack.has(ModComponents.CHEMOTYPE)) {
            Optional<Holder.Reference<Strain>> strain = Strain.fromBuds(registries, stack.getItem());
            if (strain.isPresent() && strain.get().value().wildFlower().isPresent()) {
                return strain.get().value().traits().chemotype();
            }
        }
        return of(stack);
    }

    /** Translation key of the word the player reads, e.g. {@code hempdustry.chemotype.balanced}. */
    public String getTranslationKey() {
        return "hempdustry.chemotype." + name;
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
