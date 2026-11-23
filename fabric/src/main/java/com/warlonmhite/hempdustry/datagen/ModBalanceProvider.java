package com.warlonmhite.hempdustry.datagen;

import com.warlonmhite.hempdustry.balance.DeviceStats;
import com.warlonmhite.hempdustry.balance.EdibleBundle;
import com.warlonmhite.hempdustry.balance.GreenOut;
import com.warlonmhite.hempdustry.balance.GrowClimate;
import com.warlonmhite.hempdustry.balance.Genetics;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.core.HolderLookup;
import java.util.concurrent.CompletableFuture;

/**
 * Writes the mod's own balance out as {@code data/hempdustry/hempdustry/device/*.json},
 * {@code edible/default.json}, {@code green_out/default.json}, {@code grow_climate/default.json} and
 * {@code genetics/default.json}. As with the strains, the generated files are the documentation: a server
 * owner who wants a gentler bong copies its file into their own datapack and edits it.
 */
public class ModBalanceProvider extends FabricDynamicRegistryProvider {
    public ModBalanceProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void configure(HolderLookup.Provider registries, Entries entries) {
        entries.addAll(registries.lookupOrThrow(DeviceStats.REGISTRY_KEY));
        entries.addAll(registries.lookupOrThrow(EdibleBundle.REGISTRY_KEY));
        entries.addAll(registries.lookupOrThrow(GreenOut.REGISTRY_KEY));
        entries.addAll(registries.lookupOrThrow(GrowClimate.REGISTRY_KEY));
        entries.addAll(registries.lookupOrThrow(Genetics.REGISTRY_KEY));
    }

    @Override
    public String getName() {
        return "Balance";
    }
}
