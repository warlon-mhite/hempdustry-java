package com.warlonmhite.hempdustry.block.custom;

import com.warlonmhite.hempdustry.block.entity.custom.DehumidifierBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Dehumidifier: a grow room's dry air, in a grey cabinet with a sight tube on the front. Powered by redstone
 * as the Grow Lamp is, it dries every roofed plant in its reach two humidity steps ({@link Climate#humidity}):
 * a hydro room's damp is gone, a greenhouse's normal air is dry. Under the open sky it can do nothing — the
 * weather is not a room.
 *
 * <p>The water it takes out of the air is the plants' own: each growth step of a plant it dries is one step
 * of condensate ({@link DehumidifierBlockEntity}), and enough steps fill a level of its three ({@link #LEVEL},
 * the sight tube). A level drips straight into a cauldron under it, if one stands there with room, as a pointed
 * dripstone's water does ({@code PointedDripstoneBlock#maybeTransferFluid}, then the cauldron's
 * {@code receiveStalactiteDrip}: a level at a time). Otherwise it stays in the tank, to be taken out as a cauldron's
 * water is ({@code CauldronInteraction}), a bottle a level or a bucket when full, or by a pipe. <b>Full, with nowhere to drip, it stops</b>, and the room goes back
 * to its own air.
 *
 * <p>Every change of the tank tells the comparators around it, as a cauldron's does, which reads its level
 * ({@code LayeredCauldronBlock#getAnalogOutputSignal}). No ticking: it fills from
 * the plants' steps, and drains when its neighbour below changes.
 */
public class DehumidifierBlock extends Block implements EntityBlock {
    public static final int MAX_LEVEL = 3;
    /** Condensate in the tank, as the sight tube shows it. */
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, MAX_LEVEL);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public DehumidifierBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false)
                .setValue(LEVEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, LEVEL);
    }

    /** Facing the player who sets it down, its sight tube towards them; running if the spot is already powered. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState()
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(POWERED, ctx.getLevel().hasNeighborSignal(ctx.getClickedPos()));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DehumidifierBlockEntity(pos, state);
    }

    /** The signal switches it at once; and whatever changed, a tank with water in it tries the block below. */
    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block block,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        if (world.isClientSide()) {
            return;
        }
        boolean powered = world.hasNeighborSignal(pos);
        BlockState now = powered != state.getValue(POWERED) ? state.setValue(POWERED, powered) : state;
        while (now.getValue(LEVEL) > 0 && drip(world, pos.below())) {
            now = now.setValue(LEVEL, now.getValue(LEVEL) - 1);
        }
        if (now != state) {
            // A tank that changed tells its comparators, which only a neighbour update reaches; the signal alone
            // is the client's to draw.
            world.setBlock(pos, now, now.getValue(LEVEL) != state.getValue(LEVEL) ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Whether the Dehumidifier standing as {@code state} at {@code pos} is drying the air: powered, and with room
     * for what it takes out — a level free in its tank, or a cauldron with room under it.
     */
    public static boolean working(BlockGetter world, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof DehumidifierBlock && state.getValue(POWERED)
                && (state.getValue(LEVEL) < MAX_LEVEL || takesADrip(world.getBlockState(pos.below())));
    }

    /**
     * A level of condensate made at {@code pos}: dripped into the block below if it takes one, else into the tank.
     * Nothing is kept where water boils away, as the Hydro Tray keeps none there.
     */
    public static void condense(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof DehumidifierBlock) || HydroTrayBlock.boilsAway(world, pos)) {
            return;
        }
        if (!drip(world, pos.below()) && state.getValue(LEVEL) < MAX_LEVEL) {
            world.setBlock(pos, state.setValue(LEVEL, state.getValue(LEVEL) + 1), Block.UPDATE_ALL);
        }
    }

    /**
     * Whether {@code below} takes a drop of condensate: a cauldron that is empty or holds water short of full, as a
     * pointed dripstone fills one. Nothing else — a Hydro Tray under a Dehumidifier could carry no plant, so a tank
     * dripping into it would be only another tank.
     */
    public static boolean takesADrip(BlockState below) {
        if (below.is(Blocks.CAULDRON)) {
            return true;
        }
        return below.is(Blocks.WATER_CAULDRON) && below.getValue(LayeredCauldronBlock.LEVEL) < LayeredCauldronBlock.MAX_FILL_LEVEL;
    }

    /** Drips one level into the block at {@code at}, if it takes one; whether it did. */
    private static boolean drip(Level world, BlockPos at) {
        BlockState below = world.getBlockState(at);
        if (!takesADrip(below) || HydroTrayBlock.boilsAway(world, at)) {
            return false;
        }
        BlockState filled = below.is(Blocks.CAULDRON)
                ? Blocks.WATER_CAULDRON.defaultBlockState()
                : below.setValue(LayeredCauldronBlock.LEVEL, below.getValue(LayeredCauldronBlock.LEVEL) + 1);
        world.setBlock(at, filled, Block.UPDATE_ALL);
        world.playSound(null, at, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON, SoundSource.BLOCKS, 1.0F, 1.0F);
        world.gameEvent(null, GameEvent.BLOCK_CHANGE, at);
        return true;
    }

    /**
     * Taken out as a cauldron's water is: a glass bottle takes a level as a water bottle, a bucket a full tank as a
     * water bucket. Anything else falls through to {@code super}, never a bare pass, which would swallow the click.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        int level = state.getValue(LEVEL);
        if (stack.is(Items.GLASS_BOTTLE) && level > 0) {
            if (!world.isClientSide()) {
                ItemStack water = new ItemStack(Items.POTION);
                water.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER));
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, water));
                player.awardStat(Stats.ITEM_USED.get(Items.GLASS_BOTTLE));
                world.setBlock(pos, state.setValue(LEVEL, level - 1), Block.UPDATE_ALL);
                world.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                world.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.BUCKET) && level == MAX_LEVEL) {
            if (!world.isClientSide()) {
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.WATER_BUCKET)));
                player.awardStat(Stats.ITEM_USED.get(Items.BUCKET));
                world.setBlock(pos, state.setValue(LEVEL, 0), Block.UPDATE_ALL);
                world.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                world.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
            }
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, world, pos, player, hand, hit);
    }

    /** A comparator reads the tank, a level to a step, as it reads a cauldron. */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos, Direction direction) {
        return state.getValue(LEVEL);
    }
}
