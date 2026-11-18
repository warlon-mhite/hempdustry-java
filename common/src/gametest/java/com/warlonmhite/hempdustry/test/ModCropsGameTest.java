package com.warlonmhite.hempdustry.test;

import com.warlonmhite.hempdustry.block.ModCrops;
import com.warlonmhite.hempdustry.item.custom.StrainPlantItem;
import com.warlonmhite.hempdustry.strain.Strain;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The crop list every other list of the mod is drawn from ({@link ModCrops}) holds every crop, once, and
 * agrees with the strains: a crop left out of it would get no plant record, no compost, no tint, no tag
 * and no seed loot, each failing somewhere else or nowhere at all.
 */
public final class ModCropsGameTest {
    private ModCropsGameTest() {
    }

    /** Every crop block and seed item of the mod is in the list, once, and each entry agrees with its strain. */
    public static void everyCropIsListedOnce(GameTestHelper context) {
        Map<String, String> findings = new TreeMap<>();
        int crops = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (!Sweep.ours(id) || !(block instanceof CropBlock)) {
                continue;
            }
            crops++;
            long listed = ModCrops.ALL.stream().filter(crop -> crop.crop() == block).count();
            if (listed != 1) {
                findings.put(id.toString(), "listed " + listed + " times in the crop list");
            }
        }
        Sweep.atLeast(context, "crop blocks", crops, 13);

        int seeds = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (!Sweep.ours(id) || !(item instanceof StrainPlantItem.Seeds planting)) {
                continue;
            }
            seeds++;
            // A wild plant picked whole plants itself again (Ditchweed): it must be a listed wild plant.
            if (ModCrops.wilds().anyMatch(wild -> wild.flower() == planting.getBlock())) {
                continue;
            }
            Optional<ModCrops.Crop> crop = ModCrops.of(planting.getBlock());
            if (crop.isEmpty() || crop.get().seeds() != item) {
                findings.put(id.toString(), "plants " + BuiltInRegistries.BLOCK.getKey(planting.getBlock())
                        + ", which the crop list does not give these seeds");
            }
        }
        Sweep.atLeast(context, "seed items", seeds, 14);

        for (ModCrops.Crop crop : ModCrops.ALL) {
            String id = BuiltInRegistries.BLOCK.getKey(crop.crop()).toString();
            if (!(crop.seeds() instanceof BlockItem planting) || planting.getBlock() != crop.crop()) {
                findings.put(id, "its listed seeds plant something else");
            }
            crop.buds().filter(buds -> !(buds instanceof StrainPlantItem.Buds))
                    .ifPresent(buds -> findings.put(id, BuiltInRegistries.ITEM.getKey(buds) + " is listed as its buds"));
        }

        // A strain of ours grown from a listed crop names the same buds and the same wild plant.
        int strains = 0;
        for (Holder.Reference<Strain> strain : Strain.all(context.getLevel().registryAccess())) {
            if (!Sweep.ours(strain.key().identifier())) {
                continue;
            }
            Optional<ModCrops.Crop> crop = strain.value().seedItem()
                    .flatMap(seed -> seed instanceof BlockItem planting ? ModCrops.of(planting.getBlock()) : Optional.empty());
            if (crop.isEmpty()) {
                continue;
            }
            strains++;
            String id = strain.key().identifier().toString();
            if (crop.get().buds().filter(buds -> buds == strain.value().buds()).isEmpty()) {
                findings.put(id, "its buds are not its crop's in the crop list");
            }
            // A strain with no wild plant of its own grows wild as its crop, and names that.
            Optional<Block> listedWild = Optional.of(crop.get().wild().map(ModCrops.Wild::flower).orElse(crop.get().crop()));
            if (!listedWild.equals(strain.value().wildFlower())) {
                findings.put(id, "its wild plant " + strain.value().wildFlower() + " is not the crop list's " + listedWild);
            }
        }
        Sweep.atLeast(context, "strains grown from a listed crop", strains, 12);

        Sweep.report(context, "every crop is in the crop list once", findings, Map.of());
        context.succeed();
    }
}
