package com.warlonmhite.hempdustry.loot;

import com.warlonmhite.hempdustry.balance.Genetics;
import com.warlonmhite.hempdustry.strain.internal.Rolls;
import com.warlonmhite.hempdustry.strain.internal.Resins;
import com.warlonmhite.hempdustry.strain.internal.Bands;
import com.warlonmhite.hempdustry.strain.internal.Tiers;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.warlonmhite.hempdustry.strain.BudTier;
import com.warlonmhite.hempdustry.strain.PotencyBand;
import com.warlonmhite.hempdustry.strain.QualityGene;
import com.warlonmhite.hempdustry.strain.ResinStep;
import com.warlonmhite.hempdustry.strain.Strain;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * {@code hempdustry:roll_genetics} — gives a bud found as loot (a chest's, a treasure's) the genetics a grown one
 * has: a potency band, a tier and a resin step, each rolled from its strain's own sheets, as a plant of it would
 * roll them at planting and at its harvest. Nothing cared for the plant it came from, so its tier is rolled at the
 * strain's base luck and the plant's quality gene alone: no trims, no light, no tray, no player's luck.
 *
 * <p>The whole stack is one plant's. It takes exactly one draw of the loot table's random, whatever it rolls, and
 * rolls the rest on a random of its own seeded by it, so a pool after it draws what it always would. Anything that
 * is not a plant's bud passes through untouched.
 */
public final class RollGeneticsFunction extends LootItemConditionalFunction {

    public static final MapCodec<RollGeneticsFunction> CODEC = RecordCodecBuilder.mapCodec(instance ->
            commonFields(instance).apply(instance, RollGeneticsFunction::new));

    private RollGeneticsFunction(Optional<Holder<LootItemCondition>> condition) {
        super(condition);
    }

    @Override
    public MapCodec<RollGeneticsFunction> codec() {
        return ModLootEntryTypes.ROLL_GENETICS;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        var registries = context.getLevel().registryAccess();
        if (!SetPotencyFunction.isBud(stack, registries)) {
            return stack;
        }
        Optional<Strain> strain = Strain.fromBuds(registries, stack.getItem()).map(Holder::value);
        if (strain.isEmpty()) {
            return stack;
        }
        Strain sheet = strain.get();
        RandomSource random = RandomSource.create(context.getRandom().nextLong());
        PotencyBand band = Rolls.roll(sheet.traits().potency(), random);
        QualityGene gene = Rolls.roll(sheet.traits().quality(), random);
        Genetics genetics = Genetics.of(registries);
        BudTier tier = Tiers.roll(genetics, sheet.traits().quality().baseLuck() + Rolls.luck(gene), random);
        ResinStep resin = Rolls.roll(sheet.traits().resin(), random);
        Bands.stamp(stack, band);
        Tiers.stamp(stack, tier);
        return Resins.stamp(genetics, stack, resin, sheet.traits().resin().texture());
    }

    /** The function as a loot table builder takes it. */
    public static LootItemConditionalFunction.Builder<?> rollGenetics() {
        return simpleBuilder(RollGeneticsFunction::new);
    }
}
