package com.warlonmhite.hempdustry.block.custom;

import com.warlonmhite.hempdustry.balance.GrowClimate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The UV-C Lamp: a quartz tube under a copper hood, hung from the ceiling on the Grow Lamp's two chains, for the
 * dark hours of a grow room. Real germicidal tubes are fused quartz, and a grower runs them at night and with
 * nobody in the room: the fungus repairs UV damage with blue light, so a lamp that works in the dark is one that
 * works. It does the same here.
 *
 * <p><b>It runs on redstone</b>, as the Grow Lamp, the fan and the Dehumidifier do ({@link #POWERED}: a signal into
 * the block it hangs from, or a lever or button on it), and lights itself ({@link #LIT}) only while it is night:
 * the one night the mod keeps ({@link PlantStress#hours}, what a daylight detector at the plant would read), or at
 * any hour in a dimension with no day ({@link #dark}). It switches at nightfall and daybreak by itself and keeps its
 * state through the twilight between, so one redstone block runs it and a lever turns it off. A soft click at each
 * switch, vanilla's copper bulb's.
 *
 * <p><b>It gives no light</b> (level 0): nothing for White Widow to leak, nothing against Echo OG, no grow luck, no
 * mob-proofing. A lit tube is drawn full-bright instead, as vanilla draws a magma block, so a room shows it working.
 * It is not a roof, and it is not in {@code #hempdustry:grow_lamps}.
 *
 * <p><b>What it does</b> is in {@link #protects}: a plant under a lit lamp that is itself in the dark catches mildew
 * and takes it from a neighbour at half the odds ({@link Climate#factor}), and a mildewed one comes clean on one in
 * sixteen of its random ticks, ripe or not ({@link Mildew#randomTick}). A sulfured plant stays sulfured. The tube may
 * stay lit through the twilight, but the work does not: the fungus mends UV damage with the blue light of a
 * brightening sky, so only night itself (or a world with no day) counts.
 */
public class UvcLampBlock extends Block {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** How often a powered lamp looks at the sky again, in ticks: a daylight detector's second. */
    public static final int READ_EVERY = 20;

    /** The hood, the tube under it and the two chains it hangs from, as the model draws them. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1.0D, 8.0D, 3.0D, 15.0D, 11.0D, 13.0D),
            Block.box(2.0D, 4.0D, 6.0D, 14.0D, 8.0D, 10.0D),
            Block.box(2.5D, 11.0D, 6.5D, 5.5D, 16.0D, 9.5D),
            Block.box(10.5D, 11.0D, 6.5D, 13.5D, 16.0D, 9.5D));

    public UvcLampBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, LIT);
    }

    /**
     * Hung where there is a ceiling to hang it from, and placed powered if the spot is: the sky is read on the tick
     * {@link #onPlace} schedules, so a lamp placed at night lights a tick later, never in the placement itself.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = this.defaultBlockState().setValue(POWERED, ctx.getLevel().hasNeighborSignal(ctx.getClickedPos()));
        return state.canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? state : null;
    }

    /** The same test a hanging lantern makes, and the Grow Lamp: something under the block above to hang the chains from. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        return Block.canSupportCenter(world, pos.above(), Direction.DOWN);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader world, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState,
                                     RandomSource random) {
        return direction == Direction.UP && !state.canSurvive(world, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, world, ticks, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState old, boolean movedByPiston) {
        if (!old.is(this) && !world.isClientSide() && state.getValue(POWERED)) {
            world.scheduleTick(pos, this, 1);
        }
    }

    /** The signal reaches the lamp at once; the sky is read on the tick that follows, and every second after. */
    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block block,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        if (world.isClientSide()) {
            return;
        }
        boolean powered = world.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            this.switchTo(world, pos, state, powered);
            if (powered) {
                world.scheduleTick(pos, this, READ_EVERY);
            }
        }
    }

    /** Looks at the sky again while the lamp is powered, as a daylight detector does. */
    @Override
    protected void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (!state.getValue(POWERED)) {
            return;
        }
        this.switchTo(world, pos, state, true);
        world.scheduleTick(pos, this, READ_EVERY);
    }

    /** Writes the power and what it makes of the lamp now, with the copper bulb's click if the tube switched. */
    private void switchTo(Level world, BlockPos pos, BlockState state, boolean powered) {
        boolean lit = powered && lights(world, pos, state.getValue(LIT));
        if (powered == state.getValue(POWERED) && lit == state.getValue(LIT)) {
            return;
        }
        world.setBlock(pos, state.setValue(POWERED, powered).setValue(LIT, lit), Block.UPDATE_CLIENTS);
        if (lit != state.getValue(LIT)) {
            world.playSound(null, pos, lit ? SoundEvents.COPPER_BULB_TURN_ON : SoundEvents.COPPER_BULB_TURN_OFF,
                    SoundSource.BLOCKS);
        }
    }

    /**
     * Whether a powered lamp at {@code pos} lights: at night, off by day, and as it was through the twilight
     * between ({@link PlantStress.Hours}).
     */
    private static boolean lights(Level world, BlockPos pos, boolean wasLit) {
        if (noDay(world)) {
            return true;
        }
        return switch (PlantStress.hours(world, pos)) {
            case NIGHT -> true;
            case DAY -> false;
            case TWILIGHT -> wasLit;
        };
    }

    /**
     * Whether the world has no day at all: a dimension whose clock is fixed (vanilla's {@code has_fixed_time}, the
     * Nether and the End). No sun there, nothing to repair the fungus, so it counts as night at every hour.
     */
    public static boolean noDay(Level world) {
        return world.dimensionType().hasFixedTime();
    }

    /**
     * Whether it is dark where {@code pos} is, for a lamp: night by the mod's one reading
     * ({@link PlantStress#hours}), or any hour where there is no day.
     */
    public static boolean dark(Level world, BlockPos pos) {
        return noDay(world) || PlantStress.hours(world, pos) == PlantStress.Hours.NIGHT;
    }

    /** Whether {@code state} is a UV-C Lamp with its tube lit. */
    public static boolean lit(BlockState state) {
        return state.getBlock() instanceof UvcLampBlock && state.getValue(LIT);
    }

    /**
     * Whether a lit lamp works on the plant whose root is at {@code root} and which stands {@code height} blocks
     * tall once grown: a lit lamp within the climate's reach to each side of it and in the three blocks over its top,
     * the Grow Lamp's column ({@link GrowLight#over}) — an opaque block between stops it — and the plant itself in the
     * dark: night at the plant by the mod's one reading, not its twilight ({@link #dark}, so a lamp kept lit through
     * the dawn does nothing), and no lit light over it and none touching it, White Widow's leak test
     * ({@link PlantStress#leaks}). A plant that hangs from a ceiling has no lamp over it.
     *
     * <p>Read only where it matters: a growth step, a spread target, a mildewed plant's random tick. Most plants have
     * no lamp near them, and the sections' palettes say so without a block read.
     */
    public static boolean protects(Level world, BlockPos root, int height) {
        if (Climate.hangs(world, root)) {
            return false;
        }
        int reach = GrowClimate.of(world.registryAccess()).uvcLamp().reach();
        if (!Climate.mayHold(world, root.offset(-reach, height, -reach),
                root.offset(reach, height + GrowLight.REACH - 1, reach), state -> state.getBlock() instanceof UvcLampBlock)) {
            return false;
        }
        return dark(world, root) && lampOver(world, root, height, reach) && !PlantStress.leaks(world, root, height);
    }

    /** {@link #protects} for the plant standing as {@code plant} at {@code root}. */
    public static boolean protects(Level world, BlockPos root, BlockState plant) {
        return protects(world, root, Climate.heightOf(plant));
    }

    private static boolean lampOver(BlockGetter world, BlockPos root, int height, int reach) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (int i = 0; i < GrowLight.REACH; i++) {
                    BlockState state = world.getBlockState(at.setWithOffset(root, dx, height + i, dz));
                    if (lit(state)) {
                        return true;
                    }
                    if (state.isSolidRender()) {
                        break;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Whether a mildewed plant at {@code root} comes clean on this random tick: a lit lamp protects it
     * ({@link #protects}), and one tick in {@code cure_one_in} takes. Nothing is drawn for a plant no lamp reaches.
     */
    public static boolean cures(Level world, BlockPos root, BlockState plant, RandomSource random) {
        return protects(world, root, plant)
                && random.nextInt(GrowClimate.of(world.registryAccess()).uvcLamp().cureOneIn()) == 0;
    }

    /**
     * What a lit lamp takes off the odds of mildew starting on a plant, or reaching it, under it: the climate's
     * {@code night_factor}, or 1 where no lamp works on the plant.
     */
    public static float factorOver(Level world, BlockPos root, BlockState plant) {
        return protects(world, root, plant) ? GrowClimate.of(world.registryAccess()).uvcLamp().nightFactor() : 1.0F;
    }
}
