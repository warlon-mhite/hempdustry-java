package com.warlonmhite.hempdustry.test.fabric;

import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import com.warlonmhite.hempdustry.Hempdustry;
import com.warlonmhite.hempdustry.api.HempdustryEvents;
import com.warlonmhite.hempdustry.test.HempdustryGameTests;
import com.warlonmhite.hempdustry.test.TestPlatform;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.impl.recipe.sync.RecipeSyncImpl;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComposterBlock;

import java.nio.file.Path;
import java.util.List;

public final class FabricTestPlatform implements TestPlatform {
    private AddonProbe probe;

    @Override
    public String loaderName() {
        return "fabric";
    }

    @Override
    public List<RegistryDataLoader.RegistryData<?>> dynamicRegistries() {
        return DynamicRegistries.getDynamicRegistries();
    }

    @Override
    public List<Path> modRoots() {
        return roots(Hempdustry.MOD_ID);
    }

    @Override
    public List<Path> testModRoots() {
        return roots(HempdustryGameTests.NAMESPACE);
    }

    @Override
    public float compostChance(ItemLike item) {
        // Fabric's composting registry writes into vanilla's table, whose missing value is -1.
        return ComposterBlock.COMPOSTABLES.getFloat(item.asItem());
    }

    @Override
    public Flammability flammability(Block block) {
        // Fabric calls them burn and spread: the odds of catching fire, and of burning away.
        FlammableBlockRegistry.Entry entry = FlammableBlockRegistry.getDefaultInstance().get(block);
        return entry == null ? new Flammability(0, 0) : new Flammability(entry.getBurnChance(), entry.getSpreadChance());
    }

    @Override
    public boolean reachesAClient(RecipeHolder<?> recipe) {
        // What Fabric's recipe sync sends: every recipe of a serializer someone opted in, by anyone
        // (a recipe viewer opts in vanilla's own).
        return RecipeSyncImpl.isSynced(recipe.value().getSerializer());
    }

    @Override
    public ServerPlayer fakePlayer(ServerLevel level) {
        return FakePlayer.get(level);
    }

    @Override
    public synchronized AddonProbe addonProbe() {
        if (probe == null) {
            AddonProbe p = new AddonProbe();
            HempdustryEvents.ALLOW_SMOKE.register((player, contents, device) -> {
                p.heard("ALLOW_SMOKE", player, contents, device);
                return !p.refusing();
            });
            HempdustryEvents.AFTER_SMOKE.register((player, contents, device) -> p.heard("AFTER_SMOKE", player, contents, device));
            HempdustryEvents.AFTER_EAT.register((player, tier, quality) -> p.heard("AFTER_EAT", player, tier, quality));
            HempdustryEvents.AFTER_INFUSE.register((world, pos, strength, quality) -> p.heard("AFTER_INFUSE", world, pos, strength, quality));
            probe = p;
        }
        return probe;
    }

    /** Fabric has no crop-growth events: nothing of another mod's hears a crop grow there, ours or vanilla's. */
    @Override
    public CropGrowthProbe cropGrowthProbe() {
        return null;
    }

    private static List<Path> roots(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).orElseThrow().getRootPaths();
    }

    @Override
    public int[] insertThroughTwoLookupsAndAbort(ServerLevel level, BlockPos pos, Direction side, ItemStack first,
                                                 ItemStack second, boolean nested) {
        try (Transaction outer = Transaction.openOuter()) {
            int[] took;
            if (nested) {
                try (Transaction inner = outer.openNested()) {
                    took = twoLookups(level, pos, side, first, second, inner);
                    inner.commit();
                }
            } else {
                took = twoLookups(level, pos, side, first, second, outer);
            }
            outer.abort();
            return took;
        }
    }

    private static int[] twoLookups(ServerLevel level, BlockPos pos, Direction side, ItemStack first, ItemStack second,
                                    Transaction transaction) {
        Storage<ItemVariant> a = ItemStorage.SIDED.find(level, pos, side);
        long tookA = a.insert(ItemVariant.of(first), first.getCount(), transaction);
        Storage<ItemVariant> b = ItemStorage.SIDED.find(level, pos, side);
        long tookB = b.insert(ItemVariant.of(second), second.getCount(), transaction);
        return new int[]{(int) tookA, (int) tookB};
    }
}
