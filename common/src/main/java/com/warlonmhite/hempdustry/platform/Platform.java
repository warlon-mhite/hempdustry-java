package com.warlonmhite.hempdustry.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Path;
import java.util.function.Consumer;

/** Facts about the running game that only the loader knows. */
public interface Platform {

    /** The folder every mod's config file goes in. */
    Path configDir();

    /** Whether the mod {@code id} is loaded beside this one. */
    boolean isModLoaded(String id);

    /** This mod's version, as the loader read it from the mod's manifest. */
    String modVersion();

    /**
     * The loader's name as Modrinth files a version under it ({@code fabric}, {@code neoforge}): the
     * update checker asks for the builds made for the loader it runs on.
     */
    String loaderName();

    /**
     * The growth odds the loader's own crops read at {@code pos} for {@code crop}: vanilla's farmland
     * count and crowding halving, which NeoForge reworks to let a modded soil count as farmland. So a
     * hemp crop grows on each loader exactly as wheat does there.
     */
    float cropGrowthSpeed(BlockState crop, BlockGetter level, BlockPos pos);

    /**
     * Whether {@code enderman} would not see {@code player} meet its eyes for what the player wears:
     * vanilla's carved pumpkin, and on NeoForge also a modded mask and the anger event an addon may
     * cancel. Asked of a player who breaks Ender Haze, which provokes only the endermen this is false for.
     */
    boolean disguisedFrom(Player player, Enderman enderman);

    /**
     * Whether a crop's random-tick growth step happens, {@code roll} being our own odds' answer. NeoForge
     * puts the question to its {@code CropGrowEvent.Pre} listeners first, as vanilla crops do there, so a
     * season or growth-control mod can hold a hemp plant back or push it on; Fabric has no such event and
     * answers {@code roll}.
     */
    boolean canCropGrow(Level level, BlockPos pos, BlockState state, boolean roll);

    /**
     * Tells the loader's listeners that the crop at {@code pos}, {@code before} the step, has just grown
     * one age (NeoForge's {@code CropGrowEvent.Post}: Ars Nouveau's Agronomic Sourcelink earns its Source
     * from it). Nothing on Fabric.
     */
    void cropGrew(Level level, BlockPos pos, BlockState before);

    /**
     * {@code biome}'s rainfall and warmth as the running game has them: vanilla's own on Fabric, where Fabric
     * API's biome modifications write into them, and NeoForge's modified copy there, which is where a biome
     * modifier puts its change. Read by the grow climate, which makes a swamp damp and a desert dry.
     */
    Biome.ClimateSettings climateOf(Biome biome);

    /**
     * Whether {@code pack} is this mod's own data pack, the one its jar brings: the pack a datapack registry
     * entry or a data file says it came from. Fabric knows a mod's pack by the mod's id, NeoForge by
     * {@code mod/} and the ids of every mod in the jar.
     */
    boolean isOwnPack(KnownPack pack);

    /**
     * Hands {@code out} what {@code player} wears in the accessory slots only this loader has a mod for: Curios on
     * NeoForge, nothing on Fabric. Read for the smell of a worn backpack ({@code WornSlots}, which asks the mods
     * both loaders share itself).
     */
    void wornAccessories(Player player, Consumer<ItemStack> out);
}
