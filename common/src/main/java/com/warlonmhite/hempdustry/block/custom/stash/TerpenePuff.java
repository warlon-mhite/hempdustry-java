package com.warlonmhite.hempdustry.block.custom.stash;

import com.warlonmhite.hempdustry.strain.internal.Terpenes;
import com.warlonmhite.hempdustry.item.ModItems;
import com.warlonmhite.hempdustry.particle.TerpenePuffOption;
import com.warlonmhite.hempdustry.strain.Strain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * The smell let out when an empty hand opens a placed Stash Jar: one emitter particle sent to everyone
 * near enough to see particles (vanilla's 32 blocks), the opener too, so a whole room sees a good stash
 * opened. How strong is the whole jar's ({@link StashNose}, read before the take-out); the colour is the
 * smoke colour of what came out. Hoppers, pipes and the inventory let off nothing: no hand opened a
 * jar standing in the world. Cosmetic only. Only an epic jar makes a sound of its own, vanilla's amethyst chime
 * over the take-out's bundle sound, heard by everyone near: the best stash is the one that rings.
 */
public final class TerpenePuff {
    /** Stale hay: schwag's smoke, and the colour of any jar gone stale whatever came out. */
    public static final int STALE_HAY = 0xA89A6E;
    /** What comes out of a strain with no smoke colour and no terpene: the plain wisp. */
    public static final int PLAIN = 0xFFFFFF;
    /** Where the puff starts: just over the lid ({@code StashJarBlock}'s shape tops out at 12/16). */
    private static final double LID = 12.5 / 16.0;

    private TerpenePuff() {
    }

    /** The puff a jar of this {@code nose} lets off as {@code out} comes out of it. */
    public static TerpenePuffOption of(ServerLevel level, StashNose nose, ItemStack out) {
        return new TerpenePuffOption(colourOf(level, nose, out), nose);
    }

    /** Lets the puff off at the jar at {@code pos}; returns how many players it was sent to. */
    public static int release(ServerLevel level, BlockPos pos, StashNose nose, ItemStack out) {
        if (nose == StashNose.EPIC) {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F,
                    1.1F + level.getRandom().nextFloat() * 0.2F);
        }
        // Count 0 sends the one particle exactly where it is put, its speed unused.
        return level.sendParticles(of(level, nose, out), pos.getX() + 0.5, pos.getY() + LID, pos.getZ() + 0.5,
                0, 0.0, 0.0, 0.0, 0.0);
    }

    private static int colourOf(ServerLevel level, StashNose nose, ItemStack out) {
        if (nose.level() == 0 || out.is(ModItems.SCHWAG)) {
            return STALE_HAY;
        }
        return Strain.fromBuds(level.registryAccess(), out.getItem())
                .flatMap(strain -> Terpenes.smokeTint(strain.value(), level.registryAccess()))
                .orElse(PLAIN);
    }
}
