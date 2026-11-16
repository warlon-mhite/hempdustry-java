package com.warlonmhite.hempdustry.test;

import com.mojang.serialization.JsonOps;
import com.warlonmhite.hempdustry.block.ModBlocks;
import com.warlonmhite.hempdustry.block.custom.Defoliation;
import com.warlonmhite.hempdustry.block.custom.MauiWowieCropBlock;
import com.warlonmhite.hempdustry.block.custom.SativaCropBlock;
import com.warlonmhite.hempdustry.block.custom.TriplePlantSegment;
import com.warlonmhite.hempdustry.block.entity.custom.PlantRecordBlockEntity;
import com.warlonmhite.hempdustry.component.ModComponents;
import com.warlonmhite.hempdustry.entity.ModEntities;
import com.warlonmhite.hempdustry.entity.ai.SeekHempCropGoal;
import com.warlonmhite.hempdustry.item.ModItems;
import com.warlonmhite.hempdustry.item.custom.DeviceType;
import com.warlonmhite.hempdustry.item.custom.SmokeContents;
import com.warlonmhite.hempdustry.strain.Chemotype;
import com.warlonmhite.hempdustry.strain.ModStrains;
import com.warlonmhite.hempdustry.strain.Resistance;
import com.warlonmhite.hempdustry.strain.Strain;
import com.warlonmhite.hempdustry.strain.Terpene;
import com.warlonmhite.hempdustry.util.ModTags;
import com.warlonmhite.hempdustry.world.ModPlacedFeatures;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Maui Wowie, the ocean's plant: Lemon Haze's body under water, holding its own water as seagrass does, grown in a
 * seagrass meadow, smoked for Water Breathing and Dolphin's Grace with Mining Fatigue on the exhale, found in the
 * warm shallows, the warm ocean ruins and buried treasure, and drawing drowned to a ripe patch.
 *
 * <p>Nearly every guard here fails quietly in play: a plant that grew into air, left a dry hole when harvested or
 * stood in a sponge's dry pocket holding a block of sea looks like a quirk, not a bug; a meadow that counted for
 * nothing, or seeds in a cold ruin, look like bad luck.
 */
public final class MauiWowieGameTest {
    private static final BlockPos ROOT = new BlockPos(2, 2, 2);
    private static final int ROLLS = 40;
    private static final long SEED_SOURCE = 0x3A01E1L;
    /** The pipe's exhale: {@code Smoking.EXHALE_DELAY_TICKS}, with no sound delay. */
    private static final int EXHALE_TICKS = 38;

    /** A roll that always lands: nextInt is 0, nextFloat 0, so every chance and every gate passes. */
    private static final RandomSource HIT = new net.minecraft.world.level.levelgen.SingleThreadedRandomSource(0L) {
        @Override
        public int nextInt(int bound) {
            return 0;
        }

        @Override
        public float nextFloat() {
            return 0.0F;
        }
    };

    // ----- the plant -----

    /**
     * Planted only into a full water source on the sea floor's soils; every block of it holds water and dims
     * the light as water does; it grows a segment only into still water; and whatever takes it leaves the water.
     */
    public static void mauiWowieGrowsUnderWater(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        MauiWowieCropBlock maui = (MauiWowieCropBlock) ModBlocks.MAUI_WOWIE_CROP;
        sea(context, 1, 5);
        BlockPos root = context.absolutePos(ROOT);
        BlockState seedling = maui.defaultBlockState();

        for (Block soil : new Block[]{Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL, Blocks.CLAY, Blocks.MUD}) {
            context.setBlock(ROOT.below(), soil);
            context.assertTrue(seedling.canSurvive(world, root), "Maui Wowie cannot be planted on " + soil);
        }
        for (Block soil : new Block[]{Blocks.DIRT, Blocks.FARMLAND, Blocks.SUSPICIOUS_SAND, Blocks.STONE,
                ModBlocks.GROW_POT, ModBlocks.HYDRO_TRAY}) {
            context.setBlock(ROOT.below(), soil);
            context.assertTrue(!seedling.canSurvive(world, root), "Maui Wowie can be planted on " + soil);
        }
        context.setBlock(ROOT.below(), Blocks.SAND);
        context.assertTrue(!seedling.is(BlockTags.CROPS), "Maui Wowie is in #minecraft:crops: a bee would work it under water");
        context.assertTrue(seedling.is(ModTags.Blocks.HEMP_CROPS), "Maui Wowie is not in #hempdustry:hemp_crops");

        // It holds the sea, and dims the light by one as a block of water does: that is how depth is light.
        context.assertTrue(seedling.getFluidState().is(Fluids.WATER) && seedling.getFluidState().isSource(),
                "a Maui Wowie block holds " + seedling.getFluidState());
        context.assertValueEqual(1, seedling.getLightBlock(), "a Maui Wowie block's light dampening");
        context.assertTrue(!seedling.propagatesSkylightDown(), "sky light passes a Maui Wowie block undimmed");

        // Seeds go into a full water source, never into air.
        ServerPlayer player = Players.survival(context);
        ItemStack seeds = new ItemStack(ModItems.MAUI_WOWIE_SEEDS);
        context.assertTrue(maui.getStateForPlacement(placing(context, player, seeds, ROOT)) != null,
                "Maui Wowie seeds cannot be planted in water");
        BlockPos dry = new BlockPos(6, 6, 6);
        context.setBlock(dry.below(), Blocks.SAND);
        context.assertTrue(maui.getStateForPlacement(placing(context, player, seeds, dry)) == null,
                "Maui Wowie seeds can be planted in air");

        // A segment grows only into still water: air over the root holds it back as a roof would.
        BlockPos shallowRel = new BlockPos(5, 5, 5);
        context.setBlock(shallowRel.below(), Blocks.SAND);
        context.setBlock(shallowRel, maui.defaultBlockState().setValue(SativaCropBlock.AGE, 3));
        context.assertTrue(!maui.isValidBonemealTarget(world, context.absolutePos(shallowRel),
                        context.getBlockState(shallowRel)),
                "a Maui Wowie with air over it would grow into the air");

        context.setBlock(ROOT, maui.defaultBlockState().setValue(SativaCropBlock.AGE, 5));
        maui.performBonemeal(world, HIT, root, world.getBlockState(root));
        for (int up = 0; up < 3; up++) {
            BlockState segment = world.getBlockState(root.above(up));
            context.assertTrue(segment.is(maui) && segment.getValue(SativaCropBlock.AGE) == 6,
                    "a bone-mealed Maui Wowie is not three tall at age 6: " + segment + " at +" + up);
            context.assertTrue(segment.getFluidState().is(Fluids.WATER), "a grown segment holds no water");
        }

        // A player breaking the middle takes the rest of the plant by its own writes: each must leave the sea.
        ServerPlayer harvester = Players.survival(context);
        harvester.setPos(Vec3.atCenterOf(root.above(4)));
        harvester.gameMode.destroyBlock(root.above());
        for (int up = 0; up < 3; up++) {
            context.assertTrue(world.getBlockState(root.above(up)).is(Blocks.WATER),
                    "a harvested Maui Wowie left " + world.getBlockState(root.above(up)) + " at +" + up + ", not the sea");
        }
        context.succeed();
    }

    /**
     * A sponge takes the plant and the wild flower as it takes seagrass: with their drops, the water gone. Without
     * that, each would stand in the sponge's dry pocket holding a block of sea.
     */
    public static void aSpongeTakesMauiWowie(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        sea(context, 1, 5);
        ripe(context, ROOT);
        BlockPos flower = new BlockPos(3, 2, 3);
        context.setBlock(flower, ModBlocks.MAUI_WOWIE_FLOWER);
        context.assertTrue(context.getBlockState(flower).getFluidState().is(Fluids.WATER), "wild Maui Wowie holds no water");
        BlockPos grass = new BlockPos(1, 2, 3);
        context.setBlock(grass, Blocks.SEAGRASS);

        context.setBlock(new BlockPos(2, 3, 3), Blocks.SPONGE);
        context.assertTrue(context.getBlockState(new BlockPos(2, 3, 3)).is(Blocks.WET_SPONGE), "the sponge took no water");
        context.assertTrue(context.getBlockState(grass).isAir(), "the sponge left vanilla's seagrass, so the rest proves nothing");
        for (int up = 0; up < 3; up++) {
            BlockState left = context.getBlockState(ROOT.above(up));
            context.assertTrue(!left.is(ModBlocks.MAUI_WOWIE_CROP), "a sponge left a Maui Wowie segment standing at +" + up);
        }
        context.assertTrue(!context.getBlockState(flower).is(ModBlocks.MAUI_WOWIE_FLOWER), "a sponge left wild Maui Wowie standing");
        AABB box = new AABB(context.absolutePos(BlockPos.ZERO)).inflate(8);
        context.assertTrue(!world.getEntitiesOfClass(ItemEntity.class, box, item -> item.getItem().is(ModItems.MAUI_WOWIE_BUDS)).isEmpty(),
                "the sponge took a ripe Maui Wowie without its harvest");
        context.assertTrue(!world.getEntitiesOfClass(ItemEntity.class, box, item -> item.getItem().is(ModItems.MAUI_WOWIE_SEEDS)).isEmpty(),
                "the sponge took the plants without a seed");
        context.succeed();
    }

    /**
     * Bare soil reads moisture 1; each meadow plant around the root an eighth of the way to watered farmland's 10;
     * its own kind beside it halves that.
     */
    public static void aMeadowIsItsFarmland(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        MauiWowieCropBlock maui = (MauiWowieCropBlock) ModBlocks.MAUI_WOWIE_CROP;
        sea(context, 1, 5);
        BlockPos rootRel = new BlockPos(3, 2, 3);
        context.setBlock(rootRel, maui.defaultBlockState());
        BlockPos root = context.absolutePos(rootRel);
        near(context, maui.fieldMoisture(world, root), 1.0F, "bare sand's moisture");

        Block[] meadow = {Blocks.SEAGRASS, Blocks.SEAGRASS, Blocks.KELP_PLANT, Blocks.SEAGRASS};
        int[][] spots = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};
        for (int i = 0; i < 4; i++) {
            context.setBlock(rootRel.offset(spots[i][0], 0, spots[i][1]), meadow[i]);
        }
        near(context, maui.fieldMoisture(world, root), 5.5F, "half a meadow's moisture");
        for (int i = 4; i < 8; i++) {
            context.setBlock(rootRel.offset(spots[i][0], 0, spots[i][1]), i == 4 ? Blocks.KELP : Blocks.SEAGRASS);
        }
        near(context, maui.fieldMoisture(world, root), 10.0F, "a full meadow's moisture");
        context.setBlock(rootRel.offset(1, 0, 1), maui.defaultBlockState());
        near(context, maui.fieldMoisture(world, root), (1.0F + 9.0F * 7 / 8) / 2, "a crowded meadow's moisture");
        context.succeed();
    }

    /**
     * The water over a plant is no roof to the open-sky record, at any height: above sea level vanilla's {@code canSeeSkyFromBelowWater} is plain {@code canSeeSky},
     * which nothing inside water passes, so a tank or a mountain lake read roofed. A block in the water is a roof.
     */
    public static void theSeaIsNoRoof(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        sea(context, 1, 7);
        BlockPos surface = context.absolutePos(new BlockPos(ROOT.getX(), 8, ROOT.getZ()));
        context.setBlock(new BlockPos(ROOT.getX(), 8, ROOT.getZ()), Blocks.AIR);
        ripe(context, ROOT);
        BlockPos root = context.absolutePos(ROOT);
        context.assertTrue(root.above(2).getY() >= world.getSeaLevel(),
                "test setup: the plant stands below sea level, where vanilla's own walk already reads the water");
        context.startSequence()
                .thenWaitUntil(() -> context.assertTrue(world.canSeeSky(surface), "the opened column reads no sky at the water's surface"))
                // A barrier lets the sky through, so the column was lit to 15 before the sea went in: wait for the
                // water to dim the plant's top, or plain canSeeSky would still read the old light there.
                .thenWaitUntil(() -> context.assertTrue(!world.canSeeSky(root.above(2)), "the water over the plant has not dimmed its top yet"))
                .thenExecute(() -> {
                    PlantRecordBlockEntity.recordSky(world, root, 0, 3);
                    context.assertValueEqual(Boolean.TRUE, record(world, root).skyRecord().orElse(null),
                            "a Maui Wowie under open water above sea level, its sky record");
                    context.setBlock(ROOT.above(4), Blocks.STONE);
                })
                .thenWaitUntil(() -> context.assertTrue(!world.canSeeSky(root.above(4)), "the stone in the water still reads the sky"))
                .thenExecute(() -> {
                    PlantRecordBlockEntity.recordSky(world, root, 0, 3);
                    context.assertValueEqual(Boolean.FALSE, record(world, root).skyRecord().orElse(null),
                            "a Maui Wowie under a stone in the water, its sky record");
                })
                .thenSucceed();
    }

    /** Lemon Haze's harvest, from the root of the same body. */
    public static void mauiWowieHarvestIsLemonHazes(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(ROOT));
        LootTable maui = table(world, ModBlocks.MAUI_WOWIE_CROP);
        LootTable haze = table(world, ModBlocks.SATIVA_CROP);
        Random seeds = new Random(SEED_SOURCE);
        for (int roll = 0; roll < ROLLS; roll++) {
            long seed = seeds.nextLong();
            int[] m = harvest(maui, world, origin, ripeRoot(ModBlocks.MAUI_WOWIE_CROP), seed,
                    ModItems.MAUI_WOWIE_BUDS, ModItems.MAUI_WOWIE_SEEDS);
            int[] h = harvest(haze, world, origin, ripeRoot(ModBlocks.SATIVA_CROP), seed,
                    ModItems.SATIVA_BUDS, ModItems.SATIVA_SEEDS);
            context.assertTrue(java.util.Arrays.equals(m, h), "Maui Wowie paid buds/leaves/seeds/stems "
                    + java.util.Arrays.toString(m) + " against Lemon Haze's " + java.util.Arrays.toString(h));
        }
        context.succeed();
    }

    // ----- the smoke -----

    /**
     * Type I, ocimene, its own wild flower; Water Breathing and Dolphin's Grace flat, every bud past the first
     * buying half the device's time; Mining Fatigue only on the exhale, a quarter of the hit.
     */
    public static void mauiWowieSmokesForTheDive(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Holder<Strain> maui = world.registryAccess().lookupOrThrow(Strain.REGISTRY_KEY).getOrThrow(ModStrains.MAUI_WOWIE);
        Strain strain = maui.value();
        context.assertTrue(strain.chemotype() == Chemotype.THC_DOMINANT, "Maui Wowie is not Type I");
        context.assertValueEqual(Optional.of(Terpene.OCIMENE), strain.terpene(), "Maui Wowie's terpene");
        context.assertValueEqual(Optional.of(0x4A7BD8),
                Terpene.resolve(world.registryAccess(), Terpene.OCIMENE).map(Terpene::color), "ocimene's colour");
        context.assertTrue(strain.wildFlower().filter(f -> f == ModBlocks.MAUI_WOWIE_FLOWER).isPresent(),
                "Maui Wowie's wild plant is " + strain.wildFlower());
        context.assertTrue(world.recipeAccess().byKey(ResourceKey.create(Registries.RECIPE,
                        Identifier.fromNamespaceAndPath("hempdustry", "decarboxylating/maui_wowie_buds"))).isPresent(),
                "the oven turns Maui Wowie buds away");
        context.assertValueEqual(com.warlonmhite.hempdustry.strain.Resistances.of(Resistance.WEAK,
                Resistance.VERY_RESISTANT, Resistance.NEUTRAL, Resistance.VERY_RESISTANT), strain.resistances(), "Maui Wowie's resistances");

        int bong = DeviceType.BONG.builtInStats().durationTicks();
        List<MobEffectInstance> one = SmokeContents.of(maui, 1).effects(bong, false, new java.util.Random(1L));
        List<MobEffectInstance> three = SmokeContents.of(maui, 3).effects(bong, false, new java.util.Random(1L));
        for (Holder<net.minecraft.world.effect.MobEffect> buff : List.of(MobEffects.WATER_BREATHING, MobEffects.DOLPHINS_GRACE)) {
            MobEffectInstance single = find(one, buff);
            MobEffectInstance triple = find(three, buff);
            context.assertTrue(single != null && single.getAmplifier() == 0 && single.getDuration() == bong,
                    "one bud's " + buff.getRegisteredName() + " is " + single + ", expected level I for " + bong);
            context.assertTrue(triple != null && triple.getAmplifier() == 0 && triple.getDuration() == 2 * bong,
                    "three buds' " + buff.getRegisteredName() + " is " + triple + ", expected level I for " + 2 * bong);
        }
        context.assertTrue(find(three, MobEffects.HUNGER) != null, "a bowl of Maui Wowie gave no Hunger");
        context.assertTrue(find(three, MobEffects.MINING_FATIGUE) == null, "Mining Fatigue landed with the hit");
        MobEffectInstance fatigue = find(SmokeContents.of(maui, 3).effects(bong, true, new java.util.Random(1L)), MobEffects.MINING_FATIGUE);
        context.assertTrue(fatigue != null && fatigue.getAmplifier() == 0 && fatigue.getDuration() <= bong / 2,
                "the exhale's Mining Fatigue is " + fatigue + ", expected level I for a quarter of the hit");

        int length = DeviceType.PIPE.builtInStats().durationTicks();
        ServerPlayer smoker = Players.survival(context);
        smoker.setPos(Vec3.atCenterOf(context.absolutePos(new BlockPos(4, 2, 4))));
        smoke(smoker, maui, 1);
        MobEffectInstance breath = smoker.getEffect(MobEffects.WATER_BREATHING);
        context.assertTrue(breath != null && breath.getDuration() == length, "the hit did not give " + length + " ticks of Water Breathing: " + breath);
        context.assertTrue(smoker.hasEffect(MobEffects.DOLPHINS_GRACE), "the hit gave no Dolphin's Grace");
        context.assertTrue(!smoker.hasEffect(MobEffects.MINING_FATIGUE), "Mining Fatigue landed with the hit, not the exhale");
        context.runAtTickTime(EXHALE_TICKS + 5, () -> {
            MobEffectInstance tired = smoker.getEffect(MobEffects.MINING_FATIGUE);
            int expected = Math.round(length * 0.25F);
            context.assertTrue(tired != null, "no Mining Fatigue on the exhale");
            context.assertTrue(tired.getDuration() <= expected && tired.getDuration() > expected - 10,
                    "the exhale's Mining Fatigue lasts " + tired.getDuration() + ", expected a quarter of the hit (" + expected + ")");
            context.succeed();
        });
    }

    /** Hidden, under First Contact: smoke Maui Wowie riding a hemp boat or chest boat; nothing else grants it. */
    public static void smokeOnTheWater(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        AdvancementHolder song = world.getServer().getAdvancements().get(Identifier.fromNamespaceAndPath("hempdustry", "smoke_on_the_water"));
        context.assertTrue(song != null, "hempdustry:smoke_on_the_water is not loaded");
        context.assertTrue(song.value().display().map(DisplayInfo::isHidden).orElse(false), "Smoke on the Water is not hidden");
        context.assertTrue(song.value().parent().equals(Optional.of(Identifier.fromNamespaceAndPath("hempdustry", "first_contact"))),
                "Smoke on the Water does not hang under First Contact");
        Holder<Strain> maui = world.registryAccess().lookupOrThrow(Strain.REGISTRY_KEY).getOrThrow(ModStrains.MAUI_WOWIE);
        Holder<Strain> haze = world.registryAccess().lookupOrThrow(Strain.REGISTRY_KEY).getOrThrow(ModStrains.SATIVA);
        Vec3 at = Vec3.atCenterOf(context.absolutePos(new BlockPos(4, 2, 4)));

        ServerPlayer ashore = Players.survival(context);
        ashore.setPos(at);
        smoke(ashore, maui, 1);
        context.assertTrue(!ashore.getAdvancements().getOrStartProgress(song).isDone(), "smoking Maui Wowie ashore granted it");

        ServerPlayer wrongStrain = Players.survival(context);
        board(context, wrongStrain, ModEntities.HEMP_BOAT, at);
        smoke(wrongStrain, haze, 1);
        context.assertTrue(!wrongStrain.getAdvancements().getOrStartProgress(song).isDone(), "smoking Lemon Haze on a hemp boat granted it");

        ServerPlayer oak = Players.survival(context);
        board(context, oak, EntityType.OAK_BOAT, at);
        smoke(oak, maui, 1);
        context.assertTrue(!oak.getAdvancements().getOrStartProgress(song).isDone(), "smoking Maui Wowie on an oak boat granted it");

        for (var boat : List.of(ModEntities.HEMP_BOAT, ModEntities.HEMP_CHEST_BOAT)) {
            ServerPlayer sailor = Players.survival(context);
            board(context, sailor, boat, at);
            smoke(sailor, maui, 1);
            context.assertTrue(sailor.getAdvancements().getOrStartProgress(song).isDone(),
                    "smoking Maui Wowie on a " + BuiltInRegistries.ENTITY_TYPE.getKey(boat) + " did not grant it");
        }
        context.succeed();
    }

    // ----- where it is found -----

    /**
     * Seeds in the ocean ruins' chests behind a check for the warm seas, and in the warm ruin's brushed sand at 2 in
     * 17; buds in buried treasure, at a quarter; never in the grass or the shared chests.
     */
    public static void mauiWowieStaysInTheWarmSea(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Predicate<ItemStack> buds = stack -> stack.is(ModItems.MAUI_WOWIE_BUDS);
        Predicate<ItemStack> seeds = stack -> stack.is(ModItems.MAUI_WOWIE_SEEDS);
        BlockPos at = new BlockPos(2, 2, 2);
        context.assertTrue(!world.getBiome(context.absolutePos(at)).is(ModTags.Biomes.MAUI_WOWIE_GEN),
                "test setup: the test runs in a warm sea, so the cold-sea check proves nothing");
        for (ResourceKey<LootTable> ruin : List.of(BuiltInLootTables.UNDERWATER_RUIN_SMALL, BuiltInLootTables.UNDERWATER_RUIN_BIG)) {
            String json = encode(world, ruin);
            context.assertTrue(json.contains("\"hempdustry:maui_wowie_seeds\"") && json.contains("\"#hempdustry:maui_wowie_gen\""),
                    ruin.identifier() + " holds no Maui Wowie seeds behind a warm-sea check");
            context.assertValueEqual(0, rolls(context, ruin, at, seeds), ruin.identifier() + " holding Maui Wowie seeds outside a warm sea");
        }
        // And in a warm sea the check lets them through, at the roll's own quarter.
        String home = world.getBiome(context.absolutePos(at)).unwrapKey().orElseThrow().identifier().toString();
        fillBiome(context, at, "minecraft:warm_ocean");
        try {
            for (ResourceKey<LootTable> ruin : List.of(BuiltInLootTables.UNDERWATER_RUIN_SMALL, BuiltInLootTables.UNDERWATER_RUIN_BIG)) {
                int warm = rolls(context, ruin, at, seeds);
                context.assertTrue(warm >= 190 && warm <= 310, ruin.identifier() + " in a warm ocean held Maui Wowie seeds "
                        + warm + " times in 1000, expected about 250");
            }
        } finally {
            fillBiome(context, at, home);
        }
        int brushed = brushes(context, BuiltInLootTables.OCEAN_RUIN_WARM_ARCHAEOLOGY, seeds);
        context.assertTrue(brushed >= 80 && brushed <= 160, "warm ruin sand gave Maui Wowie seeds " + brushed + " times in 1000, expected about 118");
        int treasure = rolls(context, BuiltInLootTables.BURIED_TREASURE, at, buds);
        context.assertTrue(treasure >= 190 && treasure <= 310, "buried treasure held Maui Wowie buds " + treasure + " times in 1000, expected about 250");
        context.assertValueEqual(0, rolls(context, BuiltInLootTables.BURIED_TREASURE, at, seeds), "buried treasure holding Maui Wowie seeds");
        for (ResourceKey<LootTable> none : List.of(BuiltInLootTables.SIMPLE_DUNGEON, BuiltInLootTables.SHIPWRECK_SUPPLY,
                BuiltInLootTables.VILLAGE_PLAINS_HOUSE, BuiltInLootTables.ABANDONED_MINESHAFT, BuiltInLootTables.WOODLAND_MANSION,
                BuiltInLootTables.ANCIENT_CITY, BuiltInLootTables.SHIPWRECK_TREASURE)) {
            context.assertValueEqual(0, rolls(context, none, at, seeds.or(buds)), none.identifier() + " holding Maui Wowie");
        }
        String grass = LootTable.DIRECT_CODEC.encodeStart(world.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                world.getServer().reloadableRegistries().getLootTable(Blocks.TALL_GRASS.getLootTable().orElseThrow())).getOrThrow().toString();
        context.assertTrue(grass.contains("\"hempdustry:indica_seeds\"") && !grass.contains("\"hempdustry:maui_wowie_seeds\""),
                "tall grass names Maui Wowie seeds, or no hemp seeds at all");
        context.succeed();
    }

    /**
     * Wild only in the warm seas; placed only on its soil, in plain water 3 to 6 blocks deep, read off the water
     * column; and it holds the water it was placed in.
     */
    public static void wildMauiWowieInTheShallows(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        var tags = world.registryAccess().lookupOrThrow(Registries.BIOME);
        for (var warm : List.of(Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN)) {
            context.assertTrue(tags.getOrThrow(warm).is(ModTags.Biomes.MAUI_WOWIE_GEN), warm.identifier() + " is not in #hempdustry:maui_wowie_gen");
        }
        for (var cold : List.of(Biomes.OCEAN, Biomes.COLD_OCEAN, Biomes.FROZEN_OCEAN, Biomes.DEEP_COLD_OCEAN, Biomes.BEACH)) {
            context.assertTrue(!tags.getOrThrow(cold).is(ModTags.Biomes.MAUI_WOWIE_GEN), cold.identifier() + " is in #hempdustry:maui_wowie_gen");
        }
        PlacedFeature placed = world.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE)
                .getOrThrow(ModPlacedFeatures.MAUI_WOWIE_PLACED_KEY).value();
        // One plant of the patch: the selector's patch, then what each of its tries places, the shallows filter on it.
        var selector = (net.minecraft.world.level.levelgen.feature.configurations.RandomFeatureConfiguration)
                placed.feature().value().config();
        var patch = (net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration)
                selector.defaultFeature.value().feature().value().config();
        PlacedFeature onePlant = patch.feature().value();
        var generator = world.getChunkSource().getGenerator();
        RandomSource random = RandomSource.create(7L);

        // Each column walled in glass, so no water runs between them.
        column(context, new BlockPos(1, 1, 1), Blocks.SAND, 4);
        column(context, new BlockPos(4, 1, 1), Blocks.SAND, 2);
        column(context, new BlockPos(1, 0, 5), Blocks.SAND, 7);
        column(context, new BlockPos(4, 1, 5), Blocks.STONE, 4);
        context.assertTrue(onePlant.place(world, generator, random, context.absolutePos(new BlockPos(1, 2, 1))),
                "wild Maui Wowie did not place in 4 blocks of water on sand");
        BlockState wild = context.getBlockState(new BlockPos(1, 2, 1));
        context.assertTrue(wild.is(ModBlocks.MAUI_WOWIE_FLOWER) && wild.getFluidState().is(Fluids.WATER),
                "the wild plant placed as " + wild);
        context.assertTrue(!onePlant.place(world, generator, random, context.absolutePos(new BlockPos(4, 2, 1))),
                "wild Maui Wowie placed in 2 blocks of water");
        context.assertTrue(!onePlant.place(world, generator, random, context.absolutePos(new BlockPos(1, 1, 5))),
                "wild Maui Wowie placed in 7 blocks of water");
        context.assertTrue(!onePlant.place(world, generator, random, context.absolutePos(new BlockPos(4, 2, 5))),
                "wild Maui Wowie placed on stone");
        context.succeed();
    }

    // ----- the drowned -----

    /** A drowned finds a ripe patch on the sea floor, never an unripe one, and only by reading the floor. */
    public static void drownedAreDrawnToARipePatch(GameTestHelper context) {
        sea(context, 1, 7);
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) {
                context.setBlock(new BlockPos(x, 8, z), Blocks.AIR);
            }
        }
        BlockPos rootRel = new BlockPos(4, 2, 4);
        Drowned drowned = context.spawn(EntityType.DROWNED, new BlockPos(4, 6, 4));
        SeekHempCropGoal goal = goalOf(drowned);
        context.assertTrue(goal != null, "DrownedMixin did not add the seek goal to a drowned");
        context.assertTrue(SeekHempCropGoal.findCover(drowned, 2, 256, SeekHempCropGoal::ripeMauiWowie, true) == null,
                "a drowned found cover on a bare sea floor");

        grown(context, rootRel, SativaCropBlock.MAX_AGE - 1);
        context.assertTrue(SeekHempCropGoal.findCover(drowned, 2, 256, SeekHempCropGoal::ripeMauiWowie, true) == null,
                "an unripe Maui Wowie drew a drowned");
        grown(context, rootRel, SativaCropBlock.MAX_AGE);
        BlockPos cover = SeekHempCropGoal.findCover(drowned, 2, 256, SeekHempCropGoal::ripeMauiWowie, true);
        context.assertTrue(context.absolutePos(rootRel).equals(cover), "a drowned found " + cover + ", not the ripe plant's root");
        context.assertTrue(SeekHempCropGoal.findCover(drowned, 2, 256, SeekHempCropGoal::ripeMauiWowie, false) == null,
                "the open-sky search found a plant under water; the floor is what makes the drowned's work");
        drowned.discard();
        context.succeed();
    }

    // ----- helpers -----

    /** Sand at {@code floor} under the whole structure, still water from above it up to {@code top}. */
    private static void sea(GameTestHelper context, int floor, int top) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                context.setBlock(new BlockPos(x, floor, z), Blocks.SAND);
                for (int y = floor + 1; y <= top; y++) {
                    context.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                }
            }
        }
    }

    /** A column of {@code soil} at {@code soilRel}, {@code depth} blocks of water on it, glass around, air above. */
    private static void column(GameTestHelper context, BlockPos soilRel, Block soil, int depth) {
        for (int y = soilRel.getY(); y <= soilRel.getY() + depth + 1; y++) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                context.setBlock(new BlockPos(soilRel.getX(), y, soilRel.getZ()).relative(side), Blocks.GLASS);
            }
        }
        context.setBlock(soilRel, soil);
        for (int up = 1; up <= depth; up++) {
            context.setBlock(soilRel.above(up), Blocks.WATER);
        }
        if (soilRel.getY() + depth + 1 < 8) {
            context.setBlock(soilRel.above(depth + 1), Blocks.AIR);
        }
    }

    /** Sets {@code biome} over the 9-block cube round {@code at}, as {@code /fillbiome} does. */
    private static void fillBiome(GameTestHelper context, BlockPos at, String biome) {
        BlockPos a = context.absolutePos(at).offset(-4, -4, -4);
        BlockPos b = context.absolutePos(at).offset(4, 4, 4);
        var server = context.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "fillbiome " + a.getX() + " " + a.getY() + " " + a.getZ() + " " + b.getX() + " " + b.getY() + " " + b.getZ() + " " + biome);
    }

    /** A ripe three-tall Maui Wowie with its root at {@code rootRel}. */
    private static void ripe(GameTestHelper context, BlockPos rootRel) {
        grown(context, rootRel, SativaCropBlock.MAX_AGE);
    }

    private static void grown(GameTestHelper context, BlockPos rootRel, int age) {
        BlockState base = Defoliation.unworked(ModBlocks.MAUI_WOWIE_CROP.defaultBlockState()).setValue(SativaCropBlock.AGE, age);
        context.setBlock(rootRel, base.setValue(SativaCropBlock.SEGMENT, TriplePlantSegment.LOWER));
        context.setBlock(rootRel.above(), base.setValue(SativaCropBlock.SEGMENT, TriplePlantSegment.MIDDLE));
        context.setBlock(rootRel.above(2), base.setValue(SativaCropBlock.SEGMENT, TriplePlantSegment.UPPER));
    }

    private static BlockState ripeRoot(Block crop) {
        return Defoliation.unworked(crop.defaultBlockState())
                .setValue(SativaCropBlock.SEGMENT, TriplePlantSegment.LOWER)
                .setValue(SativaCropBlock.AGE, SativaCropBlock.MAX_AGE);
    }

    private static BlockPlaceContext placing(GameTestHelper context, ServerPlayer player, ItemStack stack, BlockPos targetRel) {
        BlockPos floor = context.absolutePos(targetRel.below());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return new BlockPlaceContext(context.getLevel(), player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false));
    }

    private static void board(GameTestHelper context, ServerPlayer player, net.minecraft.world.entity.EntityType<? extends AbstractBoat> type, Vec3 at) {
        AbstractBoat boat = type.create(context.getLevel(), EntitySpawnReason.COMMAND);
        boat.setPos(at);
        context.getLevel().addFreshEntity(boat);
        player.setPos(at);
        context.assertTrue(player.startRiding(boat, true, false), "test setup: the player could not board a " + type);
    }

    /** One hit off a wooden pipe packed with {@code count} of {@code strain}. */
    private static void smoke(ServerPlayer smoker, Holder<Strain> strain, int count) {
        ItemStack pipe = new ItemStack(ModItems.WOODEN_PIPE);
        pipe.set(ModComponents.SMOKE_CONTENTS, SmokeContents.of(strain, count));
        pipe.set(ModComponents.CHARGES, DeviceType.PIPE.builtInBowl().hits());
        smoker.setItemInHand(InteractionHand.MAIN_HAND, pipe);
        smoker.getCooldowns().removeCooldown(BuiltInRegistries.ITEM.getKey(ModItems.WOODEN_PIPE));
        smoker.gameMode.useItem(smoker, smoker.level(), pipe, InteractionHand.MAIN_HAND);
    }

    private static MobEffectInstance find(List<MobEffectInstance> effects, Holder<net.minecraft.world.effect.MobEffect> effect) {
        return effects.stream().filter(e -> e.getEffect().is(effect)).findFirst().orElse(null);
    }

    private static void near(GameTestHelper context, float actual, float expected, String what) {
        context.assertTrue(Math.abs(actual - expected) < 1.0E-4F, what + " is " + actual + ", expected " + expected);
    }

    private static SeekHempCropGoal goalOf(Mob mob) {
        try {
            var field = Mob.class.getDeclaredField("goalSelector");
            field.setAccessible(true);
            for (WrappedGoal wrapped : ((GoalSelector) field.get(mob)).getAvailableGoals()) {
                if (wrapped.getGoal() instanceof SeekHempCropGoal goal) {
                    return wrapped.getPriority() == 7 ? goal : null;
                }
            }
            return null;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static PlantRecordBlockEntity record(ServerLevel world, BlockPos root) {
        return (PlantRecordBlockEntity) world.getBlockEntity(root);
    }

    private static LootTable table(ServerLevel world, Block block) {
        return world.getServer().reloadableRegistries().getLootTable(block.getLootTable().orElseThrow());
    }

    private static String encode(ServerLevel world, ResourceKey<LootTable> key) {
        return LootTable.DIRECT_CODEC.encodeStart(world.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                world.getServer().reloadableRegistries().getLootTable(key)).getOrThrow().toString();
    }

    /** {buds, leaves, seeds, stems} from one roll. */
    private static int[] harvest(LootTable table, ServerLevel world, Vec3 origin, BlockState state, long seed,
                                 Item buds, Item seeds) {
        LootParams params = new LootParams.Builder(world)
                .withParameter(LootContextParams.ORIGIN, origin)
                .withParameter(LootContextParams.BLOCK_STATE, state)
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .create(LootContextParamSets.BLOCK);
        int[] counts = new int[4];
        for (ItemStack stack : table.getRandomItems(params, seed)) {
            if (stack.is(buds)) {
                counts[0] += stack.getCount();
            } else if (stack.is(ModItems.HEMP_LEAF)) {
                counts[1] += stack.getCount();
            } else if (stack.is(seeds)) {
                counts[2] += stack.getCount();
            } else if (stack.is(ModItems.HEMP_STEM)) {
                counts[3] += stack.getCount();
            }
        }
        return counts;
    }

    private static int rolls(GameTestHelper context, ResourceKey<LootTable> key, BlockPos at, Predicate<ItemStack> what) {
        ServerLevel world = context.getLevel();
        LootTable table = world.getServer().reloadableRegistries().getLootTable(key);
        LootParams params = new LootParams.Builder(world)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(context.absolutePos(at)))
                .create(LootContextParamSets.CHEST);
        Random seeds = new Random(20261116L);
        int found = 0;
        for (int i = 0; i < 1000; i++) {
            if (table.getRandomItems(params, seeds.nextLong()).stream().anyMatch(what)) {
                found++;
            }
        }
        return found;
    }

    /** As {@link #rolls}, for a brushing table: a player with a brush brushes the block. */
    private static int brushes(GameTestHelper context, ResourceKey<LootTable> key, Predicate<ItemStack> what) {
        ServerLevel world = context.getLevel();
        LootTable table = world.getServer().reloadableRegistries().getLootTable(key);
        ServerPlayer brusher = Players.survival(context);
        LootParams params = new LootParams.Builder(world)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(context.absolutePos(ROOT)))
                .withParameter(LootContextParams.THIS_ENTITY, brusher)
                .withParameter(LootContextParams.TOOL, new ItemStack(Items.BRUSH))
                .create(LootContextParamSets.ARCHAEOLOGY);
        Random seeds = new Random(20261117L);
        int found = 0;
        for (int i = 0; i < 1000; i++) {
            if (table.getRandomItems(params, seeds.nextLong()).stream().anyMatch(what)) {
                found++;
            }
        }
        return found;
    }
}
