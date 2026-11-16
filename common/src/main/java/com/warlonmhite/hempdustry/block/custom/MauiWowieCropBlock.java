package com.warlonmhite.hempdustry.block.custom;

import com.warlonmhite.hempdustry.item.ModItems;
import com.warlonmhite.hempdustry.util.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * Maui Wowie, the ocean's plant: Lemon Haze's three-block body, every block of it under water. It holds its own
 * water as vanilla's seagrass does, with no {@code waterlogged} property: each block of it is a block of water as
 * well, so the plant can only be planted in a full water source and only grows a segment into one, and whatever
 * takes a block of it away leaves the water behind ({@link SativaCropBlock#leftBehind}). A sponge takes it as it
 * takes seagrass and kelp, with its drops ({@code #hempdustry:sponge_clears}).
 *
 * <p><b>The water sets how deep it grows.</b> A block of water dims sky light by one, and its own blocks dim it
 * as much as the water they hold, so under open sky the crops' own rule (growth at light 9, life at 8) grows it at
 * most 6 blocks deep and keeps it alive at 7. Nothing here computes a depth: it is vanilla's light. Sea pickles,
 * sea lanterns or a Grow Lamp take a farm deeper.
 *
 * <p><b>A seagrass meadow is its farmland.</b> On sand, gravel, clay or mud ({@code #hempdustry:maui_wowie_soil})
 * a crop reads moisture 1, as vanilla reads any soil that is not farmland, about ten times slower than Lemon Haze
 * on watered farmland. Each of the eight spots around its root grown with seagrass, tall seagrass or kelp
 * ({@code #hempdustry:maui_wowie_meadow}) brings it an eighth of the way to watered farmland's 10, so a full
 * meadow grows it at a land crop's pace; its own kind crowding it halves that, as on land. Bone meal on the sand
 * around spreads the meadow, as it does in vanilla. No Grow Pot and no Hydro Tray: they hold no sea.
 *
 * <p>Its open sky, for the quality of its buds, is read through the water from its top
 * ({@code PlantRecordBlockEntity#recordSky}); and the water above it is no roof to powdery mildew
 * ({@code Mildew#roofed}).
 */
public class MauiWowieCropBlock extends SativaCropBlock implements LiquidBlockContainer {
    /** Every block of water around the root's soil that a meadow plant may fill: the eight spots, not the centre. */
    private static final float MEADOW_SPOTS = 8.0F;
    /** Moisture on bare soil, vanilla's for any soil that is not farmland. */
    private static final float BARE = 1.0F;
    /** Moisture in a full meadow: a crop in the middle of watered farmland, 1 + 3 + 8 × ¾. */
    private static final float FULL_MEADOW = 10.0F;

    public MauiWowieCropBlock(Properties settings) {
        super(settings);
    }

    /** Sand, red sand, gravel, clay or mud: the sea floor, by name. Never a bed of ours, which holds no sea. */
    @Override
    protected boolean mayPlaceOn(BlockState floor, BlockGetter world, BlockPos pos) {
        return floor.is(ModTags.Blocks.MAUI_WOWIE_SOIL);
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return ModItems.MAUI_WOWIE_SEEDS;
    }

    /** Planted only into a full water source, as seagrass is placed. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        return fluid.is(FluidTags.WATER) && fluid.isFull() ? super.getStateForPlacement(context) : null;
    }

    /** A segment grows only into still water, a full source: the plant needs the sea all the way up. */
    @Override
    protected boolean isRoomToGrow(BlockState state) {
        return isOpenWater(state);
    }

    /** A block of plain still water, a full source, with nothing else in it. */
    public static boolean isOpenWater(BlockState state) {
        return state.is(Blocks.WATER) && state.getFluidState().isSource();
    }

    /** Every block of it is a block of water. */
    @Override
    protected FluidState getFluidState(BlockState state) {
        return Fluids.WATER.getSource(false);
    }

    /** As seagrass: the water it holds still flows into its neighbours. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader world, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        BlockState shaped = super.updateShape(state, world, ticks, pos, direction, neighborPos, neighbor, random);
        if (!shaped.isAir()) {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }
        return shaped;
    }

    @Override
    public boolean canPlaceLiquid(@Nullable LivingEntity user, BlockGetter world, BlockPos pos, BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor world, BlockPos pos, BlockState state, FluidState fluid) {
        return false;
    }

    /**
     * The meadow's moisture at the root {@code pos}: {@link #BARE} on bare soil, and an eighth of the way to
     * {@link #FULL_MEADOW} for each of the eight spots around the root grown with the meadow, halved when its own
     * kind crowds it, as vanilla halves a crop's ({@code CropBlock#getGrowthSpeed}).
     */
    @Override
    public float fieldMoisture(LevelReader world, BlockPos pos) {
        int meadow = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && world.getBlockState(pos.offset(dx, 0, dz)).is(ModTags.Blocks.MAUI_WOWIE_MEADOW)) {
                    meadow++;
                }
            }
        }
        float moisture = BARE + (FULL_MEADOW - BARE) * meadow / MEADOW_SPOTS;
        return this.crowded(world, pos) ? moisture / 2.0F : moisture;
    }

    /** Vanilla's crowding: its own kind on two sides at right angles, or on any diagonal. */
    private boolean crowded(LevelReader world, BlockPos pos) {
        boolean eastWest = world.getBlockState(pos.west()).is(this) || world.getBlockState(pos.east()).is(this);
        boolean northSouth = world.getBlockState(pos.north()).is(this) || world.getBlockState(pos.south()).is(this);
        if (eastWest && northSouth) {
            return true;
        }
        return world.getBlockState(pos.north().west()).is(this) || world.getBlockState(pos.north().east()).is(this)
                || world.getBlockState(pos.south().east()).is(this) || world.getBlockState(pos.south().west()).is(this);
    }
}
