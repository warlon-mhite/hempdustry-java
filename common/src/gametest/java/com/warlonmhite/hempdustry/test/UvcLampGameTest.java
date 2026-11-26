package com.warlonmhite.hempdustry.test;

import com.warlonmhite.hempdustry.block.ModBlocks;
import com.warlonmhite.hempdustry.block.custom.Defoliation;
import com.warlonmhite.hempdustry.block.custom.GrowLight;
import com.warlonmhite.hempdustry.block.custom.Mildew;
import com.warlonmhite.hempdustry.block.custom.PlantStress;
import com.warlonmhite.hempdustry.block.custom.UvcLampBlock;
import com.warlonmhite.hempdustry.util.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;

/**
 * The UV-C Lamp ({@link UvcLampBlock}): it lights itself at night under redstone and not by day, gives no light and
 * is no roof, works only on a plant in the dark within its 5 × 5 and three blocks, clears a mildewed plant of any age
 * to clean (and leaves a sulfured one), and takes half off the odds mildew starts on a plant and spreads to it. Every
 * roll is decided, as in {@link MildewGameTest}, so what is asserted is the agreed rule and never a measured rate.
 */
public final class UvcLampGameTest {
    private static final long NOON = 6000;
    private static final long MIDNIGHT = 18000;
    private static final int EAST = 4;

    private static final BlockPos ROOT = new BlockPos(3, 2, 3);
    private static final RandomSource MISS = decided(0.999F, 0);

    /** A lamp lights at night under a signal, goes dark by day, keeps its state through the twilight, and obeys the power. */
    public static void aUvcLampLightsInTheDarkOnly(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos at = new BlockPos(3, 4, 3);
        BlockPos abs = context.absolutePos(at);
        long[] twilights = twilights(context, abs);

        WhiteWidowGameTest.at(context, MIDNIGHT, () -> {
            hang(context, at);
            context.assertTrue(lit(context, at), "a powered lamp placed at midnight is dark");
            context.assertTrue(context.getBlockState(at).getValue(UvcLampBlock.POWERED), "the lamp does not read its signal");
        });
        for (long twilight : twilights) {
            WhiteWidowGameTest.at(context, twilight, () -> {
                tick(world, abs);
                context.assertTrue(lit(context, at), "a lit lamp went out in the twilight at " + twilight);
            });
        }
        WhiteWidowGameTest.at(context, NOON, () -> {
            tick(world, abs);
            context.assertTrue(!lit(context, at), "a lamp is still lit at noon");
            context.assertTrue(context.getBlockState(at).getValue(UvcLampBlock.POWERED), "a dark lamp lost its signal");
        });
        for (long twilight : twilights) {
            WhiteWidowGameTest.at(context, twilight, () -> {
                tick(world, abs);
                context.assertTrue(!lit(context, at), "a dark lamp lit in the twilight at " + twilight);
            });
        }
        WhiteWidowGameTest.at(context, MIDNIGHT, () -> {
            tick(world, abs);
            context.assertTrue(lit(context, at), "the lamp did not light again at night");
            context.setBlock(at.north(), Blocks.AIR);
            context.assertTrue(!lit(context, at) && !context.getBlockState(at).getValue(UvcLampBlock.POWERED),
                    "the lamp stays lit with its signal gone: " + context.getBlockState(at));
        });
        context.succeed();
    }

    /** No light, no roof, no Grow Lamp, drawn full-bright while lit, and it needs a ceiling to hang from. */
    public static void aUvcLampGivesNoLightAndIsNoRoof(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockState dark = ModBlocks.UVC_LAMP.defaultBlockState();
        BlockState lit = dark.setValue(UvcLampBlock.POWERED, true).setValue(UvcLampBlock.LIT, true);
        context.assertTrue(dark.getLightEmission() == 0 && lit.getLightEmission() == 0,
                "the lamp gives light " + dark.getLightEmission() + " dark and " + lit.getLightEmission() + " lit");
        context.assertTrue(GrowLight.of(lit) == GrowLight.NATURAL && GrowLight.ofFixture(dark) == GrowLight.NATURAL,
                "a plant would read the lamp as a light: " + GrowLight.of(lit));
        context.assertTrue(!lit.is(ModTags.Blocks.GROW_LAMPS), "the lamp is a grow lamp");
        context.assertTrue(!lit.is(BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP_NO_LEAVES) && !lit.isSolidRender(),
                "the lamp is a roof or opaque");
        // Full-bright is the tube's alone: the lit model's tube carries light_emission, its hood and chains do not, and the
        // block as a whole is never drawn full-bright (which would flatten the hood's shading as well).
        context.assertTrue(!lit.emissiveRendering() && !dark.emissiveRendering(),
                "the whole lamp is drawn full-bright: " + dark.emissiveRendering() + " dark and " + lit.emissiveRendering() + " lit");
        glowOf(context, "uvc_lamp_on", 15);
        glowOf(context, "uvc_lamp", 0);
        BlockPos at = new BlockPos(2, 4, 2);
        BlockPos abs = context.absolutePos(at);
        context.assertTrue(!dark.canSurvive(world, abs), "the lamp hangs from nothing");
        context.setBlock(at.above(), Blocks.STONE);
        context.assertTrue(dark.canSurvive(world, abs), "the lamp does not hang from a stone ceiling");
        context.succeed();
    }

    /**
     * A lit lamp over a plant in the dark clears it: any age, ripe included, to clean, one random tick in sixteen;
     * a sulfured plant stays sulfured; by day nothing; a lit Grow Lamp over the plant, or a light touching it, undoes
     * it, and a dark Grow Lamp does not.
     */
    public static void aLitUvcLampClearsMildewInTheDark(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos root = context.absolutePos(ROOT);
        BlockPos lamp = ROOT.above(3);

        WhiteWidowGameTest.at(context, MIDNIGHT, () -> {
            for (int age : new int[]{2, 4, 7}) {
                sow(context, ROOT, age, Mildew.MILDEWED);
                hang(context, lamp);
                context.assertTrue(lit(context, lamp), "the lamp is dark at midnight");
                Mildew.randomTick(world, root, decided(0.999F, 1));
                expect(context, context.getBlockState(ROOT).getValue(Mildew.PROPERTY), Mildew.MILDEWED,
                        "an age " + age + " plant on a tick the lamp does not take");
                Mildew.randomTick(world, root, decided(0.999F, 0));
                expect(context, context.getBlockState(ROOT).getValue(Mildew.PROPERTY), Mildew.CLEAN,
                        "an age " + age + " plant on a tick the lamp takes");
                if (age >= GrowLight.FLOWERING_AGE) {
                    expect(context, context.getBlockState(ROOT.above()).getValue(Mildew.PROPERTY), Mildew.CLEAN,
                            "an age " + age + " plant's upper half");
                }
            }

            // Residue, not fungus.
            sow(context, ROOT, 4, Mildew.SULFURED);
            Mildew.randomTick(world, root, decided(0.0F, 0));
            expect(context, context.getBlockState(ROOT).getValue(Mildew.PROPERTY), Mildew.SULFURED, "a sulfured plant under the lamp");

            // A lit light over the plant spoils the dark: a lit Grow Lamp, a lamp at 15 touching it; a dark Grow Lamp does not.
            sow(context, ROOT, 4, Mildew.MILDEWED);
            BlockPos grow = ROOT.west().above(2);
            context.setBlock(grow.above(), Blocks.STONE);
            context.setBlock(grow, ModBlocks.GROW_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, false));
            Mildew.randomTick(world, root, decided(0.0F, 0));
            expect(context, context.getBlockState(ROOT).getValue(Mildew.PROPERTY), Mildew.CLEAN, "a plant under a dark Grow Lamp");
            sow(context, ROOT, 4, Mildew.MILDEWED);
            context.setBlock(grow, ModBlocks.GROW_LAMP.defaultBlockState().setValue(BlockStateProperties.LIT, true));
            Mildew.randomTick(world, root, decided(0.0F, 0));
            expect(context, context.getBlockState(ROOT).getValue(Mildew.PROPERTY), Mildew.MILDEWED, "a plant under a lit Grow Lamp");
            context.setBlock(grow, Blocks.AIR);
            context.setBlock(ROOT.west(), Blocks.GLOWSTONE);
            Mildew.randomTick(world, root, decided(0.0F, 0));
            expect(context, context.getBlockState(ROOT).getValue(Mildew.PROPERTY), Mildew.MILDEWED, "a plant beside glowstone");
            context.setBlock(ROOT.west(), Blocks.AIR);
        });

        // Through the twilight the tube may stay lit, but the fungus mends UV damage with a brightening sky's blue light:
        // the lamp does nothing while the plant is in twilight, at dusk or at dawn.
        for (long twilight : twilights(context, root)) {
            WhiteWidowGameTest.at(context, twilight, () -> {
                sow(context, ROOT, 4, Mildew.MILDEWED);
                tick(world, context.absolutePos(lamp));
                context.assertTrue(lit(context, lamp), "the lamp went out in the twilight at " + twilight);
                context.assertTrue(!UvcLampBlock.protects(world, root, 2), "a lit lamp covers a plant in the twilight at " + twilight);
                context.assertTrue(UvcLampBlock.factorOver(world, root, context.getBlockState(ROOT)) == 1.0F,
                        "a lit lamp halves the odds in the twilight at " + twilight);
                Mildew.randomTick(world, root, decided(0.0F, 0));
                expect(context, context.getBlockState(ROOT).getValue(Mildew.PROPERTY), Mildew.MILDEWED,
                        "a plant under a lit lamp in the twilight at " + twilight);
            });
        }

        // By day the same lamp does nothing.
        WhiteWidowGameTest.at(context, NOON, () -> {
            sow(context, ROOT, 4, Mildew.MILDEWED);
            tick(world, context.absolutePos(lamp));
            context.assertTrue(!lit(context, lamp), "the lamp is lit at noon");
            Mildew.randomTick(world, root, decided(0.0F, 0));
            expect(context, context.getBlockState(ROOT).getValue(Mildew.PROPERTY), Mildew.MILDEWED, "a plant under a lamp at noon");
        });
        context.succeed();
    }

    /** Five by five across, three blocks under it, an opaque block stops it, and two lamps do no more than one. */
    public static void aUvcLampReachesFiveByFiveAndThreeBlocksDown(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos lamp = new BlockPos(4, 5, 3);
        WhiteWidowGameTest.at(context, MIDNIGHT, () -> {
            hang(context, lamp);
            context.assertTrue(lit(context, lamp), "the lamp is dark at midnight");
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos root = new BlockPos(4 + dx, 2, 3 + dz);
                    if (root.getX() < 0 || root.getX() > 7 || root.getZ() < 0 || root.getZ() > 7) {
                        continue;
                    }
                    boolean covered = Math.abs(dx) <= 2 && Math.abs(dz) <= 2;
                    context.assertTrue(UvcLampBlock.protects(world, context.absolutePos(root), 2) == covered,
                            "a plant " + dx + " across and " + dz + " along from the lamp is "
                                    + (covered ? "not covered" : "covered"));
                }
            }
            // The column: a standing plant's top is two blocks tall (three for a sativa), the lamp 0 to 2 blocks over it.
            for (int y = 0; y <= 4; y++) {
                BlockPos root = new BlockPos(4, y, 3);
                boolean covered = y >= 1 && y <= 3;
                context.assertTrue(UvcLampBlock.protects(world, context.absolutePos(root), 2) == covered,
                        "a plant two blocks tall rooted " + (5 - y) + " under the lamp is " + (covered ? "not covered" : "covered"));
            }
            context.assertTrue(UvcLampBlock.protects(world, context.absolutePos(new BlockPos(4, 0, 3)), 3),
                    "a plant three blocks tall, its top just under the lamp's column, is not covered");
            // An opaque block between them stops it.
            context.setBlock(new BlockPos(4, 4, 3), Blocks.STONE);
            context.assertTrue(!UvcLampBlock.protects(world, context.absolutePos(new BlockPos(4, 2, 3)), 2),
                    "a plant under a stone block under the lamp is covered");
            context.setBlock(new BlockPos(4, 4, 3), Blocks.AIR);
            // A dark lamp covers nothing.
            context.setBlock(lamp.north(), Blocks.AIR);
            context.assertTrue(!UvcLampBlock.protects(world, context.absolutePos(new BlockPos(4, 2, 3)), 2),
                    "a plant under a lamp with no signal is covered");
        });
        context.succeed();
    }

    /** Half the onset and half the spread to a plant under it, and two lamps do no more than one. */
    public static void aUvcLampHalvesMildewsOnsetAndSpread(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos sick = ROOT;
        BlockPos east = ROOT.east();
        BlockPos lamp = ROOT.above(2).north();
        BlockPos second = ROOT.above(2).south().east();
        fillBiome(context, ROOT, "minecraft:plains");
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                context.setBlock(new BlockPos(x, 5, z), Blocks.GLASS);
            }
        }
        WhiteWidowGameTest.at(context, MIDNIGHT, () -> {
            sow(context, sick, 2, Mildew.CLEAN);
            sow(context, east, 2, Mildew.CLEAN);
            BlockPos abs = context.absolutePos(east);
            BlockState plant = world.getBlockState(abs);
            float onset = Mildew.onsetFor(world, abs, plant);
            float spread = Mildew.spreadTo(world, abs, plant);
            context.assertTrue(Math.abs(onset - 1.0F / 500) < 1.0E-7F && Math.abs(spread - 0.03F) < 1.0E-7F,
                    "under glass with no lamp the odds are " + onset + " and " + spread + ", not 1 in 500 and 3 in 100");

            // The same plant under a lit lamp: a roll between the halves lands without it and misses with it.
            context.assertTrue(Mildew.afterStep(world, abs, plant, Mildew.CLEAN, decided(0.0015F, 0)) == Mildew.MILDEWED,
                    "an onset roll of 0.0015 misses a plant under glass with no lamp");
            hang(context, lamp);
            context.assertTrue(lit(context, lamp), "the lamp is dark at midnight");
            near(context, Mildew.onsetFor(world, abs, plant), onset / 2, "onset under a lit lamp");
            near(context, Mildew.spreadTo(world, abs, plant), spread / 2, "spread under a lit lamp");
            expect(context, Mildew.afterStep(world, abs, plant, Mildew.CLEAN, decided(0.0015F, 0)), Mildew.CLEAN,
                    "an onset roll of 0.0015 under a lit lamp");
            expect(context, Mildew.afterStep(world, abs, plant, Mildew.CLEAN, decided(0.0009F, 0)), Mildew.MILDEWED,
                    "an onset roll of 0.0009 under a lit lamp");

            // Spread: a mildewed neighbour's tick picks the plant under the lamp; 0.02 is under 0.03 and over 0.015.
            Mildew.write(world, context.absolutePos(sick), Mildew.MILDEWED);
            Mildew.randomTick(world, context.absolutePos(sick), decided(0.02F, EAST));
            expect(context, context.getBlockState(east).getValue(Mildew.PROPERTY), Mildew.CLEAN,
                    "a spread roll of 0.02 to a plant under a lit lamp");
            Mildew.randomTick(world, context.absolutePos(sick), decided(0.014F, EAST));
            expect(context, context.getBlockState(east).getValue(Mildew.PROPERTY), Mildew.MILDEWED,
                    "a spread roll of 0.014 to a plant under a lit lamp");

            // A second lamp over it: no more than one.
            hang(context, second);
            near(context, Mildew.onsetFor(world, abs, plant), onset / 2, "onset under two lit lamps");
        });
        // By day no lamp is lit, so the odds are the plain ones.
        WhiteWidowGameTest.at(context, NOON, () -> {
            tick(world, context.absolutePos(lamp));
            tick(world, context.absolutePos(second));
            BlockPos abs = context.absolutePos(east);
            near(context, Mildew.onsetFor(world, abs, world.getBlockState(abs)), 1.0F / 500, "onset under a lamp at noon");
        });
        context.succeed();
    }

    /** The Nether and the End have no day, so they are night at every hour for the lamp. */
    public static void theNetherAndTheEndAreDarkAtEveryHour(GameTestHelper context) {
        var server = context.getLevel().getServer();
        ServerLevel nether = server.getLevel(Level.NETHER);
        ServerLevel end = server.getLevel(Level.END);
        context.assertTrue(nether != null && end != null, "the server has no Nether or no End");
        BlockPos spot = context.absolutePos(ROOT);
        for (long time : new long[]{NOON, MIDNIGHT}) {
            WhiteWidowGameTest.at(context, time, () -> {
                context.assertTrue(UvcLampBlock.noDay(nether) && UvcLampBlock.dark(nether, spot),
                        "the Nether is not dark at " + time);
                context.assertTrue(UvcLampBlock.noDay(end) && UvcLampBlock.dark(end, spot), "the End is not dark at " + time);
                context.assertTrue(!UvcLampBlock.noDay(context.getLevel()), "the Overworld has no day");
                context.assertTrue(UvcLampBlock.dark(context.getLevel(), spot) == (time == MIDNIGHT),
                        "the Overworld is " + (time == MIDNIGHT ? "not " : "") + "dark at " + time);
            });
        }
        context.succeed();
    }

    // ----- helpers -----

    /** A lamp hung at {@code at} under a stone ceiling with a redstone block beside it: the signal that runs it. */
    private static void hang(GameTestHelper context, BlockPos at) {
        context.setBlock(at.above(), Blocks.STONE);
        context.setBlock(at.north(), Blocks.AIR);
        context.setBlock(at, ModBlocks.UVC_LAMP);
        context.setBlock(at.north(), Blocks.REDSTONE_BLOCK);
    }

    /** That the lit lamp's model gives its tube {@code light_emission} {@code level} and its hood and chains none. */
    private static void glowOf(GameTestHelper context, String model, int level) {
        java.nio.file.Path file = Sweep.modFiles().get("assets/hempdustry/models/block/" + model + ".json");
        context.assertTrue(file != null, "the model " + model + " is not there");
        int glowing = 0;
        for (var element : Sweep.json(file).getAsJsonObject().getAsJsonArray("elements")) {
            var faces = element.getAsJsonObject().getAsJsonObject("faces");
            boolean tube = faces.entrySet().stream()
                    .allMatch(face -> face.getValue().getAsJsonObject().get("texture").getAsString().equals("#tube"));
            int emission = element.getAsJsonObject().has("light_emission") ? element.getAsJsonObject().get("light_emission").getAsInt() : 0;
            context.assertTrue(emission == (tube ? level : 0),
                    model + ": " + (tube ? "the tube" : "an element that is not the tube") + " has light_emission " + emission);
            glowing += emission > 0 ? 1 : 0;
        }
        context.assertTrue(glowing == (level > 0 ? 1 : 0), model + " has " + glowing + " glowing elements");
    }

    private static boolean lit(GameTestHelper context, BlockPos at) {
        return UvcLampBlock.lit(context.getBlockState(at));
    }

    /** A scheduled tick of the lamp at {@code abs}, as the world would run it a second after. */
    private static void tick(ServerLevel world, BlockPos abs) {
        world.getBlockState(abs).tick(world, abs, world.getRandom());
    }

    /** Some hours of the day in the twilight at {@code abs}, either side of dusk and of dawn, by what the mod reads. */
    private static long[] twilights(GameTestHelper context, BlockPos abs) {
        java.util.List<Long> found = new java.util.ArrayList<>();
        for (long t = 11000; t <= 14500; t += 25) {
            long time = t;
            WhiteWidowGameTest.at(context, time, () -> {
                if (PlantStress.hours(context.getLevel(), abs) == PlantStress.Hours.TWILIGHT) {
                    found.add(time);
                }
            });
        }
        for (long t = 22000; t <= 23999; t += 25) {
            long time = t;
            WhiteWidowGameTest.at(context, time, () -> {
                if (PlantStress.hours(context.getLevel(), abs) == PlantStress.Hours.TWILIGHT) {
                    found.add(time);
                }
            });
        }
        context.assertTrue(found.size() >= 2, "no twilight found in the day");
        return found.stream().mapToLong(Long::longValue).toArray();
    }

    /** A plant of Indica at {@code age} on moist farmland at {@code pos}, its segments grown in and carrying {@code mildew}. */
    private static void sow(GameTestHelper context, BlockPos pos, int age, Mildew mildew) {
        for (int y = 2; y >= 0; y--) {
            if (context.getBlockState(pos.above(y)).is(ModBlocks.INDICA_CROP)) {
                context.setBlock(pos.above(y), Blocks.AIR);
            }
        }
        context.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE));
        context.setBlock(pos, Defoliation.unworked(ModBlocks.INDICA_CROP.defaultBlockState()).setValue(BlockStateProperties.AGE_7, age));
        Genes.neutral(context, pos);
        ServerLevel world = context.getLevel();
        BlockPos at = context.absolutePos(pos);
        world.getBlockState(at).randomTick(world, at, MISS);
        Mildew.write(world, at, mildew);
        context.assertTrue(context.getBlockState(pos).getValue(Mildew.PROPERTY) == mildew
                        && context.getBlockState(pos).getValue(BlockStateProperties.AGE_7) == age,
                "the plant at " + pos + " did not come up as sown");
    }

    /** Sets {@code biome} over the 9-block cube round {@code at}, as {@code /fillbiome} does. */
    private static void fillBiome(GameTestHelper context, BlockPos at, String biome) {
        BlockPos a = context.absolutePos(at).offset(-4, -4, -4);
        BlockPos b = context.absolutePos(at).offset(4, 4, 4);
        var server = context.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "fillbiome " + a.getX() + " " + a.getY() + " " + a.getZ() + " " + b.getX() + " " + b.getY() + " " + b.getZ() + " " + biome);
    }

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
        context.assertTrue(Math.abs(actual - expected) <= 1.0E-7F, what + ": odds " + actual + ", expected " + expected);
    }
}
