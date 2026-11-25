package com.warlonmhite.hempdustry.block.custom;

import com.warlonmhite.hempdustry.util.ModTags;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Where a Grow Fan's wind stops. The wind runs level, in straight lines one block wide and one block high, and a
 * line stops at the first block that closes most of its way: one whose collision shape, seen from the fan, covers
 * at least three quarters of the block's face ({@link #STOPS_AT}). Less than that and the air goes round it inside
 * the block — past a slab, a bed, a lantern, a lone fence post or pane, a chain, a closed trapdoor lying flat — and
 * a full block, leaves, a chest, stairs, a closed door or a glass-pane window stops it. Blocks in
 * {@code #hempdustry:fan_passes_through} never stop it, whatever their shape: bars, fences and fence gates, copper
 * grates and mangrove roots, open work the air goes through though a player cannot.
 *
 * <p>The shape is the one vanilla keeps for a block's collisions with nothing in particular
 * ({@link CollisionContext#empty()}), the shape campfire smoke and dripstone drips are tested against, read only
 * inside the block's own cell (a fence's 24-pixel collision counts its first 16). It is worked out once per state
 * and wind axis and kept, as vanilla keeps that same shape once per state, except for blocks whose shape depends on
 * where they stand ({@link Block#hasDynamicShape()}), which are asked where they stand every time.
 *
 * <p>A blocked line cuts only itself: the line beside it blows on, and the wind never bends round a block.
 */
public final class FanWind {
    /** How much of a block's face, seen from the fan, its shape covers before it stops the wind: three quarters. */
    public static final double STOPS_AT = 0.75;
    /** An area summed from doubles can fall a hair short: a block covering exactly three quarters stops it. */
    private static final double SLACK = 1.0E-6;

    /** Each state's cover, two to a state (wind along x, wind along z); NaN until worked out. */
    private static volatile float[] covers;

    private FanWind() {
    }

    /**
     * Whether the block standing as {@code state} at {@code pos} stops wind blowing along {@code axis} (x or z):
     * a block out of {@code #hempdustry:fan_passes_through} whose shape covers at least three quarters of its face.
     */
    public static boolean stops(BlockGetter world, BlockPos pos, BlockState state, Direction.Axis axis) {
        return !state.isAir() && !state.is(ModTags.Blocks.FAN_PASSES_THROUGH) && cover(world, pos, state, axis) >= STOPS_AT - SLACK;
    }

    /**
     * How far a line of wind blowing {@code blows} from {@code from} gets, from {@code from}'s own block (never
     * counted, it is the fan's or the plant's) out to {@code limit} blocks: the number of the first block in that
     * stops it, from 1, or {@code limit + 1} when none does.
     */
    public static int firstStop(BlockGetter world, BlockPos from, Direction blows, int limit) {
        BlockPos.MutableBlockPos at = from.mutable();
        for (int step = 1; step <= limit; step++) {
            at.move(blows);
            if (stops(world, at, world.getBlockState(at), blows.getAxis())) {
                return step;
            }
        }
        return limit + 1;
    }

    /**
     * How much of its face the block standing as {@code state} at {@code pos} covers, seen along {@code axis} (x or
     * z), from 0 to 1: the area of its collision shape, inside its own cell, projected onto the face across that axis.
     */
    public static float cover(BlockGetter world, BlockPos pos, BlockState state, Direction.Axis axis) {
        if (state.getBlock().hasDynamicShape()) {
            return cover(state.getCollisionShape(world, pos, CollisionContext.empty()), axis);
        }
        float[] kept = covers;
        if (kept == null) {
            kept = new float[Block.BLOCK_STATE_REGISTRY.size() * 2];
            Arrays.fill(kept, Float.NaN);
            covers = kept;
        }
        int id = Block.getId(state);
        int slot = id * 2 + (axis == Direction.Axis.X ? 0 : 1);
        if (id < 0 || slot >= kept.length) {
            return cover(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()), axis);
        }
        float cover = kept[slot];
        if (Float.isNaN(cover)) {
            // Worked out the way vanilla works out the collision shape it keeps for a state: in an empty world, at
            // the origin. Two threads (a client and its own server) may both do it; they write the same number.
            cover = cover(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()), axis);
            kept[slot] = cover;
        }
        return cover;
    }

    /**
     * How much of a block's face {@code shape} covers, seen along {@code axis}: its boxes cut to the cell, laid flat
     * on the face across {@code axis} (height, and the other level axis), and the area of what they cover together,
     * each overlap counted once.
     */
    static float cover(VoxelShape shape, Direction.Axis axis) {
        if (shape.isEmpty()) {
            return 0.0F;
        }
        Direction.Axis side = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        List<AABB> boxes = shape.toAabbs();
        double[][] flat = new double[boxes.size()][];
        int count = 0;
        for (AABB box : boxes) {
            if (Math.min(box.max(axis), 1.0) <= Math.max(box.min(axis), 0.0)) {
                continue;
            }
            double u0 = Math.clamp(box.min(side), 0.0, 1.0);
            double u1 = Math.clamp(box.max(side), 0.0, 1.0);
            double v0 = Math.clamp(box.minY, 0.0, 1.0);
            double v1 = Math.clamp(box.maxY, 0.0, 1.0);
            if (u1 > u0 && v1 > v0) {
                flat[count++] = new double[] {u0, u1, v0, v1};
            }
        }
        if (count == 0) {
            return 0.0F;
        }
        // The area of a union of rectangles: cut the face along every rectangle's edges and add up the pieces some
        // rectangle covers. A block's shape is a few dozen boxes at most.
        double[] us = new double[count * 2];
        double[] vs = new double[count * 2];
        for (int i = 0; i < count; i++) {
            us[2 * i] = flat[i][0];
            us[2 * i + 1] = flat[i][1];
            vs[2 * i] = flat[i][2];
            vs[2 * i + 1] = flat[i][3];
        }
        Arrays.sort(us);
        Arrays.sort(vs);
        double area = 0.0;
        for (int i = 0; i + 1 < us.length; i++) {
            double du = us[i + 1] - us[i];
            if (du <= 0.0) {
                continue;
            }
            double u = (us[i] + us[i + 1]) / 2.0;
            for (int j = 0; j + 1 < vs.length; j++) {
                double dv = vs[j + 1] - vs[j];
                if (dv <= 0.0) {
                    continue;
                }
                double v = (vs[j] + vs[j + 1]) / 2.0;
                for (int k = 0; k < count; k++) {
                    double[] r = flat[k];
                    if (u > r[0] && u < r[1] && v > r[2] && v < r[3]) {
                        area += du * dv;
                        break;
                    }
                }
            }
        }
        return (float) Math.min(1.0, area);
    }
}
