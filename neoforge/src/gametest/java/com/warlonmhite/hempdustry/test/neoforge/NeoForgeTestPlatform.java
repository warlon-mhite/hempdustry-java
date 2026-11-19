package com.warlonmhite.hempdustry.test.neoforge;

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
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.registries.DataPackRegistriesHooks;
import net.neoforged.neoforge.registries.datamaps.builtin.Compostable;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

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
        return DataPackRegistriesHooks.getDataPackRegistries();
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
    public float compostChance(ItemLike item) {
        Compostable compostable = item.asItem().builtInRegistryHolder().getData(NeoForgeDataMaps.COMPOSTABLES);
        return compostable == null ? -1.0F : compostable.chance();
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
    public ServerPlayer fakePlayer(ServerLevel level) {
        return FakePlayerFactory.getMinecraft(level);
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
}
