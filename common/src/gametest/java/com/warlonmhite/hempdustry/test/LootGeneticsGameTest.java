package com.warlonmhite.hempdustry.test;

import com.warlonmhite.hempdustry.item.ModItems;
import com.warlonmhite.hempdustry.strain.BudTier;
import com.warlonmhite.hempdustry.strain.PotencyBand;
import com.warlonmhite.hempdustry.strain.ResinStep;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** A bud found as loot carries the genetics a grown one does, rolled from its own strain's sheets. */
public final class LootGeneticsGameTest {
    private LootGeneticsGameTest() {
    }

    /**
     * The Ancient City's Echo OG, the buried treasure's Maui Wowie and the bastion's Warped Kush come out with
     * bands, tiers and resin steps of their own, varied as their strains' sheets roll them: never all Dank and
     * Mid, as a bud with nothing written reads.
     */
    public static void aLootedBudCarriesItsStrainsGenetics(GameTestHelper context) {
        Map<ResourceKey<LootTable>, Item> sources = Map.of(
                BuiltInLootTables.ANCIENT_CITY, ModItems.ECHO_OG_BUDS,
                BuiltInLootTables.BURIED_TREASURE, ModItems.MAUI_WOWIE_BUDS,
                BuiltInLootTables.BASTION_TREASURE, ModItems.WARPED_KUSH_BUDS);
        for (Map.Entry<ResourceKey<LootTable>, Item> source : sources.entrySet()) {
            List<ItemStack> buds = buds(context, source.getKey(), source.getValue());
            String where = source.getKey().identifier() + "'s " + source.getValue();
            Set<BudTier> tiers = EnumSet.noneOf(BudTier.class);
            Set<PotencyBand> bands = EnumSet.noneOf(PotencyBand.class);
            Set<ResinStep> resin = EnumSet.noneOf(ResinStep.class);
            for (ItemStack bud : buds) {
                tiers.add(BudTier.of(bud));
                bands.add(PotencyBand.of(bud));
                resin.add(ResinStep.of(bud));
            }
            context.assertTrue(buds.size() >= 40, "only " + buds.size() + " " + where + " stacks in 4000 chests");
            context.assertTrue(tiers.size() >= 2 && bands.size() >= 2 && resin.size() >= 2,
                    where + " carry only tiers " + tiers + ", bands " + bands + ", resin " + resin);
        }
        context.succeed();
    }

    private static List<ItemStack> buds(GameTestHelper context, ResourceKey<LootTable> key, Item item) {
        ServerLevel world = context.getLevel();
        LootTable table = world.getServer().reloadableRegistries().getLootTable(key);
        LootParams params = new LootParams.Builder(world)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1))))
                .create(LootContextParamSets.CHEST);
        Random seeds = new Random(20261118L);
        List<ItemStack> found = new ArrayList<>();
        for (int i = 0; i < 4000; i++) {
            for (ItemStack stack : table.getRandomItems(params, seeds.nextLong())) {
                if (stack.is(item)) {
                    found.add(stack);
                }
            }
        }
        return found;
    }
}
