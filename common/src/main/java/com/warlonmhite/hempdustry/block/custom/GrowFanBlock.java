package com.warlonmhite.hempdustry.block.custom;

import com.warlonmhite.hempdustry.balance.GrowClimate;
import com.warlonmhite.hempdustry.config.ClientConfig;
import com.warlonmhite.hempdustry.particle.FanPuffOption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Grow Fan: a box fan in a copper housing, for a grow room's air. It stands on a floor, sits on a wall or
 * hangs from a ceiling, as a lever does, and runs on redstone as the Grow Lamp does: a signal into the block,
 * or into the block it is fixed to — a lever or button on that block does it; the housing has no face to hold
 * one — never a torch on that block, which powers everything around it but the block it stands on.
 *
 * <p>It blows level the way its grille faces ({@link #blows}), wherever it is fixed: placed on a floor or a
 * ceiling, away from the player who set it; on a wall, out of the wall. A running fan moves the air of every
 * plant it blows on one step towards the open ({@link Climate#ventilated}, {@link Climate#air}): a sealed room's
 * still air is stirred, a greenhouse's is as good as a field's. A wall stops its wind, line by line
 * ({@link FanWind}); bars, fences and grates do not. Fans do not add up; one is enough. And a plant
 * that grew in a fan's wind at every step thickens its stalk: one stem more at harvest
 * ({@code hempdustry:ventilated}), which a Grow Pot's plant, short of a stem, is glad of.
 *
 * <p>Its blades spin while it runs, drawn by an animated texture: the block needs no renderer of its own. And now
 * and then a faint puff of air leaves the grille and drifts down the lane it blows on, out to its sides and on to
 * its far edge ({@link #animateTick}), so a player sees which plants it reaches without a recipe viewer's page.
 */
public class GrowFanBlock extends FaceAttachedHorizontalDirectionalBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /** How far from the grille's middle a puff may leave it, each way: within its 12 pixels, short of the rim. */
    private static final double GRILLE = 0.3;
    /** How far past the lane's outer rows a puff's middle may drift to the side: about its own size. */
    private static final double EDGE = 0.4;
    /** How far short of the lane's far edge a puff is gone, so it never shows a block past the end. */
    private static final double SHORT = 0.05;

    /** The housing for a fan whose front faces north, standing, on a wall and hanging; turned for the rest. */
    private static final VoxelShape FLOOR_NS = Block.box(2.0D, 0.0D, 6.0D, 14.0D, 14.0D, 10.0D);
    private static final VoxelShape FLOOR_EW = Block.box(6.0D, 0.0D, 2.0D, 10.0D, 14.0D, 14.0D);
    private static final VoxelShape CEILING_NS = Block.box(2.0D, 2.0D, 6.0D, 14.0D, 16.0D, 10.0D);
    private static final VoxelShape CEILING_EW = Block.box(6.0D, 2.0D, 2.0D, 10.0D, 16.0D, 14.0D);
    private static final VoxelShape WALL_NORTH = Block.box(2.0D, 2.0D, 11.0D, 14.0D, 14.0D, 16.0D);
    private static final VoxelShape WALL_SOUTH = Block.box(2.0D, 2.0D, 0.0D, 14.0D, 14.0D, 5.0D);
    private static final VoxelShape WALL_EAST = Block.box(0.0D, 2.0D, 2.0D, 5.0D, 14.0D, 14.0D);
    private static final VoxelShape WALL_WEST = Block.box(11.0D, 2.0D, 2.0D, 16.0D, 14.0D, 14.0D);

    public GrowFanBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FACE, AttachFace.FLOOR)
                .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FACE, POWERED);
    }

    /** Placed as a lever is, and running from the start if the spot is already powered. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        return state == null ? null : state.setValue(POWERED, ctx.getLevel().hasNeighborSignal(ctx.getClickedPos()));
    }

    /** Switched on and off at once by the signal, as a powered rail is: a fan has no afterglow to wait out. */
    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block block,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        if (world.isClientSide()) {
            return;
        }
        boolean powered = world.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            world.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        boolean northSouth = facing.getAxis() == Direction.Axis.Z;
        return switch (state.getValue(FACE)) {
            case FLOOR -> northSouth ? FLOOR_NS : FLOOR_EW;
            case CEILING -> northSouth ? CEILING_NS : CEILING_EW;
            case WALL -> switch (facing) {
                case SOUTH -> WALL_SOUTH;
                case EAST -> WALL_EAST;
                case WEST -> WALL_WEST;
                default -> WALL_NORTH;
            };
        };
    }

    /**
     * A running fan's air, seen: on each of its animate ticks (a block near a player gets one or two a second, more
     * the nearer it is), one faint puff from somewhere on the grille, bound for a spot picked across the whole width
     * of the lane, and gone at its far edge. Drawn on the client only, as a damp plant's drips are; the lane is the
     * world's own ({@code fan} in the grow climate), so a datapack that moves the wind moves the puffs with it.
     */
    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        if (!puffs(state)) {
            return;
        }
        Puff puff = puff(state, world, pos, GrowClimate.of(world.registryAccess()).fan(),
                random.nextDouble() * 2.0 - 1.0, random.nextDouble() * 2.0 - 1.0, random.nextDouble() * 2.0 - 1.0);
        // One stopped before it leaves the fan's block would only flicker inside the housing.
        if (puff.path().reach() > puff.path().clear()) {
            world.addParticle(puff.path(), puff.from().x, puff.from().y, puff.from().z, 0.0, 0.0, 0.0);
        }
    }

    /**
     * A puff of the fan's air, leaving the grille at {@code from} along {@code path}.
     */
    public record Puff(Vec3 from, FanPuffOption path) {
    }

    /**
     * A puff off the fan standing as {@code state} at {@code pos}, blowing along {@code lane}. Where it leaves the
     * grille and where it goes are picked by three numbers from -1 to 1: {@code side} and {@code up} across the
     * grille from its middle, {@code to} across the whole width of the lane from the fan's line (positive clockwise
     * of the way it blows, seen from above). It runs straight out of the fan's own block, drifts out to that side
     * over the lane's first block, then goes straight on, and is gone at the lane's far edge: {@code ahead} blocks
     * of lane in front of the fan's own block, half a block past the middle of the last.
     *
     * <p>Or short of it, where a wall stops the wind ({@link FanWind}): a puff runs in the line of the column it ends
     * in, at the fan's height, and is gone at the near face of the first block in that line that stops the wind, as a
     * plant behind that block is not blown on. One bound across the lane's first block is gone at its near face if
     * any block it would drift through there stops the wind.
     */
    public static Puff puff(BlockState state, BlockGetter world, BlockPos pos, GrowClimate.Fan lane,
                            double side, double up, double to) {
        Direction blows = blows(state);
        Direction across = blows.getClockWise();
        Vec3 from = grille(state, world, pos)
                .add(across.getStepX() * side * GRILLE, up * GRILLE, across.getStepZ() * side * GRILLE);
        Vec3 off = from.subtract(Vec3.atCenterOf(pos));
        double alreadyAhead = off.x * blows.getStepX() + off.z * blows.getStepZ();
        double alreadyAcross = off.x * across.getStepX() + off.z * across.getStepZ();
        double ends = to * (lane.side() + EDGE);
        double sideways = ends - alreadyAcross;
        double clear = 0.5 - alreadyAhead;
        // The column the puff runs on in, read off its own path once it has spread, as the particle will follow it: a
        // path near the edge between two blocks is in whichever one the path's own numbers put it.
        FanPuffOption spread = new FanPuffOption(blows, (float) clear, (float) sideways, Float.MAX_VALUE);
        BlockPos landing = BlockPos.containing(from.add(spread.offset(clear + 2.0 * FanPuffOption.SPREAD)));
        int column = Math.clamp((long) across.getStepX() * (landing.getX() - pos.getX())
                + (long) across.getStepZ() * (landing.getZ() - pos.getZ()), -lane.side(), lane.side());
        int stop = FanWind.firstStop(world, pos.relative(across, column), blows, lane.ahead());
        BlockPos.MutableBlockPos first = new BlockPos.MutableBlockPos();
        for (int passed = 0; passed != column && stop > 1; passed += Integer.signum(column)) {
            first.set(pos).move(blows).move(across, passed);
            if (FanWind.stops(world, first, world.getBlockState(first), blows.getAxis())) {
                stop = 1;
            }
        }
        double reach = Math.max(0.0, stop - 0.5 - alreadyAhead - SHORT);
        return new Puff(from, new FanPuffOption(blows, (float) clear, (float) sideways, (float) reach));
    }

    /**
     * The middle of the grille of the fan standing as {@code state} at {@code pos}: on the front of its housing, at
     * the middle of the block across and up, where the housing (2 to 14 pixels each way) has it in every placement;
     * a standing fan's foot and a hanging fan's hanger are below and above the housing, never in front of it.
     */
    private static Vec3 grille(BlockState state, BlockGetter world, BlockPos pos) {
        Direction blows = blows(state);
        AABB housing = state.getShape(world, pos).bounds();
        double front = blows.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? housing.max(blows.getAxis()) : housing.min(blows.getAxis());
        Vec3 middle = Vec3.atCenterOf(pos);
        return blows.getAxis() == Direction.Axis.X
                ? new Vec3(pos.getX() + front, middle.y, middle.z)
                : new Vec3(middle.x, middle.y, pos.getZ() + front);
    }

    /**
     * Whether this client draws puffs off the Grow Fan standing as {@code state}: a running one, unless its player
     * turned them off ({@code client.fanPuffs}). Read on every animate tick, so a reload takes effect at once.
     */
    public static boolean puffs(BlockState state) {
        return running(state) && ClientConfig.get().fanPuffs();
    }

    /** Whether {@code state} is a Grow Fan that is running. */
    public static boolean running(BlockState state) {
        return state.getBlock() instanceof GrowFanBlock && state.getValue(POWERED);
    }

    /**
     * The way the Grow Fan standing as {@code state} blows: the way its grille faces, which each model draws on its
     * north face and the block state turns by {@link #FACING}, on a floor, a wall and a ceiling alike.
     */
    public static Direction blows(BlockState state) {
        return state.getValue(FACING);
    }
}
