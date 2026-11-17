package com.warlonmhite.hempdustry.test;

import com.warlonmhite.hempdustry.block.ModBlocks;
import com.warlonmhite.hempdustry.block.custom.Defoliation;
import com.warlonmhite.hempdustry.block.custom.Mildew;
import com.warlonmhite.hempdustry.strain.Resistance;
import com.warlonmhite.hempdustry.strain.ResistanceGenes;
import com.warlonmhite.hempdustry.strain.StressPhenomenon;
import com.warlonmhite.hempdustry.item.ModItems;
import com.warlonmhite.hempdustry.util.ModTags;
import java.util.List;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.properties.PotentSulfurState;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.phys.AABB;

/**
 * Powdery mildew ({@link Mildew}): where it starts, how it spreads, what clears it, and which strains it and the
 * sulfur gas leave alone. Every roll is
 * decided ({@link #HIT} lands under any odds above zero, {@link #MISS} under none), so each probe
 * either certainly catches or certainly does not, and what is asserted is the wiring and the
 * agreed numbers, never a measured rate.
 */
public final class MildewGameTest {
    private static final BlockPos SOIL = new BlockPos(1, 1, 1);
    private static final BlockPos CROP = SOIL.above();
    /** A pane of glass over the plant: a roof that lets the light in, as a greenhouse's does. */
    private static final BlockPos ROOF = CROP.above(3);

    private static final RandomSource HIT = decided(0.0F, 0);
    private static final RandomSource MISS = decided(0.999F, 0);
    /** Under a roof's onset (1 in 150) and over the open sky's (1 in 1,500): it tells the two apart. */
    private static final float BETWEEN_ROLL = 0.005F;
    private static final RandomSource BETWEEN = decided(BETWEEN_ROLL, 0);

    /**
     * Onset: 1 in 150 a step under a roof, 1 in 1,500 under open sky. {@link #BETWEEN} lands under the first and
     * over the second, so it tells a roof from the sky: glass, stone, hempcrete and a hung glowstone are roofs;
     * the open sky, a tree's leaves and a Grow Lamp are not.
     */
    public static void mildewStartsTenTimesLikelierUnderARoof(GameTestHelper context) {
        context.assertTrue(Mildew.ONSET == 1.0F / 150 && Mildew.ONSET_OUTDOORS == 1.0F / 1500
                        && Mildew.SPREAD == 1.0F / 2 && Mildew.SPREAD_OUTDOORS == 1.0F / 100,
                "the mildew odds are not 1 in 150 a step under a roof and 1 in 1,500 outdoors, and 1 in 2 a spread "
                        + "under a roof and 1 in 100 outdoors");

        openSky(context, CROP);
        expect(context, step(context, ModBlocks.INDICA_CROP, 2, Blocks.AIR, BETWEEN), Mildew.CLEAN,
                "a field plant under open sky, at " + BETWEEN_ROLL);
        expect(context, step(context, ModBlocks.INDICA_CROP, 2, Blocks.AIR, decided(0.00066F, 0)), Mildew.MILDEWED,
                "a field plant under open sky, at 0.00066");
        expect(context, step(context, ModBlocks.INDICA_CROP, 2, Blocks.AIR, decided(0.00067F, 0)), Mildew.CLEAN,
                "a field plant under open sky, at 0.00067");
        expect(context, step(context, ModBlocks.INDICA_CROP, 2, Blocks.OAK_LEAVES, BETWEEN), Mildew.CLEAN,
                "a plant under a tree's leaves, at " + BETWEEN_ROLL);
        expect(context, step(context, ModBlocks.INDICA_CROP, 2, Blocks.OAK_LEAVES, decided(0.00066F, 0)), Mildew.MILDEWED,
                "a plant under a tree's leaves, at 0.00066");
        expect(context, step(context, ModBlocks.INDICA_CROP, 2, ModBlocks.GROW_LAMP, BETWEEN), Mildew.CLEAN,
                "a plant under a Grow Lamp, at " + BETWEEN_ROLL);
        context.assertTrue(context.getBlockState(ROOF).is(ModBlocks.GROW_LAMP), "the Grow Lamp over the plant did not stay, "
                + "so the probe under it proves nothing");
        expect(context, step(context, ModBlocks.INDICA_CROP, 2, ModBlocks.HEMPCRETE_BLOCK, BETWEEN), Mildew.MILDEWED,
                "a plant under a hempcrete roof, at " + BETWEEN_ROLL);
        expect(context, step(context, ModBlocks.INDICA_CROP, 2, Blocks.GLOWSTONE, BETWEEN), Mildew.MILDEWED,
                "a plant under a hung glowstone, at " + BETWEEN_ROLL);
        expect(context, step(context, ModBlocks.INDICA_CROP, 2, Blocks.GLASS, BETWEEN), Mildew.MILDEWED,
                "a plant under a glass roof, at " + BETWEEN_ROLL);
        expect(context, step(context, ModBlocks.INDICA_CROP, 2, Blocks.GLASS, MISS), Mildew.CLEAN,
                "a missed roll under a glass roof");
        expect(context, step(context, ModBlocks.INDICA_CROP, 4, Blocks.GLASS, HIT), Mildew.MILDEWED,
                "a plant under a glass roof");
        context.assertTrue(context.getBlockState(CROP.above()).getValue(Mildew.PROPERTY) == Mildew.MILDEWED,
                "a mildewed plant is drawn clean at the top: its upper half reads "
                        + context.getBlockState(CROP.above()).getValue(Mildew.PROPERTY));
        expect(context, step(context, ModBlocks.SATIVA_CROP, 5, Blocks.STONE, HIT), Mildew.MILDEWED,
                "a Lemon Haze plant under a stone roof");
        expect(context, step(context, ModBlocks.BELDIA_CROP, 1, Blocks.GLASS, HIT), Mildew.MILDEWED,
                "a Beldía seedling under a glass roof");

        // Nothing of ours claims to resist it; the tag is a datapack's to fill.
        context.assertTrue(BuiltInRegistries.BLOCK.get(ModTags.Blocks.MILDEW_RESISTANT).map(tag -> tag.size() == 0).orElse(true),
                "#hempdustry:mildew_resistant names a crop of ours, which the mod never claims");

        // Switched off: nothing catches it, and a plant that has it is clean on its next tick.
        ConfigGameTest.withConfig("{ \"world\": { \"mildew\": false } }", () -> {
            expect(context, step(context, ModBlocks.INDICA_CROP, 4, Blocks.GLASS, HIT), Mildew.CLEAN,
                    "a plant under a roof with world.mildew=false");
            plant(context, ModBlocks.INDICA_CROP, 4, Mildew.MILDEWED);
            Mildew.randomTick(context.getLevel(), context.absolutePos(CROP), MISS);
            expect(context, context.getBlockState(CROP.above()).getValue(Mildew.PROPERTY), Mildew.CLEAN,
                    "a mildewed plant's random tick with world.mildew=false");
        });
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

        // And the other way: the open-sky plant, mildewed, reaches its roofed neighbour at a roof's 1 in 2.
        for (float roll : new float[]{0.51F, 0.49F}) {
            sow(context, sick, ModBlocks.INDICA_CROP, 4, Mildew.CLEAN);
            sow(context, east, ModBlocks.INDICA_CROP, 4, Mildew.MILDEWED);
            Mildew.randomTick(world, context.absolutePos(east), decided(roll, WEST));
            expect(context, context.getBlockState(sick).getValue(Mildew.PROPERTY), roll < 0.5F ? Mildew.MILDEWED : Mildew.CLEAN,
                    "a roofed neighbour of a mildewed field plant, at " + roll);
        }
        context.succeed();
    }

    /**
     * A plant's mildew resistance scales its onset (1 in 150 at neutral under a roof: very weak 1 in 75, very
     * resistant 1 in 600; 1 in 1,500 outdoors: 1 in 750, 1 in 6,000) and the spread to it (1 in 2 at neutral
     * under a roof: very weak certain, very resistant 1 in 8; 1 in 100 outdoors: 1 in 50, 1 in 400).
     */
    public static void mildewResistanceScalesOnsetAndSpread(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Block indica = ModBlocks.INDICA_CROP;
        float[][] onsets = {{0.013F, 0.014F}, {0.0066F, 0.0067F}, {0.0016F, 0.0017F}};
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
        float[][] spreads = {{0.999F, 0.999F}, {0.49F, 0.51F}, {0.12F, 0.13F}};
        for (int i = 0; i < steps.length; i++) {
            for (int hit = 0; hit < 2; hit++) {
                sow(context, east, indica, 4, Mildew.CLEAN);
                Genes.set(context, east, ResistanceGenes.NEUTRAL.with(StressPhenomenon.MILDEW, steps[i]));
                sow(context, sick, indica, 4, Mildew.MILDEWED);
                Mildew.randomTick(world, context.absolutePos(sick), decided(spreads[i][hit], EAST));
                Mildew expected = hit == 0 || steps[i] == Resistance.VERY_WEAK ? Mildew.MILDEWED : Mildew.CLEAN;
                expect(context, context.getBlockState(east).getValue(Mildew.PROPERTY), expected,
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
