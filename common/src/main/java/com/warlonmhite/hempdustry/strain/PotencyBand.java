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
 * How much THC a plant makes, as one of four bands: low, mid, high and extra-high. Potency is
 * polygenic and swings with how a plant was grown, so it is not the strain's but each plant's,
 * rolled from the strain's odds ({@link Potency}); the plant hands it to its buds.
 *
 * <h2>A count where a machine makes one, a mark on what is used raw</h2>
 *
 * The band moves the decarboxylated hemp a bud bakes into, {@link #offset} on the recipe's count, and
 * so the THC dose of a bowl, which is weighed in decarboxylated hemp. Nothing after the oven carries a
 * band: decarboxylated hemp is the count already paid. A leaf from a high or extra-high plant carries
 * one mark, {@link #HIGH}, worth a leaf more at the oven.
 *
 * <h2>Absent means mid</h2>
 *
 * Every bud and leaf 2.1 made reads mid, which bakes and smokes exactly as 2.1 did, and mid is never
 * written: a new mid bud still stacks with an old one. Buds and the leaf carry mid on their prototype too,
 * so a stack naming it explicitly loses it and stacks with a plain one.
 */
public enum PotencyBand implements StringRepresentable {
    LOW("low", -1),
    MID("mid", 0),
    HIGH("high", 1),
    EXTRA_HIGH("extra_high", 2);

    public static final Codec<PotencyBand> CODEC = StringRepresentable.fromEnum(PotencyBand::values);
    /**
     * Ordinal-based: this only travels over the wire, never to disk. An id out of range (a bad
     * creative packet) reads low, the first band, rather than throwing.
     */
    public static final StreamCodec<ByteBuf, PotencyBand> PACKET_CODEC = ByteBufCodecs.idMapper(
            ByIdMap.continuous(PotencyBand::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO), PotencyBand::ordinal);

    private final String name;
    private final int offset;

    PotencyBand(String name, int offset) {
        this.name = name;
        this.offset = offset;
    }

    /**
     * This band's place around mid: low −1 to extra-high +2. The decarboxylated hemp it adds to (or takes from)
     * what a mid piece bakes into, and what a load's {@code band_offset} sums.
     */
    public int offset() {
        return offset;
    }

    /** The band an item carries, mid when it carries none. */
    public static PotencyBand of(ItemStack stack) {
        return stack.getOrDefault(ModComponents.POTENCY_BAND, MID);
    }

    /** Translation key of the line the player reads, e.g. {@code hempdustry.potency.high}. */
    public String getTranslationKey() {
        return "hempdustry.potency." + name;
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
