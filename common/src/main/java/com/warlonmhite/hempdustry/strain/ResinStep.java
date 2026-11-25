package com.warlonmhite.hempdustry.strain;

import com.mojang.serialization.Codec;
import com.warlonmhite.hempdustry.component.ModComponents;
import com.warlonmhite.hempdustry.strain.internal.Resins;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

/**
 * How much resin one plant makes, as one of five steps rolled at planting from its strain's odds
 * ({@link ResinSheet}), very low first: more kief at the Sifting Box and more charas at the rub, <b>never a
 * stronger hash</b>. A property of the strain, never of the place a plant grows. The plant hands its step to its
 * buds in the {@code hempdustry:resin_step} component, and the rub reads it off the living plant. A low step costs
 * on both routes; a rich step's bonus goes where its strain's {@link ResinTexture} sends it.
 *
 * <h2>Absent means what a bud did before resin</h2>
 *
 * A bud with no step sieves exactly as it always did: one level, or two for a bud in
 * {@code #hempdustry:siftable/resinous} (Beldía, grown for the sieve), which is a very rich dry plant's rate.
 * So that is the step such a bud reads ({@link #of}), and nothing is written for it: a new average bud still
 * stacks with an old one, and so does a new very rich Beldía bud.
 */
public enum ResinStep implements StringRepresentable {
    VERY_LOW("very_low", -2),
    LOW("low", -1),
    AVERAGE("average", 0),
    RICH("rich", 1),
    VERY_RICH("very_rich", 2);

    public static final Codec<ResinStep> CODEC = StringRepresentable.fromEnum(ResinStep::values);
    /**
     * Ordinal-based: this only travels over the wire, never to disk. An id out of range (a bad creative packet)
     * reads very low, the first step, rather than throwing.
     */
    public static final StreamCodec<ByteBuf, ResinStep> PACKET_CODEC = ByteBufCodecs.idMapper(
            ByIdMap.continuous(ResinStep::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO), ResinStep::ordinal);

    private final String name;
    private final int offset;

    ResinStep(String name, int offset) {
        this.name = name;
        this.offset = offset;
    }

    /** This step's place around average: very low −2 to very rich +2. What a Stash Jar sums. */
    public int offset() {
        return offset;
    }

    /**
     * The step a bud reads: the one written on it, else the one it sieved as before resin (very rich for a bud in
     * {@code #hempdustry:siftable/resinous}, average for every other; the rule is {@code Resins.legacy}).
     */
    public static ResinStep of(ItemStack stack) {
        ResinStep written = stack.get(ModComponents.RESIN_STEP);
        return written != null ? written : Resins.legacy(stack);
    }

    /** Translation key of the line the player reads, e.g. {@code hempdustry.resin.very_rich}. */
    public String getTranslationKey() {
        return "hempdustry.resin." + name;
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
