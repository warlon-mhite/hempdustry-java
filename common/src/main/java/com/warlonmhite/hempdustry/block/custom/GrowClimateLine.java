package com.warlonmhite.hempdustry.block.custom;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a block-info overlay (Jade, WTHIT) shows under a plant of the mod: the air and the humidity its mildew reads,
 * and its mildew when it has any. Read from the plant's root, whichever block of it is looked at, with the plant's own
 * rules ({@link Climate#air}, {@link Climate#humidity}, {@link Mildew#roofed}), so the overlay says what the next
 * roll reads.
 *
 * <p>Everything those rules read is in the world a client holds: block states, sky light, the biome, and the
 * {@code MOTION_BLOCKING_NO_LEAVES} heightmap a roof is read from, which 26.3 sends to clients
 * ({@code Heightmap.Types} builds it with {@code Usage.CLIENT}). So the line needs nothing from the server, and
 * shows on a server that has no overlay mod.
 *
 * <p>Only a plant that keeps a mildew state has a climate worth a line ({@link Mildew#PROPERTY}): the climate is what
 * its mildew reads. The overlay mods' plugins call this and nothing else of the mod; it loads neither of them.
 */
public final class GrowClimateLine {
    /** The overlays' id for the line, and so for its switch in their settings. */
    public static final String ID = "grow_climate";

    private static final String KEY = "hempdustry.grow_climate.";

    private GrowClimateLine() {
    }

    /** A plant's climate as the line shows it. */
    public record Reading(Climate.Air air, Climate.Humidity humidity, Mildew mildew) {
    }

    /** The reading of the plant one of whose blocks is at {@code pos}, or nothing when no such plant stands there. */
    public static Optional<Reading> of(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.hasProperty(Mildew.PROPERTY)) {
            return Optional.empty();
        }
        Block block = state.getBlock();
        BlockPos root = TallHemp.rootOf(block, world, pos, state);
        if (root == null) {
            root = pos;
        }
        BlockState plant = world.getBlockState(root);
        if (!plant.hasProperty(Mildew.PROPERTY)) {
            return Optional.empty();
        }
        boolean roofed = Mildew.roofed(world, root);
        return Optional.of(new Reading(Climate.air(world, root, Climate.heightOf(plant), roofed),
                Climate.humidity(world, root, roofed, Climate.onTray(world, root)),
                plant.getValue(Mildew.PROPERTY)));
    }

    /** The lines {@code reading} is shown as: air and humidity on one, and the mildew under it when there is any. */
    public static List<Component> lines(Reading reading) {
        List<Component> lines = new ArrayList<>(2);
        lines.add(Component.translatable(KEY + "line",
                Component.translatable(KEY + "air." + reading.air().getSerializedName()),
                Component.translatable(KEY + "humidity." + reading.humidity().getSerializedName())));
        if (reading.mildew() != Mildew.CLEAN) {
            lines.add(Component.translatable(KEY + "mildew." + reading.mildew().getSerializedName())
                    .withStyle(reading.mildew() == Mildew.MILDEWED ? ChatFormatting.RED : ChatFormatting.GOLD));
        }
        return lines;
    }
}
