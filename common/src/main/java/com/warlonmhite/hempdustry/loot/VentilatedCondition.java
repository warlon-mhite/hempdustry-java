package com.warlonmhite.hempdustry.loot;

import com.mojang.serialization.MapCodec;
import com.warlonmhite.hempdustry.block.entity.custom.PlantRecordBlockEntity;
import java.util.Set;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * {@code hempdustry:ventilated} — passes for the root of a ripe plant that grew in a running Grow Fan's wind at
 * every step ({@link PlantRecordBlockEntity#ventilated}). The crop tables pay such a plant one stem more: wind
 * thickens a stalk. Only a root keeps a record, so the other segments of a tall plant never pass, and a break
 * with no record to read (a table rolled by a command) never does either.
 *
 * <pre>{ "condition": "hempdustry:ventilated" }</pre>
 */
public final class VentilatedCondition implements LootItemCondition {
    private static final VentilatedCondition INSTANCE = new VentilatedCondition();

    public static final MapCodec<VentilatedCondition> CODEC = MapCodec.unit(INSTANCE);

    private VentilatedCondition() {
    }

    @Override
    public MapCodec<VentilatedCondition> codec() {
        return ModLootEntryTypes.VENTILATED;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.BLOCK_ENTITY);
    }

    @Override
    public boolean test(LootContext context) {
        BlockEntity blockEntity = context.getOptional(LootContextParams.BLOCK_ENTITY);
        return blockEntity instanceof PlantRecordBlockEntity record && record.ventilated()
                && record.getBlockState().getBlock() instanceof CropBlock crop && crop.isMaxAge(record.getBlockState());
    }

    /** The condition as a loot table builder takes it. */
    public static LootItemCondition.Builder ventilated() {
        return () -> INSTANCE;
    }
}
