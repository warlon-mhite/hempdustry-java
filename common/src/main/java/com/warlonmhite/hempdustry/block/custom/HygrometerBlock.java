package com.warlonmhite.hempdustry.block.custom;

import com.warlonmhite.hempdustry.balance.GrowClimate;
import com.warlonmhite.hempdustry.block.custom.Climate.Humidity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hygrometer: a dial on a wall, a hemp fibre behind its needle swelling in damp air as the hair of a hair
 * hygrometer does. The needle shows the air of the plants around it ({@link #HUMIDITY}): <b>the dampest plant out
 * of water within a Dehumidifier's reach of the dial</b>, as that plant feels it — the biome, its own bed (a
 * Hydro Tray), the water near it, under a roof a running Dehumidifier. With no plant there, the dial's own spot.
 * So a dial hung high on a hydro room's wall reads the trays below it, not the dry air at its own height. It
 * looks again every second.
 *
 * <p>A comparator reads something else on purpose: <b>that air before any Dehumidifier dries it</b> — nothing in
 * normal or dry air, {@value #DAMP_SIGNAL} when damp, 15 when soaked. Read after the Dehumidifier, a dial wired
 * to one would switch it off the moment it dried the room and on again the moment it stopped, every second; read
 * before, a plain wire from the dial runs a Dehumidifier exactly while the room needs it.
 */
public class HygrometerBlock extends HorizontalDirectionalBlock {
    /** The air as the needle shows it. */
    public static final EnumProperty<Humidity> HUMIDITY = EnumProperty.create("humidity", Humidity.class);
    /** How often the dial looks again, in ticks: a daylight detector's second. */
    public static final int READ_EVERY = 20;
    /** A comparator's signal from a dial in a room that is damp of itself. */
    public static final int DAMP_SIGNAL = 8;

    /** The dial for one facing north, on the wall south of it; turned for the rest. */
    private static final VoxelShape NORTH = Block.box(2.0D, 2.0D, 14.0D, 14.0D, 14.0D, 16.0D);
    private static final VoxelShape SOUTH = Block.box(2.0D, 2.0D, 0.0D, 14.0D, 14.0D, 2.0D);
    private static final VoxelShape EAST = Block.box(0.0D, 2.0D, 2.0D, 2.0D, 14.0D, 14.0D);
    private static final VoxelShape WEST = Block.box(14.0D, 2.0D, 2.0D, 16.0D, 14.0D, 14.0D);

    public HygrometerBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HUMIDITY, Humidity.NORMAL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HUMIDITY);
    }

    /** Hung on the wall clicked, facing out of it, as a wall sign is; refused on a floor or a ceiling. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        if (face.getAxis().isVertical()) {
            return null;
        }
        BlockState state = this.defaultBlockState().setValue(FACING, face);
        return state.canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos wall = pos.relative(facing.getOpposite());
        return world.getBlockState(wall).isFaceSturdy(world, wall, facing);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader world, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(world, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, world, ticks, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    @Override
    protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState old, boolean movedByPiston) {
        if (!old.is(this)) {
            world.scheduleTick(pos, this, 1);
        }
    }

    /** Looks at the air again, and again a second later. */
    @Override
    protected void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        Humidity now = feltAt(world, pos);
        if (now != state.getValue(HUMIDITY)) {
            world.setBlock(pos, state.setValue(HUMIDITY, now), Block.UPDATE_ALL);
        }
        world.updateNeighbourForOutputSignal(pos, this);
        world.scheduleTick(pos, this, READ_EVERY);
    }

    /** The needle of a dial at {@code pos}: the dampest plant around it, any Dehumidifier drying it counted. */
    public static Humidity feltAt(Level world, BlockPos pos) {
        return read(world, pos, true);
    }

    /** What a comparator reads off a dial at {@code pos}: the same plants' air, before any Dehumidifier dries it. */
    public static Humidity ownAt(Level world, BlockPos pos) {
        return read(world, pos, false);
    }

    /**
     * The air of the dampest plant out of water that a Dehumidifier standing at {@code dial} would reach, each plant
     * read at its root as the mildew roll reads it (its roof, its tray, the water near it), and dried by any running
     * Dehumidifier that reaches it if {@code driers}; the dial's own spot when no plant stands there.
     */
    private static Humidity read(Level world, BlockPos dial, boolean driers) {
        GrowClimate climate = GrowClimate.of(world.registryAccess());
        GrowClimate.Drier box = climate.dehumidifier();
        List<BlockPos> running = driers ? runningAround(world, dial, box) : List.of();
        int dampest = Integer.MIN_VALUE;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dy = -box.below(); dy <= box.above(); dy++) {
            for (int dx = -box.reach(); dx <= box.reach(); dx++) {
                for (int dz = -box.reach(); dz <= box.reach(); dz++) {
                    at.setWithOffset(dial, dx, dy, dz);
                    BlockState plant = world.getBlockState(at);
                    if (!plant.hasProperty(Mildew.PROPERTY) || world.getBlockState(at.below()).is(plant.getBlock())
                            || Climate.inWater(world, at)) {
                        continue;
                    }
                    boolean roofed = Mildew.roofed(world, at);
                    int steps = Climate.ownSteps(world, at, roofed, Climate.onTray(world, at), climate);
                    if (roofed && dries(running, at, box)) {
                        steps -= box.dries();
                    }
                    dampest = Math.max(dampest, steps);
                }
            }
        }
        return dampest == Integer.MIN_VALUE
                ? Climate.humidity(world, dial, Mildew.roofed(world, dial), false, driers)
                : Humidity.fromNormal(dampest);
    }

    /**
     * Every working Dehumidifier that could dry a plant in the box a Dehumidifier standing at {@code dial} reaches:
     * that box grown by a Dehumidifier's own reach. Found once a read, so no plant walks the box again.
     */
    private static List<BlockPos> runningAround(Level world, BlockPos dial, GrowClimate.Drier box) {
        List<BlockPos> found = new ArrayList<>();
        int across = 2 * box.reach();
        int up = box.above() + box.below();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dy = -up; dy <= up; dy++) {
            for (int dx = -across; dx <= across; dx++) {
                for (int dz = -across; dz <= across; dz++) {
                    at.setWithOffset(dial, dx, dy, dz);
                    if (DehumidifierBlock.working(world, at, world.getBlockState(at))) {
                        found.add(at.immutable());
                    }
                }
            }
        }
        return found;
    }

    /** Whether one of {@code running} reaches the plant rooted at {@code root}, as {@link Climate#drier} finds one. */
    private static boolean dries(List<BlockPos> running, BlockPos root, GrowClimate.Drier box) {
        for (BlockPos drier : running) {
            int dy = drier.getY() - root.getY();
            if (Math.abs(drier.getX() - root.getX()) <= box.reach() && Math.abs(drier.getZ() - root.getZ()) <= box.reach()
                    && dy >= -box.above() && dy <= box.below()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos, Direction direction) {
        return switch (ownAt(world, pos)) {
            case DRY, NORMAL -> 0;
            case DAMP -> DAMP_SIGNAL;
            case SOAKED -> 15;
        };
    }
}
