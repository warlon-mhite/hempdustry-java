package com.warlonmhite.hempdustry.block.entity.custom;

import com.warlonmhite.hempdustry.balance.GrowClimate;
import com.warlonmhite.hempdustry.block.custom.DehumidifierBlock;
import com.warlonmhite.hempdustry.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.function.Function;

/**
 * What a {@link DehumidifierBlock Dehumidifier} has gathered short of a level: the growth steps of the plants it
 * dried since its last one ({@link #collect}). The climate's {@code dehumidifier.steps_per_level} make a level;
 * the levels themselves are the block's, where the sight tube can show them.
 */
public class DehumidifierBlockEntity extends BlockEntity {
    private static final String STEPS_KEY = "steps";

    private int steps;
    // The loader's fluid handler over this tank, made on the first lookup and kept for the block's life, so a
    // pipe that looks it up twice in one transaction holds one snapshot of the tank, never two.
    private Object transferHandler;

    public DehumidifierBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DEHUMIDIFIER, pos, state);
    }

    /** One growth step of a plant this Dehumidifier dried: a level of condensate once enough have gathered. */
    public void collect(Level level) {
        int perLevel = GrowClimate.of(level.registryAccess()).dehumidifier().stepsPerLevel();
        steps++;
        if (steps >= perLevel) {
            steps = 0;
            DehumidifierBlock.condense(level, getBlockPos());
        }
        setChanged();
    }

    /** This tank's one fluid handler on the running loader, made by {@code make} the first time it is asked for. */
    @SuppressWarnings("unchecked")
    public <T> T transferHandler(Function<DehumidifierBlockEntity, T> make) {
        if (transferHandler == null) {
            transferHandler = make.apply(this);
        }
        return (T) transferHandler;
    }

    /** The levels in the tank now, as the block shows them. */
    public int levels() {
        BlockState state = getBlockState();
        return level != null && level.getBlockState(getBlockPos()).getBlock() instanceof DehumidifierBlock
                ? level.getBlockState(getBlockPos()).getValue(DehumidifierBlock.LEVEL)
                : state.getValue(DehumidifierBlock.LEVEL);
    }

    /** Writes {@code levels} into the tank, as a pipe's transaction commits it, and tells the comparators. */
    public void setLevels(int levels) {
        if (level == null) {
            return;
        }
        BlockState state = level.getBlockState(getBlockPos());
        if (state.getBlock() instanceof DehumidifierBlock && state.getValue(DehumidifierBlock.LEVEL) != levels) {
            level.setBlock(getBlockPos(), state.setValue(DehumidifierBlock.LEVEL, levels), Block.UPDATE_ALL);
        }
    }

    /** The steps gathered towards the next level. */
    public int steps() {
        return steps;
    }

    @Override
    protected void loadAdditional(ValueInput view) {
        super.loadAdditional(view);
        steps = view.getIntOr(STEPS_KEY, 0);
    }

    @Override
    protected void saveAdditional(ValueOutput view) {
        super.saveAdditional(view);
        if (steps > 0) {
            view.putInt(STEPS_KEY, steps);
        }
    }
}
