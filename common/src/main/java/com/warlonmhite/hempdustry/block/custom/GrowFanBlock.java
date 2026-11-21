package com.warlonmhite.hempdustry.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * still air is stirred, a greenhouse's is as good as a field's. Fans do not add up; one is enough. And a plant
 * that grew in a fan's wind at every step thickens its stalk: one stem more at harvest
 * ({@code hempdustry:ventilated}), which a Grow Pot's plant, short of a stem, is glad of.
 *
 * <p>Its blades spin while it runs, drawn by an animated texture: the block needs no renderer of its own.
 */
public class GrowFanBlock extends FaceAttachedHorizontalDirectionalBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

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
