package com.warlonmhite.hempdustry.test.neoforge;

import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.capabilities.Capabilities;
import com.mojang.authlib.GameProfile;
import com.warlonmhite.hempdustry.Hempdustry;
import com.warlonmhite.hempdustry.api.AfterEatEvent;
import com.warlonmhite.hempdustry.api.AfterInfuseEvent;
import com.warlonmhite.hempdustry.api.AfterSmokeEvent;
import com.warlonmhite.hempdustry.api.AllowSmokeEvent;
import com.warlonmhite.hempdustry.neoforge.NeoForgeRegistration;
import com.warlonmhite.hempdustry.platform.Services;
import com.warlonmhite.hempdustry.test.TestPlatform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;

import java.nio.file.Path;
import java.util.List;

public final class NeoForgeTestPlatform implements TestPlatform {
    private AddonProbe probe;
    private CropGrowthProbe growth;

    @Override
    public String loaderName() {
        return "neoforge";
    }

    @Override
    public List<RegistryDataLoader.RegistryData<?>> dynamicRegistries() {
        // NeoForge keeps vanilla's own list open and adds the mods' world registries to it.
        return RegistryDataLoader.WORLD_REGISTRIES;
    }

    @Override
    public List<Path> modRoots() {
        return roots(Hempdustry.MOD_ID);
    }

    @Override
    public List<Path> testModRoots() {
        return roots(NeoForgeGameTests.MOD_ID);
    }


    @Override
    public Flammability flammability(Block block) {
        // Vanilla's table, which NeoForge's fire reads unless a block says otherwise.
        FireBlock fire = (FireBlock) Blocks.FIRE;
        BlockState state = block.defaultBlockState();
        return new Flammability(fire.getIgniteOdds(state), fire.getBurnOdds(state));
    }

    @Override
    public boolean reachesAClient(RecipeHolder<?> recipe) {
        // NeoForge sends whole types: vanilla's are asked for by every recipe viewer, ours by the mod.
        RecipeType<?> type = recipe.value().getType();
        Identifier id = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        return id != null && id.getNamespace().equals("minecraft")
                || ((NeoForgeRegistration) Services.REGISTRATION).sendsRecipes(type);
    }

    @Override
    public ServerPlayer fakePlayer(ServerLevel level, GameProfile profile) {
        return FakePlayerFactory.get(level, profile);
    }

    @Override
    public synchronized AddonProbe addonProbe() {
        if (probe == null) {
            AddonProbe p = new AddonProbe();
            NeoForge.EVENT_BUS.addListener(AllowSmokeEvent.class, event -> {
                p.heard("ALLOW_SMOKE", event.getPlayer(), event.getContents(), event.getDevice());
                if (p.refusing()) {
                    event.setCanceled(true);
                }
            });
            NeoForge.EVENT_BUS.addListener(AfterSmokeEvent.class,
                    event -> p.heard("AFTER_SMOKE", event.getPlayer(), event.getContents(), event.getDevice()));
            NeoForge.EVENT_BUS.addListener(AfterEatEvent.class,
                    event -> p.heard("AFTER_EAT", event.getPlayer(), event.getTier(), event.getQuality()));
            NeoForge.EVENT_BUS.addListener(AfterInfuseEvent.class,
                    event -> p.heard("AFTER_INFUSE", event.getLevel(), event.getPos(), event.getStrength(), event.getQuality()));
            probe = p;
        }
        return probe;
    }

    @Override
    public synchronized CropGrowthProbe cropGrowthProbe() {
        if (growth == null) {
            CropGrowthProbe p = new CropGrowthProbe();
            NeoForge.EVENT_BUS.addListener(CropGrowEvent.Pre.class, event -> {
                p.heard("PRE", event.getPos());
                if (p.refusing(event.getPos())) {
                    event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
                }
            });
            NeoForge.EVENT_BUS.addListener(CropGrowEvent.Post.class, event -> p.heard("POST", event.getPos()));
            growth = p;
        }
        return growth;
    }

    private static List<Path> roots(String modId) {
        return List.copyOf(ModList.get().getModFileById(modId).getFile().getContents().getContentRoots());
    }

    @Override
    public long emptyRoom(ServerLevel level, BlockPos pos, Direction side) {
        ResourceHandler<ItemResource> handler = level.getCapability(Capabilities.Item.BLOCK, pos, side);
        long room = 0;
        if (handler != null) {
            for (int index = 0; index < handler.size(); index++) {
                if (handler.getResource(index).isEmpty()) {
                    room += handler.getCapacityAsLong(index, ItemResource.EMPTY) - handler.getAmountAsLong(index);
                }
            }
        }
        return room;
    }

    @Override
    public int[] insertThroughTwoLookupsAndAbort(ServerLevel level, BlockPos pos, Direction side, ItemStack first,
                                                 ItemStack second, boolean nested) {
        // Closing the root without a commit rolls it back.
        try (Transaction root = Transaction.openRoot()) {
            if (nested) {
                try (Transaction inner = Transaction.open(root)) {
                    int[] took = twoLookups(level, pos, side, first, second, inner);
                    inner.commit();
                    return took;
                }
            }
            return twoLookups(level, pos, side, first, second, root);
        }
    }

    private static int[] twoLookups(ServerLevel level, BlockPos pos, Direction side, ItemStack first, ItemStack second,
                                    Transaction transaction) {
        ResourceHandler<ItemResource> a = level.getCapability(Capabilities.Item.BLOCK, pos, side);
        int tookA = a.insert(ItemResource.of(first), first.getCount(), transaction);
        ResourceHandler<ItemResource> b = level.getCapability(Capabilities.Item.BLOCK, pos, side);
        int tookB = b.insert(ItemResource.of(second), second.getCount(), transaction);
        return new int[]{tookA, tookB};
    }

    @Override
    public int extractWater(ServerLevel level, BlockPos pos, Direction side, int bottles, int lookups, boolean abort) {
        // A bottle is a third of NeoForge's thousand-millibucket bucket; three ask for the whole bucket.
        int asked = bottles >= 3 ? 1000 * (bottles / 3) : bottles * 333;
        long taken = 0;
        try (Transaction root = Transaction.openRoot()) {
            for (int i = 0; i < lookups; i++) {
                ResourceHandler<FluidResource> handler = level.getCapability(Capabilities.Fluid.BLOCK, pos, side);
                if (handler == null) {
                    return -1;
                }
                taken += handler.extract(FluidResource.of(Fluids.WATER), asked, root);
            }
            if (!abort) {
                root.commit();
            }
        }
        // 333 mB is a bottle as much as 1000 is three: rounded, not cut.
        return (int) Math.round(taken * 3 / 1000.0);
    }
}
