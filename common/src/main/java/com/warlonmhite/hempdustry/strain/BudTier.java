package com.warlonmhite.hempdustry.strain;

import com.mojang.serialization.Codec;
import com.warlonmhite.hempdustry.component.ModComponents;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

/**
 * How well a harvest was grown, as one of four tiers above schwag: Mids, Dank, Top Shelf and Epic, weakest
 * first. A harvest rolls one from its plant's grow luck, and a tier buys smoothness and length when smoked, never
 * level. Carried by buds and the sifted hash family in the {@code hempdustry:bud_tier} component.
 *
 * <p><b>Absent means Dank</b>: every bud and every piece of hash 2.1 made reads Dank, and Dank is never written, so a
 * new Dank bud stacks with an old one.
 */
public enum BudTier implements StringRepresentable {
    MIDS("mids"),
    DANK("dank"),
    TOP_SHELF("top_shelf"),
    EPIC("epic");

    public static final Codec<BudTier> CODEC = StringRepresentable.fromEnum(BudTier::values);
    /**
     * Ordinal-based: this only travels over the wire, never to disk. An id out of range (a bad
     * creative packet) reads Mids, the first tier, rather than throwing.
     */
    public static final StreamCodec<ByteBuf, BudTier> PACKET_CODEC = ByteBufCodecs.idMapper(
            ByIdMap.continuous(BudTier::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO), BudTier::ordinal);

    private final String name;

    BudTier(String name) {
        this.name = name;
    }

    /** How a tier ranks: Mids 0, Dank 1, Top Shelf 2, Epic 3. */
    public int rank() {
        return ordinal();
    }

    /** The tier an item carries, Dank when it carries none. */
    public static BudTier of(ItemStack stack) {
        return stack.getOrDefault(ModComponents.BUD_TIER, DANK);
    }

    /** Translation key of the line the player reads, e.g. {@code hempdustry.tier.top_shelf}. */
    public String getTranslationKey() {
        return "hempdustry.tier." + name;
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
