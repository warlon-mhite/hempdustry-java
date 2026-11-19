package com.warlonmhite.hempdustry.block.custom.stash;

import com.warlonmhite.hempdustry.component.StashContents;
import com.warlonmhite.hempdustry.item.ModItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * How a Stash Jar smells when it is opened: from a sour reek to an epic aroma, read off the whole jar,
 * never off the stack that comes out. Cosmetic only: it picks the terpene puff a take-out lets off and
 * moves nothing else.
 *
 * <ol>
 *   <li>The jar's nose is the mean over every item inside of Mids 1, Dank 2, Top Shelf 3, Epic 4 and
 *       schwag −4: spoiled bud drags a whole stash down.</li>
 *   <li>Minus one for every other kind in the jar (another strain, a schwag chemotype, a stack kept
 *       exact): a jar smells best holding one thing.</li>
 *   <li>Rounded down, at most {@link #EPIC}: the epic aroma needs a perfect jar, all Epic and one
 *       strain.</li>
 *   <li>Below {@link #SOFT} the jar has gone stale; a stale jar holding schwag reeks ({@link #ROTTEN}).</li>
 * </ol>
 *
 * So a jar is rewarded for consistency within the ceiling its quality sets: a pure Top Shelf jar (3)
 * beats an Epic one spoiled by schwag or shared with another strain (2), and a pure Mids jar never
 * passes {@link #SOFT}. The mean comes from the sums every entry keeps, so blending, the order things
 * went in and taking out and putting back never change it, and nothing new is stored.
 */
public enum StashNose {
    /** Stale and holding schwag: a sour, heavy reek that sinks off the rim, and flies. */
    ROTTEN,
    /** Stale, with no schwag to blame: one faint wisp of stale hay. */
    STALE,
    /** A soft smell: one faint wisp. */
    SOFT,
    /** A decent odour: three wisps. */
    DECENT,
    /** A strong nose: six wisps in a slow spiral, a little higher. */
    STRONG,
    /** An epic aroma: the lid pops, a double helix rises high and lingers, trichomes glint. */
    EPIC;

    public static final StreamCodec<ByteBuf, StashNose> STREAM_CODEC = ByteBufCodecs.idMapper(
            id -> values()[Math.floorMod(id, values().length)], StashNose::ordinal);

    /** What schwag counts for in the mean: spoiled, as far below Mids as Epic is above nothing. */
    public static final int SCHWAG_SCORE = -4;

    /** The nose level, 1 for {@link #SOFT} to 4 for {@link #EPIC}; 0 for a stale jar. */
    public int level() {
        return Math.max(0, ordinal() - 1);
    }

    /** How a jar holding {@code contents} smells as it is opened; an empty jar smells of nothing, so stale. */
    public static StashNose of(StashContents contents) {
        if (contents.isEmpty()) {
            return STALE;
        }
        long score = 0;
        boolean schwag = false;
        for (StashContents.Entry entry : contents.entries()) {
            if (entry.item().typeHolder().value() == ModItems.SCHWAG) {
                schwag = true;
                score += (long) SCHWAG_SCORE * entry.count();
            } else {
                // Ranks run Mids 0 to Epic 3; the nose counts them 1 to 4, so a Mids bud still smells.
                score += entry.tierSum() + (long) entry.count();
            }
        }
        long count = contents.count();
        long others = contents.size() - 1L;
        long level = Math.floorDiv(score - others * count, count);
        if (level < 1) {
            return schwag ? ROTTEN : STALE;
        }
        return values()[(int) Math.min(level, 4) + 1];
    }
}
