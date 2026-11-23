package com.warlonmhite.hempdustry.test;

import com.warlonmhite.hempdustry.config.ModGameRules;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.warlonmhite.hempdustry.balance.GrowClimate;
import com.warlonmhite.hempdustry.block.ModBlocks;
import com.warlonmhite.hempdustry.block.custom.Defoliation;
import com.warlonmhite.hempdustry.block.custom.HydroTrayBlock;
import com.warlonmhite.hempdustry.block.custom.Mildew;
import com.warlonmhite.hempdustry.strain.Resistance;
import com.warlonmhite.hempdustry.strain.ResistanceGenes;
import com.warlonmhite.hempdustry.strain.StressPhenomenon;
import com.warlonmhite.hempdustry.item.ModItems;
import com.warlonmhite.hempdustry.util.ModTags;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.PotentSulfurBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.PotentSulfurState;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.phys.AABB;

/**
 * Powdery mildew ({@link Mildew}) and the climate it reads ({@link Climate}): where it starts, how it spreads,
 * what clears it, and which strains it and the sulfur gas leave alone. The odds are asked outright where a number
 * is the point ({@link Mildew#onsetFor}, {@link Mildew#spreadTo}); where the wiring is, every roll is decided
 * ({@link #HIT} lands under any odds above zero, {@link #MISS} under none), so each probe either certainly
 * catches or certainly does not, and what is asserted is the agreed numbers, never a measured rate.
 */
public final class MildewGameTest {
    private static final BlockPos SOIL = new BlockPos(1, 1, 1);
    private static final BlockPos CROP = SOIL.above();
    /** A block over the plant, its roof when there is one: glass lets the sun in, as a greenhouse's does. */
    private static final BlockPos ROOF = CROP.above(3);

    private static final RandomSource HIT = decided(0.0F, 0);
    private static final RandomSource MISS = decided(0.999F, 0);

    private static final float FIELD = 1.0F / 1500;

    /** The agreed numbers, in the code and in the data file the mod ships. */
    public static void theGrowClimateIsTheAgreedOne(GameTestHelper context) {
        GrowClimate climate = GrowClimate.BUILT_IN;
        context.assertTrue(climate.onset() == 1.0F / 1500 && climate.spread() == 1.0F / 100,
                "the climate's onset and spread are not 1 in 1,500 and 1 in 100: " + climate);
        context.assertTrue(climate.air().equals(new GrowClimate.Air(1.0F, 3.0F, 6.0F)),
                "air is not open 1, stirred 3, still 6: " + climate.air());
        context.assertTrue(climate.humidity().equals(new GrowClimate.Humidity(0.5F, 1.0F, 2.0F, 4.0F)),
                "humidity is not dry 1/2, normal 1, damp 2, soaked 4: " + climate.humidity());
        context.assertTrue(climate.biome().equals(new GrowClimate.BiomeRule(0.3F, 0.85F, 0.5F)),
                "the biome rule is not dry below 0.3 rain, wet from 0.85 rain at 0.5 warmth: " + climate.biome());
        context.assertTrue(climate.wetReach() == 2 && climate.trimmed() == 0.75F,
                "a wet room is not water within 2, or a trim does not take a quarter off: " + climate);
        context.assertTrue(GrowClimate.of(context.getLevel().registryAccess()).equals(climate),
                "the shipped grow_climate/default.json is not the built-in climate: "
                        + GrowClimate.of(context.getLevel().registryAccess()));
        context.succeed();
    }

    /**
     * A climate file may hold any part of the climate: every key it leaves out, at the top or inside an object,
     * reads as the built-in number, so a datapack that changes one number writes only that one. The mod's own file
     * is still written in full, every key in every object, and a number out of its range still refuses the file
     * rather than falling back without a word.
     */
    public static void aPartialGrowClimateLoads(GameTestHelper context) {
        GrowClimate built = GrowClimate.BUILT_IN;
        context.assertTrue(climate("{}").equals(built), "an empty climate file is not the built-in climate: " + climate("{}"));
        GrowClimate onset = climate("{\"onset_one_in\": 500}");
        context.assertTrue(onset.equals(new GrowClimate(500, built.spreadOneIn(), built.air(), built.humidity(),
                        built.biome(), built.wetReach(), built.trimmed(), built.fan(), built.dehumidifier(), built.stress(),
                        built.boneMealFailsOneIn())),
                "a file holding only onset_one_in did not keep every other number: " + onset);
        GrowClimate fan = climate("{\"fan\": {\"ahead\": 12}}");
        context.assertTrue(fan.fan().equals(new GrowClimate.Fan(12, 2, 3, 1))
                        && fan.equals(new GrowClimate(built.onsetOneIn(), built.spreadOneIn(), built.air(), built.humidity(),
                        built.biome(), built.wetReach(), built.trimmed(), fan.fan(), built.dehumidifier(), built.stress(),
                        built.boneMealFailsOneIn())),
                "a file holding only the fan's reach ahead did not keep every other number: " + fan);
        GrowClimate humidity = climate("{\"humidity\": {\"soaked\": 8.0}, \"dehumidifier\": {\"dries\": 1}}");
        context.assertTrue(humidity.humidity().equals(new GrowClimate.Humidity(0.5F, 1.0F, 2.0F, 8.0F))
                        && humidity.dehumidifier().equals(new GrowClimate.Drier(4, 2, 2, 1, 64))
                        && humidity.air().equals(built.air()) && humidity.biome().equals(built.biome()),
                "a file holding one number in two objects did not keep the rest of each: " + humidity);

        JsonObject written = GrowClimate.CODEC.encodeStart(JsonOps.INSTANCE, built)
                .getOrThrow().getAsJsonObject();
        context.assertTrue(written.keySet().equals(Set.of("onset_one_in", "spread_one_in", "air", "humidity",
                        "biome", "wet_reach", "trimmed", "fan", "dehumidifier", "stress", "bone_meal_fails_one_in")),
                "the built-in climate is not written in full: " + written.keySet());
        context.assertTrue(written.getAsJsonObject("fan").size() == 4 && written.getAsJsonObject("air").size() == 3
                        && written.getAsJsonObject("humidity").size() == 4 && written.getAsJsonObject("biome").size() == 3
                        && written.getAsJsonObject("dehumidifier").size() == 5 && written.getAsJsonObject("stress").size() == 5,
                "an object of the built-in climate is not written in full: " + written);
        context.assertTrue(climate(written.toString()).equals(built), "the built-in climate does not read back as itself");
        // Every key, one at a time, at the top and inside each object: the file without it is still the built-in climate.
        int keys = 0;
        for (String top : written.keySet()) {
            List<String> paths = written.get(top).isJsonObject()
                    ? written.getAsJsonObject(top).keySet().stream().map(inner -> top + "." + inner).toList() : List.of();
            for (String path : Stream.concat(Stream.of(top), paths.stream()).toList()) {
                JsonObject without = written.deepCopy();
                String[] parts = path.split("\\.");
                (parts.length == 1 ? without : without.getAsJsonObject(parts[0])).remove(parts[parts.length - 1]);
                var read = GrowClimate.CODEC.parse(JsonOps.INSTANCE, without);
                context.assertTrue(read.isSuccess() && read.getOrThrow().equals(built),
                        "a climate file without " + path + " is not the built-in climate: " + read);
                keys++;
            }
        }
        context.assertTrue(keys >= 35, "only " + keys + " climate keys were left out one at a time");
        context.assertTrue(GrowClimate.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseString("{\"fan\": {\"ahead\": 0}}")).isError(),
                "a fan reaching no block ahead loaded");
        context.succeed();
    }

    private static GrowClimate climate(String json) {
        return GrowClimate.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow();
    }

    /**
     * Onset: 1 in 1,500 a step times the plant's air. The open sky, a tree's leaves and a Grow Lamp are open air
     * (1); glass is stirred (3), the sun still reaching the plant; stone, hempcrete and a hung glowstone are still
     * (6), a sealed room. The step's own roll is wired to those odds.
     */
    public static void mildewFollowsTheAir(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        fillBiome(context, CROP, "minecraft:plains");
        openSky(context, CROP);
        near(context, odds(context, ModBlocks.INDICA_CROP, Blocks.AIR), FIELD, "a field plant under open sky");
        expect(context, step(context, ModBlocks.INDICA_CROP, 2, Blocks.AIR, decided(0.00066F, 0)), Mildew.MILDEWED,
                "a field plant under open sky, at 0.00066");
        expect(context, step(context, ModBlocks.INDICA_CROP, 2, Blocks.AIR, decided(0.00067F, 0)), Mildew.CLEAN,
                "a field plant under open sky, at 0.00067");
        near(context, odds(context, ModBlocks.INDICA_CROP, Blocks.OAK_LEAVES), FIELD, "a plant under a tree's leaves");
        near(context, odds(context, ModBlocks.INDICA_CROP, ModBlocks.GROW_LAMP), FIELD, "a plant under a Grow Lamp");
        context.assertTrue(context.getBlockState(ROOF).is(ModBlocks.GROW_LAMP), "the Grow Lamp over the plant did not stay, "
                + "so the probe under it proves nothing");

        // Glass under the test's own barrier ceiling: both let the sun through.
        context.setBlock(new BlockPos(CROP.getX(), 8, CROP.getZ()), Blocks.BARRIER);
        near(context, odds(context, ModBlocks.INDICA_CROP, Blocks.GLASS), 3 * FIELD, "a plant under a glass roof");
        near(context, odds(context, ModBlocks.SATIVA_CROP, Blocks.GLASS), 3 * FIELD, "a Lemon Haze plant under a glass roof");
        expect(context, step(context, ModBlocks.INDICA_CROP, 2, Blocks.GLASS, decided(0.00199F, 0)), Mildew.MILDEWED,
                "a plant under a glass roof, at 0.00199");
        expect(context, step(context, ModBlocks.INDICA_CROP, 2, Blocks.GLASS, decided(0.00201F, 0)), Mildew.CLEAN,
                "a plant under a glass roof, at 0.00201");
        expect(context, step(context, ModBlocks.INDICA_CROP, 4, Blocks.GLASS, HIT), Mildew.MILDEWED,
                "a plant under a glass roof");
        context.assertTrue(context.getBlockState(CROP.above()).getValue(Mildew.PROPERTY) == Mildew.MILDEWED,
                "a mildewed plant is drawn clean at the top: its upper half reads "
                        + context.getBlockState(CROP.above()).getValue(Mildew.PROPERTY));
        expect(context, step(context, ModBlocks.BELDIA_CROP, 1, Blocks.GLASS, HIT), Mildew.MILDEWED,
                "a Beldía seedling under a glass roof");

        // A roof that blocks the sun: the light under it has to settle before the plant reads it.
        BlockPos top = context.absolutePos(CROP.above());
        context.setBlock(ROOF, Blocks.STONE);
        context.startSequence()
                .thenWaitUntil(() -> context.assertTrue(!world.canSeeSky(top), "the stone over the plant still lets the sky in"))
                .thenExecute(() -> {
                    near(context, odds(context, ModBlocks.INDICA_CROP, Blocks.STONE), 6 * FIELD, "a plant under a stone roof");
                    near(context, odds(context, ModBlocks.INDICA_CROP, ModBlocks.HEMPCRETE_BLOCK), 6 * FIELD,
                            "a plant under a hempcrete roof");
                    near(context, odds(context, ModBlocks.SATIVA_CROP, Blocks.STONE), 6 * FIELD, "a Lemon Haze plant under a stone roof");
                    expect(context, step(context, ModBlocks.SATIVA_CROP, 5, Blocks.STONE, HIT), Mildew.MILDEWED,
                            "a Lemon Haze plant under a stone roof");
                    context.setBlock(ROOF, Blocks.GLOWSTONE);
                })
                .thenWaitUntil(() -> context.assertTrue(!world.canSeeSky(top), "the glowstone over the plant lets the sky in"))
                .thenExecute(() -> {
                    near(context, odds(context, ModBlocks.INDICA_CROP, Blocks.GLOWSTONE), 6 * FIELD, "a plant under a hung glowstone");

                    // Nothing of ours claims to resist it; the tag is a datapack's to fill.
                    context.assertTrue(BuiltInRegistries.BLOCK.get(ModTags.Blocks.MILDEW_RESISTANT).map(tag -> tag.size() == 0).orElse(true),
                            "#hempdustry:mildew_resistant names a crop of ours, which the mod never claims");

                    // Switched off: nothing catches it, and a plant that has it is clean on its next tick.
                    ConfigGameTest.withConfig("{ \"world\": { \"mildew\": false } }", () -> {
                        expect(context, step(context, ModBlocks.INDICA_CROP, 4, Blocks.GLOWSTONE, HIT), Mildew.CLEAN,
                                "a plant under a roof with world.mildew=false");
                        plant(context, ModBlocks.INDICA_CROP, 4, Mildew.MILDEWED);
                        Mildew.randomTick(world, context.absolutePos(CROP), MISS);
                        expect(context, context.getBlockState(CROP.above()).getValue(Mildew.PROPERTY), Mildew.CLEAN,
                                "a mildewed plant's random tick with world.mildew=false");
                    });
                    // The same off by the world's game rule, the file leaving it on.
                    GameRuleGameTest.withRule(context, ModGameRules.MILDEW, false, () -> {
                        expect(context, step(context, ModBlocks.INDICA_CROP, 4, Blocks.GLOWSTONE, HIT), Mildew.CLEAN,
                                "a plant under a roof with the mildew game rule off");
                        plant(context, ModBlocks.INDICA_CROP, 4, Mildew.MILDEWED);
                        Mildew.randomTick(world, context.absolutePos(CROP), MISS);
                        expect(context, context.getBlockState(CROP.above()).getValue(Mildew.PROPERTY), Mildew.CLEAN,
                                "a mildewed plant's random tick with the mildew game rule off");
                    });
                })
                .thenSucceed();
    }

    /**
     * Onset times the plant's humidity: normal 1 in a plains; one drier (½) in a desert; one wetter (2) in a
     * swamp or a jungle, but not on a snowy peak, which is rainy and cold; one wetter again on a Hydro Tray with
     * water in it, or under a roof with water standing within two blocks; never past soaked (4).
     */
    public static void mildewFollowsTheDamp(GameTestHelper context) {
        openSky(context, CROP);
        Block indica = ModBlocks.INDICA_CROP;
        for (String[] biome : new String[][]{{"minecraft:plains", "1"}, {"minecraft:swamp", "2"}, {"minecraft:jungle", "2"},
                {"minecraft:desert", "0.5"}, {"minecraft:frozen_peaks", "1"}}) {
            fillBiome(context, CROP, biome[0]);
            near(context, odds(context, indica, Blocks.AIR), Float.parseFloat(biome[1]) * FIELD, "a field plant in " + biome[0]);
        }

        // A Hydro Tray holding water is a wet bed, even under the sky; a dry one is not.
        for (String[] biome : new String[][]{{"minecraft:plains", "2"}, {"minecraft:swamp", "4"}, {"minecraft:desert", "1"}}) {
            fillBiome(context, CROP, biome[0]);
            near(context, onTray(context, indica, 3), Float.parseFloat(biome[1]) * FIELD,
                    "a plant on a full Hydro Tray under open sky in " + biome[0]);
        }
        fillBiome(context, CROP, "minecraft:plains");
        near(context, onTray(context, indica, 0), FIELD, "a plant on a dry Hydro Tray under open sky");

        // Water beside a field plant changes nothing: it is the sky's.
        BlockPos pool = SOIL.east(2);
        pool(context, pool);
        near(context, odds(context, indica, Blocks.AIR), FIELD, "a field plant two blocks from a pool");

        // Under glass, the same pool makes a wet room; three blocks away it does not.
        context.setBlock(new BlockPos(CROP.getX(), 8, CROP.getZ()), Blocks.BARRIER);
        near(context, odds(context, indica, Blocks.GLASS), 3 * 2 * FIELD, "a plant under glass two blocks from a pool");
        context.setBlock(pool, Blocks.STONE);
        pool(context, SOIL.east(3));
        near(context, odds(context, indica, Blocks.GLASS), 3 * FIELD, "a plant under glass three blocks from a pool");

        // Soaked is the most: a swamp's tray under glass, with a pool beside it too.
        fillBiome(context, CROP, "minecraft:swamp");
        context.setBlock(SOIL.east(3), Blocks.STONE);
        pool(context, pool);
        context.setBlock(ROOF, Blocks.GLASS);
        near(context, onTray(context, indica, 3), 3 * 4 * FIELD, "a plant on a full tray under glass in a swamp, by a pool");
        fillBiome(context, CROP, "minecraft:plains");
        context.succeed();
    }

    /**
     * A plant in water never catches it and is never reached: Maui Wowie, every block of which holds water. And a
     * plant trimmed in either window takes three quarters of the odds.
     */
    public static void waterAndTrimsKeepMildewOff(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        fillBiome(context, CROP, "minecraft:plains");
        context.setBlock(ROOF, Blocks.GLASS);
        BlockState maui = Defoliation.unworked(ModBlocks.MAUI_WOWIE_CROP.defaultBlockState());
        context.setBlock(CROP, maui);
        BlockPos at = context.absolutePos(CROP);
        context.assertTrue(world.getFluidState(at).is(net.minecraft.tags.FluidTags.WATER),
                "test setup: a Maui Wowie holds no water");
        near(context, Mildew.onsetFor(world, at, world.getBlockState(at)), 0.0F, "a Maui Wowie's onset");
        near(context, Mildew.spreadTo(world, at, world.getBlockState(at)), 0.0F, "the spread to a Maui Wowie");
        expect(context, Mildew.afterStep(world, at, world.getBlockState(at), Mildew.CLEAN, HIT), Mildew.CLEAN,
                "a Maui Wowie's growth step, hit,");
        context.setBlock(CROP, Blocks.AIR);

        for (BooleanProperty window : List.of(Defoliation.TRIMMED_EARLY, Defoliation.TRIMMED_LATE)) {
            plant(context, ModBlocks.INDICA_CROP, 5, Mildew.CLEAN);
            context.setBlock(CROP, context.getBlockState(CROP).setValue(window, true));
            near(context, Mildew.onsetFor(world, at, world.getBlockState(at)), 0.75F * 3 * FIELD,
                    "a plant under glass trimmed in its " + window.getName() + " window");
            near(context, Mildew.spreadTo(world, at, world.getBlockState(at)), 0.75F * 3 / 100.0F,
                    "the spread to a plant under glass trimmed in its " + window.getName() + " window");
        }
        context.succeed();
    }

    /**
     * The strains that never catch it: Industrial Hemp (no bud for it to spoil, no shears to clear it)
     * and the two Nether plants (too hot and dry, and under a ceiling that would roof every one), by
     * {@link Mildew.Host}; Ruderalis and Ender Haze carry no mildew record at all. The step's own roll,
     * under a glass roof and under open sky, on a hit -- Purple Kush the control -- and the spread asks the
     * same thing of the neighbour it picks.
     */
    public static void strainsWithoutMildewNeverCatchIt(GameTestHelper context) {
        context.setBlock(ROOF, Blocks.GLASS);
        ServerLevel world = context.getLevel();
        BlockPos lower = context.absolutePos(CROP);
        expect(context, Mildew.afterStep(world, lower, ModBlocks.INDICA_CROP.defaultBlockState(), Mildew.CLEAN, HIT),
                Mildew.MILDEWED, "the control, a Purple Kush seedling under a glass roof,");
        for (Block crop : List.of(ModBlocks.INDUSTRIAL_HEMP_CROP, ModBlocks.WARPED_KUSH_CROP, ModBlocks.CRIMSON_CROP,
                ModBlocks.RUDERALIS_CROP, ModBlocks.ENDER_HAZE_CROP)) {
            expect(context, Mildew.afterStep(world, lower, crop.defaultBlockState(), Mildew.CLEAN, HIT), Mildew.CLEAN,
                    crop.getDescriptionId() + " under a glass roof");
        }

        // Nor under open sky, at a field's odds.
        context.setBlock(ROOF, Blocks.AIR);
        openSky(context, CROP);
        expect(context, Mildew.afterStep(world, lower, ModBlocks.INDICA_CROP.defaultBlockState(), Mildew.CLEAN, HIT),
                Mildew.MILDEWED, "the control, a Purple Kush seedling under open sky,");
        for (Block crop : List.of(ModBlocks.INDUSTRIAL_HEMP_CROP, ModBlocks.WARPED_KUSH_CROP, ModBlocks.CRIMSON_CROP,
                ModBlocks.RUDERALIS_CROP, ModBlocks.ENDER_HAZE_CROP)) {
            expect(context, Mildew.afterStep(world, lower, crop.defaultBlockState(), Mildew.CLEAN, HIT), Mildew.CLEAN,
                    crop.getDescriptionId() + " under open sky");
        }

        // Nor from a mildewed neighbour: the spread asks the plant it reaches.
        sow(context, CROP, ModBlocks.INDICA_CROP, 4, Mildew.MILDEWED);
        BlockPos beside = CROP.east();
        openSky(context, beside);
        sow(context, beside, ModBlocks.INDUSTRIAL_HEMP_CROP, 4, Mildew.CLEAN);
        Mildew.randomTick(world, lower, decided(0.0F, EAST));
        expect(context, context.getBlockState(beside).getValue(Mildew.PROPERTY), Mildew.CLEAN,
                "Industrial Hemp beside a mildewed plant, picked and hit,");
        context.succeed();
    }

    public static void mildewSpreadsOnePlantAtATime(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos sick = new BlockPos(2, 2, 2);
        BlockPos east = sick.east();
        BlockPos twoAway = sick.east(2).south(2);
        fillBiome(context, sick, "minecraft:plains");
        roof(context);
        for (BlockPos pos : List.of(sick, east, twoAway, sick.west())) {
            sow(context, pos, ModBlocks.INDICA_CROP, 4, Mildew.CLEAN);
        }
        sow(context, sick, ModBlocks.INDICA_CROP, 4, Mildew.MILDEWED);
        // The ripe neighbour is past it.
        sow(context, sick.west(), ModBlocks.INDICA_CROP, 7, Mildew.CLEAN);

        // The neighbour it picks, on a hit; and nothing on a miss.
        Mildew.randomTick(world, context.absolutePos(sick), decided(0.999F, EAST));
        expect(context, context.getBlockState(east).getValue(Mildew.PROPERTY), Mildew.CLEAN, "a missed spread");
        Mildew.randomTick(world, context.absolutePos(sick), decided(0.0F, EAST));
        expect(context, context.getBlockState(east).getValue(Mildew.PROPERTY), Mildew.MILDEWED, "the east neighbour, picked and hit");
        expect(context, context.getBlockState(east.above()).getValue(Mildew.PROPERTY), Mildew.MILDEWED,
                "the east neighbour's upper half");
        Mildew.randomTick(world, context.absolutePos(sick), decided(0.0F, WEST));
        expect(context, context.getBlockState(sick.west()).getValue(Mildew.PROPERTY), Mildew.CLEAN, "a ripe neighbour");

        // Every pick, all hits: a plant two blocks away is never reached — a gap stops it.
        for (int pick = 0; pick < 8; pick++) {
            Mildew.randomTick(world, context.absolutePos(sick), decided(0.0F, pick));
        }
        expect(context, context.getBlockState(twoAway).getValue(Mildew.PROPERTY), Mildew.CLEAN,
                "a plant two blocks from the mildewed one");

        // Out from under the roof, a neighbour catches it at 1 in 100: the one that catches it decides.
        context.setBlock(east.above(3), Blocks.AIR);
        openSky(context, east);
        sow(context, east, ModBlocks.INDICA_CROP, 4, Mildew.CLEAN);
        Mildew.randomTick(world, context.absolutePos(sick), decided(0.0101F, EAST));
        expect(context, context.getBlockState(east).getValue(Mildew.PROPERTY), Mildew.CLEAN,
                "a neighbour under open sky, from under the roof, at 0.0101");
        Mildew.randomTick(world, context.absolutePos(sick), decided(0.0099F, EAST));
        expect(context, context.getBlockState(east).getValue(Mildew.PROPERTY), Mildew.MILDEWED,
                "a neighbour under open sky, from under the roof, at 0.0099");

        // And the other way: the open-sky plant, mildewed, reaches its neighbour under glass at glass's 3 in 100.
        for (float roll : new float[]{0.0301F, 0.0299F}) {
            sow(context, sick, ModBlocks.INDICA_CROP, 4, Mildew.CLEAN);
            sow(context, east, ModBlocks.INDICA_CROP, 4, Mildew.MILDEWED);
            Mildew.randomTick(world, context.absolutePos(east), decided(roll, WEST));
            expect(context, context.getBlockState(sick).getValue(Mildew.PROPERTY), roll < 0.03F ? Mildew.MILDEWED : Mildew.CLEAN,
                    "a neighbour under glass of a mildewed field plant, at " + roll);
        }
        context.succeed();
    }

    /**
     * A plant's mildew resistance scales its onset (1 in 500 at neutral under glass: very weak 1 in 250, very
     * resistant 1 in 2,000; 1 in 1,500 outdoors: 1 in 750, 1 in 6,000) and the spread to it (3 in 100 at neutral
     * under glass: very weak 6 in 100, very resistant 3 in 400; 1 in 100 outdoors: 1 in 50, 1 in 400).
     */
    public static void mildewResistanceScalesOnsetAndSpread(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Block indica = ModBlocks.INDICA_CROP;
        fillBiome(context, CROP, "minecraft:plains");
        float[][] onsets = {{0.0039F, 0.0041F}, {0.00199F, 0.00201F}, {0.00049F, 0.00051F}};
        float[][] fieldOnsets = {{0.00133F, 0.00134F}, {0.00066F, 0.00067F}, {0.000166F, 0.000167F}};
        Resistance[] steps = {Resistance.VERY_WEAK, Resistance.NEUTRAL, Resistance.VERY_RESISTANT};
        for (int i = 0; i < steps.length; i++) {
            ResistanceGenes genes = ResistanceGenes.NEUTRAL.with(StressPhenomenon.MILDEW, steps[i]);
            expect(context, onset(context, indica, genes, Blocks.GLASS, onsets[i][0]), Mildew.MILDEWED,
                    "a plant " + steps[i] + " to mildew under a roof, at " + onsets[i][0]);
            expect(context, onset(context, indica, genes, Blocks.GLASS, onsets[i][1]), Mildew.CLEAN,
                    "a plant " + steps[i] + " to mildew under a roof, at " + onsets[i][1]);
        }
        openSky(context, CROP);
        for (int i = 0; i < steps.length; i++) {
            ResistanceGenes genes = ResistanceGenes.NEUTRAL.with(StressPhenomenon.MILDEW, steps[i]);
            expect(context, onset(context, indica, genes, Blocks.AIR, fieldOnsets[i][0]), Mildew.MILDEWED,
                    "a plant " + steps[i] + " to mildew under open sky, at " + fieldOnsets[i][0]);
            expect(context, onset(context, indica, genes, Blocks.AIR, fieldOnsets[i][1]), Mildew.CLEAN,
                    "a plant " + steps[i] + " to mildew under open sky, at " + fieldOnsets[i][1]);
        }
        context.setBlock(new BlockPos(CROP.getX(), 8, CROP.getZ()), Blocks.BARRIER);

        BlockPos sick = new BlockPos(2, 2, 2);
        BlockPos east = sick.east();
        roof(context);
        float[][] spreads = {{0.059F, 0.061F}, {0.029F, 0.031F}, {0.0074F, 0.0076F}};
        for (int i = 0; i < steps.length; i++) {
            for (int hit = 0; hit < 2; hit++) {
                sow(context, east, indica, 4, Mildew.CLEAN);
                Genes.set(context, east, ResistanceGenes.NEUTRAL.with(StressPhenomenon.MILDEW, steps[i]));
                sow(context, sick, indica, 4, Mildew.MILDEWED);
                Mildew.randomTick(world, context.absolutePos(sick), decided(spreads[i][hit], EAST));
                expect(context, context.getBlockState(east).getValue(Mildew.PROPERTY), hit == 0 ? Mildew.MILDEWED : Mildew.CLEAN,
                        "a neighbour " + steps[i] + " to mildew, at " + spreads[i][hit]);
            }
        }

        // The neighbour under open sky.
        context.setBlock(east.above(3), Blocks.AIR);
        openSky(context, east);
        float[][] fieldSpreads = {{0.0199F, 0.0201F}, {0.0099F, 0.0101F}, {0.00249F, 0.00251F}};
        for (int i = 0; i < steps.length; i++) {
            for (int hit = 0; hit < 2; hit++) {
                sow(context, east, indica, 4, Mildew.CLEAN);
                Genes.set(context, east, ResistanceGenes.NEUTRAL.with(StressPhenomenon.MILDEW, steps[i]));
                sow(context, sick, indica, 4, Mildew.MILDEWED);
                Mildew.randomTick(world, context.absolutePos(sick), decided(fieldSpreads[i][hit], EAST));
                expect(context, context.getBlockState(east).getValue(Mildew.PROPERTY), hit == 0 ? Mildew.MILDEWED : Mildew.CLEAN,
                        "a neighbour under open sky " + steps[i] + " to mildew, at " + fieldSpreads[i][hit]);
            }
        }
        context.succeed();
    }

    /** One bone meal step under {@code roof} on a fresh plant at age 4 with {@code genes}, at {@code roll}. */
    private static Mildew onset(GameTestHelper context, Block crop, ResistanceGenes genes, Block roof, float roll) {
        context.setBlock(ROOF, roof);
        plant(context, crop, 4, Mildew.CLEAN);
        Genes.set(context, CROP, genes);
        ServerLevel world = context.getLevel();
        BlockPos pos = context.absolutePos(CROP);
        ((CropBlock) crop).performBonemeal(world, decided(roll, 0), pos, world.getBlockState(pos), BonemealSource.INTERACTION);
        return context.getBlockState(CROP).getValue(Mildew.PROPERTY);
    }

    /**
     * Vanilla's sulfur gas: a potent sulfur block under a water source gives off gas at the surface,
     * and every plant within three blocks of it with nothing solid between is in it.
     */
    public static void sulfurGasClearsAndPreventsMildew(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        // The pool: potent sulfur at y 1, one water source over it, the gas rising at y 3.
        BlockPos sulfur = new BlockPos(1, 1, 4);
        // The sulfur first: the water landing on it is the neighbour update that wets it.
        context.setBlock(sulfur, Blocks.POTENT_SULFUR);
        context.setBlock(sulfur.above(), Blocks.WATER);
        PotentSulfurState gas = context.getBlockState(sulfur).getValue(PotentSulfurBlock.STATE);
        context.assertTrue(gas == PotentSulfurState.WET || gas == PotentSulfurState.DORMANT,
                "potent sulfur under water is " + gas + ", not giving off gas, so this probe proves nothing");
        BlockPos near = new BlockPos(2, 2, 3);
        roof(context);

        BlockPos lower = context.absolutePos(near);
        sow(context, near, ModBlocks.INDICA_CROP, 2, Mildew.MILDEWED);
        Mildew.randomTick(world, lower, HIT);
        expect(context, context.getBlockState(near).getValue(Mildew.PROPERTY), Mildew.CLEAN, "a young mildewed plant in the gas");
        sow(context, near, ModBlocks.INDICA_CROP, 5, Mildew.MILDEWED);
        Mildew.randomTick(world, lower, HIT);
        expect(context, context.getBlockState(near).getValue(Mildew.PROPERTY), Mildew.SULFURED, "a flowering mildewed plant in the gas");
        expect(context, context.getBlockState(near.above()).getValue(Mildew.PROPERTY), Mildew.SULFURED,
                "the upper half of a flowering plant in the gas");
        sow(context, near, ModBlocks.INDICA_CROP, 4, Mildew.CLEAN);
        Mildew.randomTick(world, lower, HIT);
        expect(context, context.getBlockState(near).getValue(Mildew.PROPERTY), Mildew.SULFURED, "a clean flowering plant in the gas");
        sow(context, near, ModBlocks.INDICA_CROP, 2, Mildew.CLEAN);
        Mildew.randomTick(world, lower, HIT);
        expect(context, context.getBlockState(near).getValue(Mildew.PROPERTY), Mildew.CLEAN, "a clean young plant in the gas");
        sow(context, near, ModBlocks.INDICA_CROP, 7, Mildew.CLEAN);
        Mildew.randomTick(world, lower, HIT);
        expect(context, context.getBlockState(near).getValue(Mildew.PROPERTY), Mildew.CLEAN, "a ripe plant in the gas");

        // No onset in the gas, whatever the roll.
        sow(context, near, ModBlocks.INDICA_CROP, 2, Mildew.CLEAN);
        ((CropBlock) ModBlocks.INDICA_CROP).performBonemeal(world, HIT, lower, world.getBlockState(lower), BonemealSource.INTERACTION);
        expect(context, context.getBlockState(near).getValue(Mildew.PROPERTY), Mildew.CLEAN, "a growth step in the gas");

        // Under open sky the gas does the same: a field beside a pool is cleared, and sulfured in flower.
        context.setBlock(near.above(3), Blocks.AIR);
        openSky(context, near);
        sow(context, near, ModBlocks.INDICA_CROP, 5, Mildew.MILDEWED);
        Mildew.randomTick(world, lower, HIT);
        expect(context, context.getBlockState(near).getValue(Mildew.PROPERTY), Mildew.SULFURED,
                "a flowering mildewed field plant in the gas");
        sow(context, near, ModBlocks.INDICA_CROP, 2, Mildew.MILDEWED);
        Mildew.randomTick(world, lower, HIT);
        expect(context, context.getBlockState(near).getValue(Mildew.PROPERTY), Mildew.CLEAN, "a young mildewed field plant in the gas");
        sow(context, near, ModBlocks.INDICA_CROP, 4, Mildew.CLEAN);
        Mildew.randomTick(world, lower, HIT);
        expect(context, context.getBlockState(near).getValue(Mildew.PROPERTY), Mildew.SULFURED, "a clean flowering field plant in the gas");
        sow(context, near, ModBlocks.INDICA_CROP, 2, Mildew.CLEAN);
        ((CropBlock) ModBlocks.INDICA_CROP).performBonemeal(world, HIT, lower, world.getBlockState(lower), BonemealSource.INTERACTION);
        expect(context, context.getBlockState(near).getValue(Mildew.PROPERTY), Mildew.CLEAN, "a field plant's growth step in the gas");

        // A strain that never catches mildew is never sulfured either: it could not show it, and its buds would pay.
        // Each on its own soil, set straight in: a random tick would take a Warped Kush off farmland.
        for (Block crop : List.of(ModBlocks.WARPED_KUSH_CROP, ModBlocks.INDUSTRIAL_HEMP_CROP)) {
            for (int y = 2; y >= 0; y--) {
                context.setBlock(near.above(y), Blocks.AIR);
            }
            context.setBlock(near.below(), crop == ModBlocks.WARPED_KUSH_CROP ? Blocks.SOUL_SAND.defaultBlockState()
                    : Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE));
            context.setBlock(near, Defoliation.unworked(crop.defaultBlockState()).setValue(BlockStateProperties.AGE_7, 5));
            context.assertTrue(context.getBlockState(near).getValue(Mildew.PROPERTY) == Mildew.CLEAN,
                    "test setup: " + crop.getDescriptionId() + " came up mildewed");
            Mildew.randomTick(world, lower, HIT);
            expect(context, context.getBlockState(near).getValue(Mildew.PROPERTY), Mildew.CLEAN,
                    crop.getDescriptionId() + ", flowering in the gas in the open,");
        }

        // A wall between the pool and the plant keeps the gas off it.
        BlockPos walled = new BlockPos(1, 2, 2);
        context.setBlock(walled.south(), Blocks.STONE);
        context.setBlock(walled.south().above(), Blocks.STONE);
        sow(context, walled, ModBlocks.INDICA_CROP, 5, Mildew.MILDEWED);
        Mildew.randomTick(world, context.absolutePos(walled), decided(0.999F, 0));
        expect(context, context.getBlockState(walled).getValue(Mildew.PROPERTY), Mildew.MILDEWED, "a plant walled off from the gas");

        // Too far: four blocks out from the surface.
        BlockPos far = new BlockPos(5, 2, 4);
        sow(context, far, ModBlocks.INDICA_CROP, 5, Mildew.MILDEWED);
        Mildew.randomTick(world, context.absolutePos(far), decided(0.999F, 0));
        expect(context, context.getBlockState(far).getValue(Mildew.PROPERTY), Mildew.MILDEWED, "a plant four blocks from the gas");

        // Drain the pool and the gas is gone.
        context.setBlock(sulfur.above(), Blocks.AIR);
        context.assertTrue(context.getBlockState(sulfur).getValue(PotentSulfurBlock.STATE) == PotentSulfurState.DRY,
                "potent sulfur with its water gone is " + context.getBlockState(sulfur).getValue(PotentSulfurBlock.STATE));
        sow(context, near, ModBlocks.INDICA_CROP, 5, Mildew.MILDEWED);
        Mildew.randomTick(world, lower, decided(0.999F, 0));
        expect(context, context.getBlockState(near).getValue(Mildew.PROPERTY), Mildew.MILDEWED, "a plant beside a drained pool");
        context.succeed();
    }

    /** A trim takes the diseased leaves away: the mildew goes, the window closes, and no leaf comes off. */
    public static void shearsClearMildewInATrimWindow(GameTestHelper context) {
        ServerPlayer player = Players.survival(context);
        roof(context);
        sow(context, CROP, ModBlocks.INDICA_CROP, 3, Mildew.MILDEWED);
        context.assertTrue(Players.click(context, player, CROP, new ItemStack(Items.SHEARS)).consumesAction(),
                "shears on a mildewed plant in its early window were turned down");
        expect(context, context.getBlockState(CROP).getValue(Mildew.PROPERTY), Mildew.CLEAN, "a trimmed mildewed plant");
        context.assertTrue(context.getBlockState(CROP).getValue(Defoliation.TRIMMED_EARLY),
                "the cure did not count as the early trim");
        context.assertTrue(leavesDropped(context) == 0, "a trim of a mildewed plant handed over "
                + leavesDropped(context) + " leaves; the diseased ones are thrown away");

        // The rub at 6-7 is no trim: it leaves the mildew where it is.
        sow(context, CROP, ModBlocks.INDICA_CROP, 6, Mildew.MILDEWED);
        Players.click(context, player, CROP, new ItemStack(Items.SHEARS));
        expect(context, context.getBlockState(CROP).getValue(Mildew.PROPERTY), Mildew.MILDEWED, "a rubbed mildewed plant");
        context.succeed();
    }

    // ----- helpers -----

    private static final int WEST = 3;
    private static final int EAST = 4;

    private static RandomSource decided(float value, int pick) {
        return new SingleThreadedRandomSource(0L) {
            @Override
            public float nextFloat() {
                return value;
            }

            @Override
            public int nextInt(int bound) {
                return pick;
            }
        };
    }

    private static void expect(GameTestHelper context, Mildew actual, Mildew expected, String what) {
        context.assertTrue(actual == expected, what + " left the plant " + actual + ", expected " + expected);
    }

    private static void near(GameTestHelper context, float actual, float expected, String what) {
        context.assertTrue(Math.abs(actual - expected) <= 1.0E-6F * Math.max(1.0F, expected * 1000.0F),
                what + ": odds " + actual + ", expected " + expected);
    }

    /** The onset odds of a fresh neutral plant of {@code crop} at age 2 with {@code roof} over it, on moist farmland. */
    private static float odds(GameTestHelper context, Block crop, Block roof) {
        context.setBlock(ROOF, roof);
        plant(context, crop, 2, Mildew.CLEAN);
        BlockPos at = context.absolutePos(CROP);
        return Mildew.onsetFor(context.getLevel(), at, context.getLevel().getBlockState(at));
    }

    /** The onset odds of a fresh neutral plant of {@code crop} at age 2 on a fed, pumped Hydro Tray of {@code water} levels. */
    private static float onTray(GameTestHelper context, Block crop, int water) {
        for (int y = 2; y >= 0; y--) {
            if (context.getBlockState(CROP.above(y)).getBlock() instanceof CropBlock) {
                context.setBlock(CROP.above(y), Blocks.AIR);
            }
        }
        context.setBlock(SOIL, ModBlocks.HYDRO_TRAY.defaultBlockState().setValue(HydroTrayBlock.LEVEL, water));
        context.setBlock(CROP, Defoliation.unworked(crop.defaultBlockState()).setValue(BlockStateProperties.AGE_7, 2));
        Genes.neutral(context, CROP);
        BlockPos at = context.absolutePos(CROP);
        float odds = Mildew.onsetFor(context.getLevel(), at, context.getLevel().getBlockState(at));
        context.setBlock(CROP, Blocks.AIR);
        return odds;
    }

    /** A water source at {@code at}, walled in on every side but the top, so it stands and never flows. */
    private static void pool(GameTestHelper context, BlockPos at) {
        context.setBlock(at.below(), Blocks.STONE);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (!context.getBlockState(at.relative(side)).is(Blocks.FARMLAND)
                    && !context.getBlockState(at.relative(side)).is(ModBlocks.HYDRO_TRAY)) {
                context.setBlock(at.relative(side), Blocks.STONE);
            }
        }
        context.setBlock(at, Blocks.WATER);
    }

    /** Sets {@code biome} over the 9-block cube round {@code at}, as {@code /fillbiome} does. */
    private static void fillBiome(GameTestHelper context, BlockPos at, String biome) {
        BlockPos a = context.absolutePos(at).offset(-4, -4, -4);
        BlockPos b = context.absolutePos(at).offset(4, 4, 4);
        var server = context.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "fillbiome " + a.getX() + " " + a.getY() + " " + a.getZ() + " " + b.getX() + " " + b.getY() + " " + b.getZ() + " " + biome);
    }

    /** One bone meal step on a fresh plant at {@code age} with {@code roof} over it, and the mildew it leaves. */
    private static Mildew step(GameTestHelper context, Block crop, int age, Block roof, RandomSource roll) {
        context.setBlock(ROOF, roof);
        plant(context, crop, age, Mildew.CLEAN);
        ServerLevel world = context.getLevel();
        BlockPos pos = context.absolutePos(CROP);
        ((CropBlock) crop).performBonemeal(world, roll, pos, world.getBlockState(pos), BonemealSource.INTERACTION);
        context.assertTrue(context.getBlockState(CROP).getValue(BlockStateProperties.AGE_7) == age + 1,
                "the step did not grow the plant, so this probe proves nothing");
        return context.getBlockState(CROP).getValue(Mildew.PROPERTY);
    }

    private static void plant(GameTestHelper context, Block crop, int age, Mildew mildew) {
        sow(context, CROP, crop, age, mildew);
    }

    /** A plant of {@code crop} at {@code age} on moist farmland at {@code pos}, its segments grown in and carrying {@code mildew}. */
    private static void sow(GameTestHelper context, BlockPos pos, Block crop, int age, Mildew mildew) {
        for (int y = 2; y >= 0; y--) {
            if (context.getBlockState(pos.above(y)).getBlock() instanceof CropBlock) {
                context.setBlock(pos.above(y), Blocks.AIR);
            }
        }
        context.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE));
        context.setBlock(pos, Defoliation.unworked(crop.defaultBlockState()).setValue(BlockStateProperties.AGE_7, age));
        Genes.neutral(context, pos);
        // A random tick reconciles the segments, and MISS keeps it from growing. Sown clean, so that
        // tick cannot spread anything; whatever the gas did on it, the mildew is written after.
        ServerLevel world = context.getLevel();
        BlockPos at = context.absolutePos(pos);
        world.getBlockState(at).randomTick(world, at, MISS);
        Mildew.write(world, at, mildew);
        context.assertTrue(context.getBlockState(pos).getValue(Mildew.PROPERTY) == mildew
                        && context.getBlockState(pos).getValue(BlockStateProperties.AGE_7) == age,
                "the plant at " + pos + " did not come up as sown");
    }

    /**
     * Takes the test's own ceiling off the column over {@code plant}: the framework walls every test
     * in with barriers, one above the structure, and a barrier is a roof like any other block.
     */
    private static void openSky(GameTestHelper context, BlockPos plant) {
        context.setBlock(new BlockPos(plant.getX(), 8, plant.getZ()), Blocks.AIR);
    }

    /** A glass roof over the whole structure, three above the plants' beds. */
    private static void roof(GameTestHelper context) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                context.setBlock(new BlockPos(x, 5, z), Blocks.GLASS);
            }
        }
    }

    private static int leavesDropped(GameTestHelper context) {
        AABB box = new AABB(context.absolutePos(BlockPos.ZERO)).inflate(8);
        return context.getLevel().getEntitiesOfClass(ItemEntity.class, box, item -> item.getItem().is(ModItems.HEMP_LEAF))
                .stream().mapToInt(item -> item.getItem().getCount()).sum();
    }
}
