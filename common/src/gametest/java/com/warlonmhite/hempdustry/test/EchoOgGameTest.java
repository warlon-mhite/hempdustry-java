package com.warlonmhite.hempdustry.test;

import com.warlonmhite.hempdustry.strain.internal.Chemotypes;
import com.mojang.serialization.JsonOps;
import com.warlonmhite.hempdustry.block.ModBlocks;
import com.warlonmhite.hempdustry.block.custom.Defoliation;
import com.warlonmhite.hempdustry.block.custom.EchoOgCropBlock;
import com.warlonmhite.hempdustry.block.custom.GrowLight;
import com.warlonmhite.hempdustry.block.custom.HempGrowth;
import com.warlonmhite.hempdustry.block.custom.IndicaCropBlock;
import com.warlonmhite.hempdustry.block.custom.Mildew;
import com.warlonmhite.hempdustry.block.entity.custom.PlantRecordBlockEntity;
import com.warlonmhite.hempdustry.component.ModComponents;
import com.warlonmhite.hempdustry.config.EffectPolicy;
import com.warlonmhite.hempdustry.effect.HushEffect;
import com.warlonmhite.hempdustry.effect.ModEffects;
import com.warlonmhite.hempdustry.item.ModItems;
import com.warlonmhite.hempdustry.item.custom.DeviceType;
import com.warlonmhite.hempdustry.item.custom.SmokeContents;
import com.warlonmhite.hempdustry.mixin.ExperienceOrbAccessor;
import com.warlonmhite.hempdustry.strain.Chemotype;
import com.warlonmhite.hempdustry.strain.ModStrains;
import com.warlonmhite.hempdustry.strain.Resistance;
import com.warlonmhite.hempdustry.strain.ResistanceGenes;
import com.warlonmhite.hempdustry.strain.Strain;
import com.warlonmhite.hempdustry.strain.Terpene;
import com.warlonmhite.hempdustry.util.ModTags;
import com.warlonmhite.hempdustry.world.ModConfiguredFeatures;
import com.warlonmhite.hempdustry.world.ModPlacedFeatures;
import com.warlonmhite.hempdustry.world.ModPlacementModifiers;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.behavior.warden.Sniffing;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkBlock;
import net.minecraft.world.level.block.SculkSpreader;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Echo OG, the Ancient City's plant: sculk only, shunning light, fed by experience, a faint glow when
 * ripe; its smoke Hush, which silences your own body to sculk and Wardens, and lets a Warden smell you
 * from twice as far.
 *
 * <p>Nearly every guard here fails quietly in play: a plant that grew in the light, ignored an orb or a
 * catalyst, or was never stressed looks like an easy plant; Hush that silenced a chest, or nothing, looks
 * like a sensor working as usual.
 */
public final class EchoOgGameTest {
    private static final BlockPos SOIL = new BlockPos(2, 1, 2);
    private static final BlockPos CROP = SOIL.above();
    private static final int ROLLS = 40;
    private static final long SEED_SOURCE = 0xEC40B6L;
    /** The pipe's exhale: {@code Smoking.EXHALE_DELAY_TICKS}, with no sound delay. */
    private static final int EXHALE_TICKS = 38;

    /** A roll that always lands: nextInt is 0, nextFloat 0, so every chance and every gate passes. */
    private static final RandomSource HIT = new SingleThreadedRandomSource(0L) {
        @Override
        public int nextInt(int bound) {
            return 0;
        }

        @Override
        public float nextFloat() {
            return 0.0F;
        }
    };
    /** A roll that never lands: nextInt is its bound less one, nextFloat just under 1. */
    private static final RandomSource MISS = new SingleThreadedRandomSource(0L) {
        @Override
        public int nextInt(int bound) {
            return bound - 1;
        }

        @Override
        public float nextFloat() {
            return 0.999F;
        }
    };

    // ----- the plant -----

    /**
     * Sculk only; bone meal refused and kept; 1 in 20 a tick; it lives and grows in a sealed dark box, and
     * the moment a sea lantern lights that box it stops growing, though it lives on.
     */
    public static void echoOgGrowsOnSculkInTheDark(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        EchoOgCropBlock echo = (EchoOgCropBlock) ModBlocks.ECHO_OG_CROP;
        BlockPos crop = context.absolutePos(CROP);
        BlockState seedling = echo.defaultBlockState();

        context.setBlock(SOIL, Blocks.SCULK);
        context.assertTrue(seedling.canSurvive(world, crop), "Echo OG cannot be planted on sculk");
        for (Block soil : new Block[]{Blocks.FARMLAND, ModBlocks.GROW_POT, ModBlocks.HYDRO_TRAY, Blocks.SCULK_CATALYST,
                Blocks.DEEPSLATE_TILES, Blocks.STONE, Blocks.MOSS_BLOCK}) {
            context.setBlock(SOIL, soil);
            context.assertTrue(!seedling.canSurvive(world, crop), "Echo OG can be planted on " + soil);
        }
        context.assertTrue(!ModBlocks.ECHO_OG_CROP.defaultBlockState().is(net.minecraft.tags.BlockTags.CROPS),
                "Echo OG is in #minecraft:crops, so bees hurry it");
        context.assertTrue(ModBlocks.ECHO_OG_CROP instanceof Mildew.Host host && host.catchesMildew(),
                "Echo OG does not catch Powdery Mildew");

        context.setBlock(SOIL, Blocks.SCULK);
        float pace = HempGrowth.chance(echo.growthResistance(world, crop), echo.fieldMoisture(world, crop), 1.0);
        context.assertTrue(Math.abs(pace - 0.05F) < 1e-6, "Echo OG grows at " + pace + " a tick, expected 1 in 20");

        context.setBlock(CROP, seedling);
        ItemStack meal = new ItemStack(Items.BONE_MEAL, 4);
        context.assertTrue(!BoneMealItem.growCrop(meal, world, crop), "Echo OG took bone meal");
        context.assertTrue(meal.getCount() == 4, "Echo OG ate the bone meal it refused");

        // Under this test's open sky the light is 15: it lives, and never grows.
        context.assertTrue(EchoOgCropBlock.lightAtTop(world, crop) > EchoOgCropBlock.MAX_LIGHT,
                "the open test floor is dark, so the light check proves nothing");
        for (int i = 0; i < 400; i++) {
            world.getBlockState(crop).randomTick(world, crop, world.getRandom());
        }
        context.assertTrue(world.getBlockState(crop).is(echo) && world.getBlockState(crop).getValue(IndicaCropBlock.AGE) == 0,
                "Echo OG grew under open sky, or died there: " + world.getBlockState(crop));

        // A sealed stone box: dark, so it grows.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    if (dx != 0 || dz != 0 || dy == 3) {
                        context.setBlock(CROP.offset(dx, dy, dz), Blocks.STONE);
                    }
                }
            }
        }
        context.setBlock(CROP.above(), Blocks.AIR);
        context.runAtTickTime(20, () -> {
            context.assertTrue(EchoOgCropBlock.lightAtTop(world, crop) <= EchoOgCropBlock.MAX_LIGHT,
                    "the sealed box is lit at " + EchoOgCropBlock.lightAtTop(world, crop));
            for (int i = 0; i < 2000 && world.getBlockState(crop).getValue(IndicaCropBlock.AGE) == 0; i++) {
                world.getBlockState(crop).randomTick(world, crop, world.getRandom());
            }
            context.assertTrue(world.getBlockState(crop).getValue(IndicaCropBlock.AGE) > 0,
                    "Echo OG never grew in 2000 random ticks in the dark");
            context.setBlock(CROP, seedling);
            context.setBlock(CROP.offset(1, 0, 0), Blocks.SEA_LANTERN);
        });
        context.runAtTickTime(40, () -> {
            context.assertTrue(EchoOgCropBlock.lightAtTop(world, crop) > EchoOgCropBlock.MAX_LIGHT,
                    "a sea lantern beside the plant left it at light " + EchoOgCropBlock.lightAtTop(world, crop));
            for (int i = 0; i < 2000; i++) {
                world.getBlockState(crop).randomTick(world, crop, world.getRandom());
            }
            context.assertTrue(world.getBlockState(crop).is(echo) && world.getBlockState(crop).getValue(IndicaCropBlock.AGE) == 0,
                    "Echo OG grew, or died, beside a sea lantern: " + world.getBlockState(crop));
            context.succeed();
        });
    }

    /**
     * Light is its lost light: a step that falls in the light stresses a weak plant from age 3, a neutral
     * one from 4, and a very resistant one shrugs it off as it shrugs off a lost lamp; in the dark no step
     * stresses it, and a Grow Lamp hanging there never raises its record.
     */
    public static void lightStressesEchoOg(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        // Open sky: light 15.
        context.assertTrue(feed(context, new BlockPos(1, 2, 1), 2, Resistance.VERY_WEAK, HIT) == GrowLight.STRESSED,
                "a very weak Echo OG fed from 2 to 3 in the light was not stressed");
        context.assertTrue(feed(context, new BlockPos(3, 2, 1), 2, Resistance.NEUTRAL, HIT) == GrowLight.NATURAL,
                "a neutral Echo OG fed from 2 to 3 in the light was stressed a stage early");
        context.assertTrue(feed(context, new BlockPos(5, 2, 1), 3, Resistance.NEUTRAL, HIT) == GrowLight.STRESSED,
                "a neutral Echo OG fed from 3 to 4 in the light was not stressed");
        context.assertTrue(feed(context, new BlockPos(1, 2, 4), 3, Resistance.VERY_RESISTANT, HIT) == GrowLight.NATURAL,
                "a very resistant Echo OG did not shrug off the light on a lucky roll");
        context.assertTrue(feed(context, new BlockPos(3, 2, 4), 3, Resistance.VERY_RESISTANT, MISS) == GrowLight.STRESSED,
                "a very resistant Echo OG shrugged off the light on an unlucky roll");
        context.assertTrue(feed(context, new BlockPos(5, 2, 4), 1, Resistance.VERY_WEAK, HIT) == GrowLight.NATURAL,
                "a very weak Echo OG fed from 1 to 2 in the light was stressed before age 3");

        // A sealed box with a dark Grow Lamp in it: no light, no stress, and the lamp counts for nothing.
        BlockPos dark = new BlockPos(3, 2, 6);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 4; dy++) {
                    if (dx != 0 || dz != 0 || dy == 4) {
                        context.setBlock(dark.offset(dx, dy, dz), Blocks.STONE);
                    }
                }
            }
        }
        // The plant's own floor now, not when it is planted: an open floor lets the sky in from below.
        context.setBlock(dark.below(), Blocks.SCULK);
        context.setBlock(dark.above(3), ModBlocks.GROW_LAMP.defaultBlockState());
        context.runAtTickTime(20, () -> {
            int light = EchoOgCropBlock.lightAtTop(world, context.absolutePos(dark));
            GrowLight record = feed(context, dark, 3, Resistance.VERY_WEAK, HIT);
            context.assertTrue(record == GrowLight.NATURAL, "a very weak Echo OG fed in the dark (light " + light
                    + ") came out " + record + ": stressed, or it took the dark lamp's record");
            context.succeed();
        });
    }

    /** Orbs are eaten, ten experience a step and the rest kept; a ripe plant and Purple Kush leave them alone. */
    public static void experienceFeedsEchoOg(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos rootRel = new BlockPos(2, 2, 2);
        context.setBlock(rootRel.below(), Blocks.SCULK);
        context.setBlock(rootRel, ModBlocks.ECHO_OG_CROP.defaultBlockState());
        BlockPos root = context.absolutePos(rootRel);
        ExperienceOrb ten = orb(world, root, 10);

        BlockPos ripeRel = new BlockPos(5, 2, 2);
        context.setBlock(ripeRel.below(), Blocks.SCULK);
        context.setBlock(ripeRel, ripe(ModBlocks.ECHO_OG_CROP, DoubleBlockHalf.LOWER));
        context.setBlock(ripeRel.above(), ripe(ModBlocks.ECHO_OG_CROP, DoubleBlockHalf.UPPER));
        ExperienceOrb pastIt = orb(world, context.absolutePos(ripeRel), 10);

        BlockPos kushRel = new BlockPos(2, 2, 5);
        context.setBlock(kushRel.below(), Blocks.FARMLAND);
        context.setBlock(kushRel, ModBlocks.INDICA_CROP.defaultBlockState());
        ExperienceOrb ignored = orb(world, context.absolutePos(kushRel), 10);

        // One step short of ripe: room for 10 points, so a 37-point orb stays whole for the player.
        BlockPos lateRel = new BlockPos(5, 2, 5);
        context.setBlock(lateRel.below(), Blocks.SCULK);
        context.setBlock(lateRel, ripe(ModBlocks.ECHO_OG_CROP, DoubleBlockHalf.LOWER).setValue(IndicaCropBlock.AGE, 6));
        context.setBlock(lateRel.above(), ripe(ModBlocks.ECHO_OG_CROP, DoubleBlockHalf.UPPER).setValue(IndicaCropBlock.AGE, 6));
        BlockPos late = context.absolutePos(lateRel);
        ExperienceOrb tooBig = orb(world, late, 37);

        context.runAtTickTime(5, () -> {
            context.assertTrue(ten.isRemoved(), "Echo OG left a 10-point orb uneaten");
            context.assertValueEqual(age(world, root), 1, "Echo OG's age after a 10-point orb");
            context.assertTrue(!pastIt.isRemoved(), "a ripe Echo OG ate an orb it had no step for");
            context.assertTrue(!ignored.isRemoved(), "Purple Kush ate an orb");
            context.assertTrue(!tooBig.isRemoved() && tooBig.getValue() == 37,
                    "an Echo OG one step from ripe ate a 37-point orb, the 27 it could not use lost");
            context.assertValueEqual(age(world, late), 6, "an Echo OG's age after an orb too big for it");
            context.assertValueEqual(record(world, late).storedExperience(), 0, "the experience an Echo OG kept from an orb too big for it");
            tooBig.discard();
            orb(world, late, 10);
            orb(world, root, 3);
        });
        context.runAtTickTime(10, () -> {
            context.assertValueEqual(age(world, root), 1, "Echo OG's age after a 3-point orb");
            context.assertValueEqual(record(world, root).storedExperience(), 3, "the experience Echo OG kept");
            context.assertValueEqual(age(world, late), IndicaCropBlock.MAX_AGE, "an Echo OG's age after an orb that fit its last step");
            orb(world, root, 7);
        });
        context.runAtTickTime(15, () -> {
            context.assertValueEqual(age(world, root), 2, "Echo OG's age after 3 then 7 points");
            context.assertValueEqual(record(world, root).storedExperience(), 0, "the experience Echo OG kept after a step");
            pastIt.discard();
            ignored.discard();
            context.succeed();
        });
    }

    /**
     * A sculk catalyst's charge on the sculk under the plant is a growth step at vanilla's price, and only
     * beyond vanilla's no-growth radius; bare sculk still grows its sensor or shrieker.
     */
    public static void aCatalystChargeFeedsEchoOg(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        SculkBlock sculk = (SculkBlock) Blocks.SCULK;
        SculkSpreader spreader = SculkSpreader.createLevelSpreader();
        BlockPos soilRel = new BlockPos(1, 1, 1);
        context.setBlock(soilRel, Blocks.SCULK);
        context.setBlock(soilRel.above(), ModBlocks.ECHO_OG_CROP.defaultBlockState());
        BlockPos soil = context.absolutePos(soilRel);
        BlockPos far = soil.offset(10, 0, 0);
        BlockPos near = soil.offset(1, 0, 0);

        int left = sculk.attemptUseCharge(new SculkSpreader.ChargeCursor(soil, 10), world, near, HIT, spreader, true);
        context.assertValueEqual(age(world, soil.above()), 0, "Echo OG's age after a charge beside its catalyst");
        context.assertTrue(left >= 9, "a charge beside the catalyst was spent: " + left + " left");

        left = sculk.attemptUseCharge(new SculkSpreader.ChargeCursor(soil, 10), world, far, HIT, spreader, true);
        context.assertValueEqual(age(world, soil.above()), 1, "Echo OG's age after a 10-point charge");
        context.assertValueEqual(left, 0, "the charge left after Echo OG fed");
        context.assertTrue(context.getBlockState(soilRel.above()).is(ModBlocks.ECHO_OG_CROP),
                "the charge grew a sensor over Echo OG");

        // Vanilla's decay gate passes on nextInt(10) == 0, the feed on nextInt(10) < charge: with a full charge and
        // every roll at 9, only the gate can hold the plant back, so a feed asked before the gate would show here.
        left = sculk.attemptUseCharge(new SculkSpreader.ChargeCursor(soil, 10), world, far, MISS, spreader, true);
        context.assertValueEqual(left, 10, "the charge left after vanilla's decay gate held it back");
        context.assertValueEqual(age(world, soil.above()), 1, "Echo OG's age after a charge vanilla's gate held back");

        // The control: bare sculk, far from the plant, still grows what vanilla grows there.
        BlockPos bareRel = new BlockPos(6, 1, 6);
        context.setBlock(bareRel, Blocks.SCULK);
        context.setBlock(bareRel.above(), Blocks.AIR);
        BlockPos bare = context.absolutePos(bareRel);
        sculk.attemptUseCharge(new SculkSpreader.ChargeCursor(bare, 10), world, bare.offset(10, 0, 0), HIT, spreader, true);
        BlockState grown = context.getBlockState(bareRel.above());
        context.assertTrue(grown.is(Blocks.SCULK_SENSOR) || grown.is(Blocks.SCULK_SHRIEKER),
                "a charge on bare sculk grew no sensor or shrieker: " + grown);
        context.succeed();
    }

    /**
     * Warped Kush's eleven-item harvest on matched seeds; a ripe plant's faint light on both halves and none
     * before; 1-3 experience from a ripe harvest, none from an unripe one.
     */
    public static void echoOgHarvestAndGlow(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(CROP));
        LootTable echo = table(world, ModBlocks.ECHO_OG_CROP);
        LootTable kush = table(world, ModBlocks.WARPED_KUSH_CROP);
        Random seeds = new Random(SEED_SOURCE);
        for (int roll = 0; roll < ROLLS; roll++) {
            long seed = seeds.nextLong();
            int[] e = harvest(echo, world, origin, ripe(ModBlocks.ECHO_OG_CROP, DoubleBlockHalf.LOWER), seed,
                    ModItems.ECHO_OG_BUDS, ModItems.ECHO_OG_SEEDS);
            int[] k = harvest(kush, world, origin, ripe(ModBlocks.WARPED_KUSH_CROP, DoubleBlockHalf.LOWER), seed,
                    ModItems.WARPED_KUSH_BUDS, ModItems.WARPED_KUSH_SEEDS);
            context.assertTrue(java.util.Arrays.equals(e, k), "Echo OG paid buds/leaves/seeds/stems "
                    + java.util.Arrays.toString(e) + " against Warped Kush's " + java.util.Arrays.toString(k));
        }

        context.assertValueEqual(ripe(ModBlocks.ECHO_OG_CROP, DoubleBlockHalf.LOWER).getLightEmission(), 1, "a ripe root's light");
        context.assertValueEqual(ripe(ModBlocks.ECHO_OG_CROP, DoubleBlockHalf.UPPER).getLightEmission(), 1, "a ripe tip's light");
        context.assertValueEqual(ripe(ModBlocks.ECHO_OG_CROP, DoubleBlockHalf.LOWER).setValue(IndicaCropBlock.AGE, 6)
                .getLightEmission(), 0, "an unripe plant's light");

        int total = 0;
        for (int x = 0; x < 7; x += 2) {
            for (int z = 0; z < 7; z += 3) {
                BlockPos rootRel = new BlockPos(x, 2, z);
                context.setBlock(rootRel.below(), Blocks.SCULK);
                context.setBlock(rootRel, ripe(ModBlocks.ECHO_OG_CROP, DoubleBlockHalf.LOWER));
                context.setBlock(rootRel.above(), ripe(ModBlocks.ECHO_OG_CROP, DoubleBlockHalf.UPPER));
                BlockPos root = context.absolutePos(rootRel);
                world.destroyBlock(root, true);
                int got = experience(world, root);
                context.assertTrue(got >= 1 && got <= 3, "a ripe Echo OG harvest dropped " + got + " experience, expected 1 to 3");
                total += got;
            }
        }
        context.assertTrue(total > 12, "twelve ripe harvests dropped only " + total + " experience");
        BlockPos youngRel = new BlockPos(1, 2, 1);
        context.setBlock(youngRel.below(), Blocks.SCULK);
        context.setBlock(youngRel, ModBlocks.ECHO_OG_CROP.defaultBlockState().setValue(IndicaCropBlock.AGE, 6));
        world.destroyBlock(context.absolutePos(youngRel), true);
        context.assertValueEqual(experience(world, context.absolutePos(youngRel)), 0, "an unripe Echo OG's experience");
        context.succeed();
    }

    // ----- the smoke -----

    /**
     * CBG-dominant, borneol, its own crop as its wild plant, no oven; a pipe gives Hush on the hit and
     * Darkness on the exhale for a quarter of it, and no Hunger; debuffs=false takes the Darkness only.
     */
    public static void echoOgSmokesQuiet(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Holder<Strain> echo = world.registryAccess().lookupOrThrow(Strain.REGISTRY_KEY).getOrThrow(ModStrains.ECHO_OG);
        context.assertTrue(echo.value().traits().chemotype() == Chemotype.CBG_DOMINANT, "Echo OG is not CBG-dominant");
        context.assertTrue(!Chemotypes.infusable(echo.value().traits().chemotype()), "Echo OG's buds can become an edible");
        context.assertValueEqual(echo.value().traits().terpene(), Optional.of(Terpene.BORNEOL), "Echo OG's terpene");
        context.assertTrue(echo.value().wildFlower().filter(f -> f == ModBlocks.ECHO_OG_CROP).isPresent(),
                "Echo OG's wild plant is " + echo.value().wildFlower());
        context.assertTrue(world.recipeAccess().byKey(ResourceKey.create(Registries.RECIPE,
                        Identifier.fromNamespaceAndPath("hempdustry", "decarboxylating/echo_og_buds"))).isEmpty(),
                "the oven takes Echo OG buds");

        List<MobEffectInstance> three = echo.value().effects(3, 700);
        context.assertTrue(three.stream().noneMatch(e -> e.getEffect().is(MobEffects.HUNGER) || e.getEffect().is(MobEffects.NAUSEA)),
                "a bowl of Echo OG gave Hunger or Nausea: " + three);
        MobEffectInstance hushOfThree = three.stream().filter(e -> e.getEffect().is(ModEffects.HUSH)).findFirst().orElse(null);
        context.assertTrue(hushOfThree != null, "a bowl of Echo OG gave no Hush: " + three);
        context.assertValueEqual(hushOfThree.getAmplifier(), 0, "Hush's level from a bowl of three (Hush scaled with the dose)");
        ConfigGameTest.withConfig("{ \"effects\": { \"debuffs\": false } }", () -> {
            List<MobEffectInstance> kept = EffectPolicy.filter(echo.value().effects(1, 700));
            context.assertTrue(kept.stream().noneMatch(e -> e.getEffect().is(MobEffects.DARKNESS)),
                    "debuffs=false left Echo OG's Darkness on");
            context.assertTrue(kept.stream().anyMatch(e -> e.getEffect().is(ModEffects.HUSH)),
                    "debuffs=false took Echo OG's Hush too");
        });

        int length = DeviceType.PIPE.builtInStats().durationTicks();
        ServerPlayer smoker = Players.survival(context);
        ItemStack pipe = new ItemStack(ModItems.WOODEN_PIPE);
        pipe.set(ModComponents.SMOKE_CONTENTS, SmokeContents.of(echo, 1));
        pipe.set(ModComponents.CHARGES, DeviceType.PIPE.builtInBowl().hits());
        smoker.setItemInHand(InteractionHand.MAIN_HAND, pipe);
        smoker.getCooldowns().removeCooldown(BuiltInRegistries.ITEM.getKey(ModItems.WOODEN_PIPE));
        Players.smoke(smoker, InteractionHand.MAIN_HAND);
        MobEffectInstance hush = smoker.getEffect(ModEffects.HUSH);
        context.assertTrue(hush != null && hush.getDuration() == length, "the hit did not give " + length + " ticks of Hush: " + hush);
        context.assertTrue(!smoker.hasEffect(MobEffects.HUNGER), "an Echo OG pipe gave Hunger");
        context.assertTrue(!smoker.hasEffect(MobEffects.DARKNESS), "Darkness landed with the hit, not the exhale");
        context.runAtTickTime(EXHALE_TICKS + 5, () -> {
            MobEffectInstance dark = smoker.getEffect(MobEffects.DARKNESS);
            int expected = Math.round(length * 0.25F);
            context.assertTrue(dark != null, "no Darkness on the exhale");
            context.assertTrue(dark.getDuration() <= expected && dark.getDuration() > expected - 10,
                    "the exhale's Darkness lasts " + dark.getDuration() + ", expected a quarter of the hit (" + expected + ")");
            context.succeed();
        });
    }

    /**
     * Hush: a sensor and a Warden let a Hushed body's own vibrations go — steps, eating — and hear what it
     * does to the world; a bare player is heard (the control). Sneak 420 is earned off either, as vanilla's
     * Sneak 100 is. (A shrieker hears only a sensor clicking, {@code #shrieker_can_listen}, never a step.)
     */
    public static void hushSilencesYourBody(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        AdvancementHolder sneak = world.getServer().getAdvancements().get(Identifier.fromNamespaceAndPath("hempdustry", "sneak_420"));
        context.assertTrue(sneak != null, "hempdustry:sneak_420 is not loaded");
        context.assertTrue(sneak.value().display().map(DisplayInfo::hidden).orElse(false), "Sneak 420 is not hidden");
        context.assertTrue(sneak.value().parent().equals(Optional.of(Identifier.fromNamespaceAndPath("hempdustry", "first_contact"))),
                "Sneak 420 does not hang under First Contact");

        ServerPlayer bare = Players.survival(context);
        ServerPlayer hushed = Players.survival(context);
        Vec3 at = Vec3.atCenterOf(context.absolutePos(new BlockPos(4, 2, 4)));
        bare.setPos(at);
        hushed.setPos(at);
        hushed.addEffect(new MobEffectInstance(ModEffects.HUSH, 200));

        context.assertTrue(hears(context, sensor(context, new BlockPos(1, 2, 1)), GameEvent.STEP, bare, at),
                "a sculk sensor did not hear a bare player's step, so the rest proves nothing");
        context.assertTrue(!hushed.getAdvancements().getOrStartProgress(sneak).isDone(),
                "Sneak 420 was granted before any listener missed a step");
        context.assertTrue(!hears(context, sensor(context, new BlockPos(1, 2, 7)), GameEvent.STEP, hushed, at),
                "a sculk sensor heard a Hushed step");
        context.assertTrue(hushed.getAdvancements().getOrStartProgress(sneak).isDone(),
                "a sensor missing a Hushed step did not grant Sneak 420");
        context.assertTrue(!hears(context, sensor(context, new BlockPos(7, 2, 7)), GameEvent.EAT, hushed, at),
                "a sculk sensor heard a Hushed player eat");
        context.assertTrue(hears(context, sensor(context, new BlockPos(1, 2, 4)), GameEvent.BLOCK_PLACE, hushed, at),
                "Hush silenced a block placed");
        context.assertTrue(hears(context, sensor(context, new BlockPos(7, 2, 4)), GameEvent.CONTAINER_OPEN, hushed, at),
                "Hush silenced a chest opened");

        Warden warden = EntityTypes.WARDEN.create(world, EntitySpawnReason.COMMAND);
        context.assertTrue(warden != null, "no Warden");
        warden.setPos(Vec3.atCenterOf(context.absolutePos(new BlockPos(4, 2, 1))));
        world.addFreshEntity(warden);
        try {
            ServerPlayer second = Players.survival(context);
            second.setPos(at);
            second.addEffect(new MobEffectInstance(ModEffects.HUSH, 200));
            context.assertTrue(!new VibrationSystem.Listener(warden).handleGameEvent(world, GameEvent.STEP, GameEvent.Context.of(second), at),
                    "a Warden heard a Hushed step");
            context.assertTrue(second.getAdvancements().getOrStartProgress(sneak).isDone(),
                    "a Warden missing a Hushed step did not grant Sneak 420");
            context.assertTrue(new VibrationSystem.Listener(warden).handleGameEvent(world, GameEvent.STEP, GameEvent.Context.of(bare), at),
                    "a Warden did not hear a bare step");
        } finally {
            warden.discard();
        }
        context.succeed();
    }

    /** A Warden's sniff reaches a Hushed player at 10 and 12 blocks, a bare one only within 6, and neither at 13. */
    public static void aWardenSmellsTheSmoke(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Vec3 from = Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1)));
        context.assertTrue(!sniffs(context, from, 10, false), "a Warden smelled a bare player 10 blocks off");
        context.assertTrue(sniffs(context, from, 5, false), "a Warden did not smell a bare player 5 blocks off (the control)");
        context.assertTrue(sniffs(context, from, 10, true), "a Warden did not smell a Hushed player 10 blocks off");
        context.assertTrue(sniffs(context, from, HushEffect.SNIFF_DISTANCE - 0.5, true),
                "a Warden did not smell a Hushed player just inside 12 blocks");
        context.assertTrue(!sniffs(context, from, 13, true), "a Warden smelled a Hushed player 13 blocks off");
        context.succeed();
    }

    // ----- where it is found -----

    /** Buds in about one Ancient City chest in five, seeds in about half its ice boxes, and nowhere else. */
    public static void echoOgStaysInTheCity(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Predicate<ItemStack> buds = stack -> stack.is(ModItems.ECHO_OG_BUDS);
        Predicate<ItemStack> seeds = stack -> stack.is(ModItems.ECHO_OG_SEEDS);
        BlockPos at = new BlockPos(2, 2, 2);
        int city = rolls(context, BuiltInLootTables.ANCIENT_CITY, at, buds);
        context.assertTrue(city >= 150 && city <= 250, "an Ancient City chest held Echo OG buds " + city + " times in 1000, expected about 200");
        int ice = rolls(context, BuiltInLootTables.ANCIENT_CITY_ICE_BOX, at, seeds);
        context.assertTrue(ice >= 420 && ice <= 580, "the ice box held Echo OG seeds " + ice + " times in 1000, expected about 500");
        context.assertValueEqual(rolls(context, BuiltInLootTables.ANCIENT_CITY, at, seeds), 0, "Ancient City chests holding Echo OG seeds");
        context.assertValueEqual(rolls(context, BuiltInLootTables.ANCIENT_CITY_ICE_BOX, at, buds), 0, "the ice box holding Echo OG buds");
        for (ResourceKey<LootTable> none : List.of(BuiltInLootTables.SIMPLE_DUNGEON, BuiltInLootTables.SHIPWRECK_SUPPLY,
                BuiltInLootTables.VILLAGE_PLAINS_HOUSE, BuiltInLootTables.ABANDONED_MINESHAFT, BuiltInLootTables.WOODLAND_MANSION,
                BuiltInLootTables.PILLAGER_OUTPOST, BuiltInLootTables.BASTION_TREASURE, BuiltInLootTables.END_CITY_TREASURE)) {
            context.assertValueEqual(rolls(context, none, at, seeds.or(buds)), 0, none.identifier() + " holding Echo OG");
        }
        String grass = LootTable.DIRECT_CODEC.encodeStart(world.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                world.getServer().reloadableRegistries().getLootTable(Blocks.TALL_GRASS.getLootTable().orElseThrow())).getOrThrow().toString();
        context.assertTrue(grass.contains("\"hempdustry:indica_seeds\"") && !grass.contains("\"hempdustry:echo_og_seeds\""),
                "tall grass names Echo OG seeds, or no hemp seeds at all");
        String warden = LootTable.DIRECT_CODEC.encodeStart(world.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                world.getServer().reloadableRegistries().getLootTable(EntityTypes.WARDEN.getDefaultLootTable().orElseThrow())).getOrThrow().toString();
        context.assertTrue(warden.contains("\"minecraft:sculk_catalyst\"") && !warden.contains("echo_og"), "the Warden drops Echo OG");
        context.succeed();
    }

    /**
     * The wild plant is the crop, ripe and whole, on sculk; its placement is held to the inside of an Ancient
     * City, so the same sculk with no city on it grows none.
     */
    public static void wildEchoOgNeedsACity(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        var tags = world.registryAccess();
        context.assertTrue(tags.lookupOrThrow(Registries.STRUCTURE).getOrThrow(BuiltinStructures.ANCIENT_CITY)
                .is(ModTags.Structures.ECHO_OG_STRUCTURES), "the Ancient City is not in #hempdustry:echo_og_structures");
        context.assertTrue(tags.lookupOrThrow(Registries.BIOME).getOrThrow(net.minecraft.world.level.biome.Biomes.DEEP_DARK)
                .is(ModTags.Biomes.ECHO_OG_GEN), "the deep dark is not in #hempdustry:echo_og_gen");

        PlacedFeature placed = tags.lookupOrThrow(Registries.PLACED_FEATURE).getOrThrow(ModPlacedFeatures.ECHO_OG_PLACED_KEY).value();
        long filters = placed.placement().stream().filter(m -> m instanceof ModPlacementModifiers.InStructurePlacementFilter f
                && f.structures().equals(ModTags.Structures.ECHO_OG_STRUCTURES)).count();
        context.assertValueEqual(filters, 2L, "the city filters on wild Echo OG's placement");

        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                context.setBlock(new BlockPos(x, 1, z), Blocks.SCULK);
            }
        }
        var generator = world.getChunkSource().getGenerator();
        RandomSource random = RandomSource.create(7L);
        var column = tags.lookupOrThrow(Registries.FEATURE).getOrThrow(ModConfiguredFeatures.ECHO_OG_KEY).value();
        context.assertTrue(column.place(world, generator, random, context.absolutePos(new BlockPos(1, 2, 1))),
                "the wild Echo OG column did not place on sculk");
        BlockState lower = context.getBlockState(new BlockPos(1, 2, 1));
        BlockState upper = context.getBlockState(new BlockPos(1, 3, 1));
        context.assertTrue(lower.is(ModBlocks.ECHO_OG_CROP) && lower.getValue(IndicaCropBlock.HALF) == DoubleBlockHalf.LOWER
                && lower.getValue(IndicaCropBlock.AGE) == IndicaCropBlock.MAX_AGE, "the wild plant's root is " + lower);
        context.assertTrue(upper.is(ModBlocks.ECHO_OG_CROP) && upper.getValue(IndicaCropBlock.HALF) == DoubleBlockHalf.UPPER
                && upper.getValue(IndicaCropBlock.AGE) == IndicaCropBlock.MAX_AGE, "the wild plant's tip is " + upper);

        int scatter = 0;
        while (!(placed.placement().get(scatter) instanceof net.minecraft.world.level.levelgen.placement.OffsetPlacement)) {
            scatter++;
        }
        PlacedFeature onePlant = new PlacedFeature(placed.feature(), placed.placement().subList(scatter + 1, placed.placement().size()));
        context.assertTrue(!onePlant.place(world, generator, random, context.absolutePos(new BlockPos(5, 2, 5))),
                "wild Echo OG grew on sculk with no Ancient City on it");
        context.assertTrue(context.getBlockState(new BlockPos(5, 2, 5)).isAir(), "the refused placement left a block");

        // Inside a city, as chunk generation sees one: a proto-chunk floored with sculk, holding an Ancient City
        // start whose one piece covers the chunk's west half, the plant placed through vanilla's WorldGenRegion.
        ChunkPos at = new ChunkPos(-30000, 30000);
        int floor = -30;
        ProtoChunk proto = WildPlantChunkGameTest.protoChunk(world, at);
        for (int x = at.getMinBlockX(); x <= at.getMaxBlockX(); x++) {
            for (int z = at.getMinBlockZ(); z <= at.getMaxBlockZ(); z++) {
                proto.setBlockState(new BlockPos(x, floor, z), Blocks.SCULK.defaultBlockState(), 0);
            }
        }
        Structure city = tags.lookupOrThrow(Registries.STRUCTURE).getOrThrow(BuiltinStructures.ANCIENT_CITY).value();
        BoundingBox hall = new BoundingBox(at.getMinBlockX(), floor, at.getMinBlockZ(), at.getMinBlockX() + 7, floor + 8, at.getMaxBlockZ());
        StructurePiece piece = new StructurePiece(StructurePieceType.JIGSAW, 0, hall) {
            @Override
            protected void addAdditionalSaveData(StructurePieceSerializationContext saving, CompoundTag tag) {
            }

            @Override
            public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator chunks,
                                    RandomSource rolls, BoundingBox box, ChunkPos chunk, BlockPos pivot) {
            }
        };
        proto.setStartForStructure(city, new StructureStart(city, at, 0, new PiecesContainer(List.of(piece))));
        proto.addReferenceForStructure(city, at.pack());
        WorldGenRegion region = WildPlantChunkGameTest.regionOf(world, proto);
        BlockPos inHall = new BlockPos(at.getMinBlockX() + 4, floor + 1, at.getMinBlockZ() + 8);
        context.assertTrue(onePlant.place(region, generator, random, inHall), "wild Echo OG did not grow on sculk inside an Ancient City's piece");
        context.assertTrue(proto.getBlockState(inHall).is(ModBlocks.ECHO_OG_CROP) && proto.getBlockState(inHall.above()).is(ModBlocks.ECHO_OG_CROP),
                "wild Echo OG in a city's piece placed as " + proto.getBlockState(inHall) + " under " + proto.getBlockState(inHall.above()));
        BlockPos pastHall = inHall.east(8);
        context.assertTrue(!onePlant.place(region, generator, random, pastHall) && proto.getBlockState(pastHall).isAir(),
                "wild Echo OG grew on sculk in a city's chunk but outside its pieces");
        // Only inside the chunk being decorated: a point in a neighbour is inside a piece not placed there yet.
        var cityFilter = ModPlacementModifiers.InStructurePlacementFilter.of(ModTags.Structures.ECHO_OG_STRUCTURES);
        PlacementContext placing = new PlacementContext(region, generator, Optional.empty());
        context.assertTrue(cityFilter.shouldPlace(placing, random, inHall), "test setup: the city filter refuses its own chunk's hall");
        context.assertTrue(!cityFilter.shouldPlace(placing, random, inHall.west(5)),
                "the city filter let through a point in a neighbour chunk, not decorated yet");
        context.succeed();
    }

    // ----- helpers -----

    /** A seedling grown to {@code from} on sculk at {@code rootRel}, with {@code light} genes, fed one charge step. */
    private static GrowLight feed(GameTestHelper context, BlockPos rootRel, int from, Resistance light, RandomSource random) {
        ServerLevel world = context.getLevel();
        context.setBlock(rootRel.below(), Blocks.SCULK);
        context.setBlock(rootRel.above(), Blocks.AIR);
        context.setBlock(rootRel, ModBlocks.ECHO_OG_CROP.defaultBlockState().setValue(IndicaCropBlock.AGE, from));
        Genes.set(context, rootRel, ResistanceGenes.NEUTRAL.with(com.warlonmhite.hempdustry.strain.StressPhenomenon.LIGHT, light));
        BlockPos root = context.absolutePos(rootRel);
        ((EchoOgCropBlock) ModBlocks.ECHO_OG_CROP).eatCharge(world, root, random);
        BlockState after = world.getBlockState(root);
        context.assertValueEqual(after.getValue(IndicaCropBlock.AGE), from + 1, "Echo OG's age after a fed step from " + from);
        return after.getValue(GrowLight.PROPERTY);
    }

    private static ExperienceOrb orb(ServerLevel world, BlockPos root, int value) {
        ExperienceOrb orb = new ExperienceOrb(world, root.getX() + 0.5, root.getY() + 0.05, root.getZ() + 0.5, value);
        orb.setDeltaMovement(Vec3.ZERO);
        world.addFreshEntity(orb);
        return orb;
    }

    private static int experience(ServerLevel world, BlockPos at) {
        int total = 0;
        for (ExperienceOrb orb : world.getEntitiesOfClass(ExperienceOrb.class, new AABB(at).inflate(2.0))) {
            total += orb.getValue() * ((ExperienceOrbAccessor) orb).hempdustry$getCount();
            orb.discard();
        }
        return total;
    }

    private static int age(ServerLevel world, BlockPos pos) {
        return world.getBlockState(pos).getValue(IndicaCropBlock.AGE);
    }

    private static PlantRecordBlockEntity record(ServerLevel world, BlockPos root) {
        return (PlantRecordBlockEntity) world.getBlockEntity(root);
    }

    private static VibrationSystem.Listener sensor(GameTestHelper context, BlockPos at) {
        context.setBlock(at, Blocks.SCULK_SENSOR);
        return ((SculkSensorBlockEntity) context.getLevel().getBlockEntity(context.absolutePos(at))).getListener();
    }

    private static boolean hears(GameTestHelper context, VibrationSystem.Listener listener, Holder<GameEvent> event,
                                 ServerPlayer source, Vec3 at) {
        return listener.handleGameEvent(context.getLevel(), event, GameEvent.Context.of(source), at);
    }

    /** Whether a Warden at {@code from} ends a sniff angry at a player {@code distance} blocks off, flat. */
    private static boolean sniffs(GameTestHelper context, Vec3 from, double distance, boolean hushed) {
        ServerLevel world = context.getLevel();
        Warden warden = EntityTypes.WARDEN.create(world, EntitySpawnReason.COMMAND);
        ServerPlayer player = Players.survival(context);
        try {
            warden.setPos(from);
            world.addFreshEntity(warden);
            player.setPos(from.add(distance, 0, 0));
            if (hushed) {
                player.addEffect(new MobEffectInstance(ModEffects.HUSH, 200));
            }
            warden.getBrain().setMemory(MemoryModuleType.NEAREST_ATTACKABLE, player);
            new Sniffing<Warden>(1).doStop(world, warden, world.getGameTime());
            return warden.getAngerManagement().getActiveAnger(player) > 0;
        } finally {
            warden.discard();
        }
    }

    private static BlockState ripe(Block crop, DoubleBlockHalf half) {
        return Defoliation.unworked(crop.defaultBlockState())
                .setValue(IndicaCropBlock.HALF, half)
                .setValue(IndicaCropBlock.AGE, IndicaCropBlock.MAX_AGE);
    }

    private static LootTable table(ServerLevel world, Block block) {
        return world.getServer().reloadableRegistries().getLootTable(block.getLootTable().orElseThrow());
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
}
