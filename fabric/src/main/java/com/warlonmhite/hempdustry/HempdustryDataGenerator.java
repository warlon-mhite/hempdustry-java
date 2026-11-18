package com.warlonmhite.hempdustry;

import com.warlonmhite.hempdustry.balance.DeviceStats;
import com.warlonmhite.hempdustry.balance.EdibleBundle;
import com.warlonmhite.hempdustry.balance.GreenOut;
import com.warlonmhite.hempdustry.datagen.*;
import com.warlonmhite.hempdustry.strain.ModStrains;
import com.warlonmhite.hempdustry.strain.Strain;
import com.warlonmhite.hempdustry.strain.Terpene;
import com.warlonmhite.hempdustry.world.ModConfiguredFeatures;
import com.warlonmhite.hempdustry.world.ModPlacedFeatures;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;

public class HempdustryDataGenerator implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
		FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();

		pack.addProvider(ModBlockTagProvider::new);
		pack.addProvider(ModItemTagProvider::new);
		pack.addProvider(ModLootTableProvider::new);
		pack.addProvider(ModModelProvider::new);   // client-side: needs configureDataGeneration { client = true }
		pack.addProvider(ModRecipeProvider::new);
		pack.addProvider(ModWorldGenerator::new);
		pack.addProvider(ModStrainProvider::new);
		pack.addProvider(ModBalanceProvider::new);
		pack.addProvider(ModAdvancementProvider::new);
		pack.addProvider(ModCompatProvider::new);
		// Not every tag comes from here. Hand-written under resources/: the worldgen tags (every *_gen
		// biome tag and echo_og_structures), and a few short tags of registries none of these providers
		// writes (game_event/hush_silences, mob_effect/counts_as_harmful, banner_pattern/pattern_item/hemp_leaf,
		// painting_variant/placeable), plus item/hash_cutters and block/concrete_powders. A provider for one
		// of those registries would take its tags over.
	}

	@Override
	public void buildRegistry(RegistrySetBuilder registryBuilder) {
		registryBuilder.add(Terpene.REGISTRY_KEY, Terpene::bootstrap);
		registryBuilder.add(Strain.REGISTRY_KEY, ModStrains::bootstrap);
		registryBuilder.add(DeviceStats.REGISTRY_KEY, DeviceStats::bootstrap);
		registryBuilder.add(EdibleBundle.REGISTRY_KEY, EdibleBundle::bootstrap);
		registryBuilder.add(GreenOut.REGISTRY_KEY, GreenOut::bootstrap);
		registryBuilder.add(Registries.CONFIGURED_FEATURE, ModConfiguredFeatures::bootstrap);
		registryBuilder.add(Registries.PLACED_FEATURE, ModPlacedFeatures::bootstrap);
	}
}
