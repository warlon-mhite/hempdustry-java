package com.warlonmhite.hempdustry.block.custom;

import com.warlonmhite.hempdustry.strain.internal.Rolls;
import com.warlonmhite.hempdustry.strain.internal.Strains;
import com.warlonmhite.hempdustry.block.entity.custom.PlantRecordBlockEntity;
import com.warlonmhite.hempdustry.strain.GrowQuality;
import com.warlonmhite.hempdustry.strain.Strain;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A plant's grow luck at its harvest: the number the {@link com.warlonmhite.hempdustry.strain.BudTier}
 * roll weighs its tiers by, in vanilla's loot-luck grammar. The sum of
 *
 * <ul>
 *   <li>the strain's <b>base luck</b> ({@link GrowQuality#baseLuck}), which stands in for care a strain
 *       cannot take;</li>
 *   <li>the plant's <b>quality gene</b>, −1 to +2;</li>
 *   <li><b>one per trim window used</b>, two at most;</li>
 *   <li>the <b>light</b> it kept: <b>the sun +1</b> for a plant that grew in it (open sky or under glass),
 *       whatever lamp hangs over it; out of the sun a lamp +1, a Grow Lamp +2, shade and glowstone 0. So a
 *       greenhouse is the easy way to good buds, light for free, and a sealed room under Grow Lamps the way to
 *       the best;</li>
 *   <li><b>a Hydro Tray +1</b>, at work under the root at every growth step (water, fed, the pump on);</li>
 *   <li>the <b>harvesting player's luck</b> ({@code minecraft:luck}: Luck +1 a level, Bad Luck −1), which
 *       vanilla hands fishing and chests but never a block's loot. No player, no luck.</li>
 * </ul>
 */
public final class GrowLuck {
    private GrowLuck() {
    }

    /** Whether the plant whose root is {@code root} skips the roll and gives Mids: stressed or mildewed. */
    public static boolean givesMids(BlockState root) {
        if (root.hasProperty(GrowLight.PROPERTY) && root.getValue(GrowLight.PROPERTY) == GrowLight.STRESSED) {
            return true;
        }
        return root.hasProperty(Mildew.PROPERTY) && root.getValue(Mildew.PROPERTY) == Mildew.MILDEWED;
    }

    /** The grow luck of {@code record}'s plant, whose root stood as {@code root}, harvested by {@code harvester}. */
    public static float of(PlantRecordBlockEntity record, BlockState root, Level level, @Nullable Entity harvester) {
        int baseLuck = Strains.ofPlant(root.getBlock(), level.registryAccess())
                .map(Holder::value)
                .map(Strain::traits).map(Strain.Traits::quality)
                .orElse(GrowQuality.DEFAULT)
                .baseLuck();
        return baseLuck + Rolls.luck(record.gene()) + care(root, record.openSky(), record.workingTray()) + luckOf(harvester);
    }

    /** What the plant's care adds: its trims, the best light it kept, and a Hydro Tray. */
    public static int care(BlockState root, boolean openSky, boolean tray) {
        int luck = 0;
        if (root.hasProperty(Defoliation.TRIMMED_EARLY) && root.getValue(Defoliation.TRIMMED_EARLY)) {
            luck++;
        }
        if (root.hasProperty(Defoliation.TRIMMED_LATE) && root.getValue(Defoliation.TRIMMED_LATE)) {
            luck++;
        }
        return luck + light(root, openSky) + (tray ? 1 : 0);
    }

    /**
     * The light the plant kept: the sun 1 for a plant that grew in it ({@code openSky}: sky light at its top at
     * every step, glass passing it), whatever lamp hung over it as well; out of the sun a Grow Lamp 2, a lamp 1,
     * anything else 0. A lamp still gives a sunlit plant its buds, its leaves and its seedling's speed; the sun
     * caps only the luck.
     */
    public static int light(BlockState root, boolean openSky) {
        if (openSky) {
            return 1;
        }
        if (!root.hasProperty(GrowLight.PROPERTY)) {
            return 0;
        }
        return switch (root.getValue(GrowLight.PROPERTY)) {
            case GROW_LAMP -> 2;
            case LAMP -> 1;
            default -> 0;
        };
    }

    /** {@code harvester}'s luck, if it is a player; nothing else has any. */
    public static float luckOf(@Nullable Entity harvester) {
        return harvester instanceof Player player ? player.getLuck() : 0.0F;
    }
}
