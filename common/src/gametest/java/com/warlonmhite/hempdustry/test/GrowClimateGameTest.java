package com.warlonmhite.hempdustry.test;

import com.warlonmhite.hempdustry.balance.GrowClimate;
import com.warlonmhite.hempdustry.block.ModBlocks;
import com.warlonmhite.hempdustry.block.custom.Climate;
import com.warlonmhite.hempdustry.block.custom.Defoliation;
import com.warlonmhite.hempdustry.block.custom.GrowFanBlock;
import com.warlonmhite.hempdustry.block.custom.IndicaCropBlock;
import com.warlonmhite.hempdustry.block.custom.Mildew;
import com.warlonmhite.hempdustry.block.entity.custom.PlantRecordBlockEntity;
import com.warlonmhite.hempdustry.item.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * The grow climate's gear: the Grow Fan, which moves a plant's air a step towards the open and thickens the
 * stalk of a plant that grew in its wind. Reach is asked outright ({@link Climate#ventilated}); what it does to
 * the odds through {@link Mildew#onsetFor}, under glass where no light has to settle.
 */
public final class GrowClimateGameTest {
    /** The plant stands one block up, so a fan can stand two below its root and still be inside the test. */
    private static final BlockPos SOIL = new BlockPos(1, 2, 1);
    private static final BlockPos CROP = SOIL.above();
    private static final BlockPos ROOF = CROP.above(3);
    /** A root for the fan's reach, room enough around it for the lane's edges and a block past each. */
    private static final BlockPos PLANT = new BlockPos(3, 3, 3);
    private static final float FIELD = 1.0F / 1500;
    private static final RandomSource MISS = decided(0.999F);

    /**
     * A Grow Fan runs on a signal into it, as the Grow Lamp does: a redstone block beside it switches it on, and
     * taking the block away switches it off. It hangs from what it is fixed to, and falls with it.
     */
    public static void aGrowFanRunsOnRedstone(GameTestHelper context) {
        BlockPos fan = new BlockPos(3, 2, 3);
        context.setBlock(fan.below(), Blocks.STONE);
        context.setBlock(fan, ModBlocks.GROW_FAN.defaultBlockState());
        context.assertTrue(!context.getBlockState(fan).getValue(GrowFanBlock.POWERED), "a fan with no signal runs");
        context.setBlock(fan.east(), Blocks.REDSTONE_BLOCK);
        context.assertTrue(context.getBlockState(fan).getValue(GrowFanBlock.POWERED), "a fan beside a redstone block is still");
        context.setBlock(fan.east(), Blocks.AIR);
        context.assertTrue(!context.getBlockState(fan).getValue(GrowFanBlock.POWERED), "a fan whose signal went still runs");

        // On a wall, facing north: fixed to the block south of it, and gone with it.
        BlockPos wall = new BlockPos(5, 3, 5);
        context.setBlock(wall.south(), Blocks.STONE);
        context.setBlock(wall, ModBlocks.GROW_FAN.defaultBlockState().setValue(GrowFanBlock.FACE, AttachFace.WALL));
        context.setBlock(wall.south(), Blocks.AIR);
        context.assertTrue(context.getBlockState(wall).isAir(), "a wall fan stayed up when its wall went");
        context.succeed();
    }

    /**
     * A running fan blows on the plants ahead of its grille, the way it faces: a lane 9 blocks long from the block in
     * front of it and 2 blocks to each side, on plants rooted from one block above the fan to three below it. Under
     * glass a plant it blows on has the open's air. Behind it, beside it, facing another way, a block past any edge
     * or still, it does nothing; two fans do no more than one; and fixed to a wall or hung from a ceiling it blows
     * level the way it faces, as it does standing. Run in a room 16 blocks long, for the lane and a block past it.
     */
    public static void aGrowFanStirsTheAirInItsReach(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        GrowClimate.Fan reach = GrowClimate.of(world.registryAccess()).fan();
        context.assertTrue(reach.equals(new GrowClimate.Fan(9, 2, 3, 1)),
                "the fan's reach is " + reach + ", not the agreed lane 9 ahead and 2 to each side, 3 below and 1 above");
        fillBiome(context, CROP, "minecraft:plains");
        near(context, odds(context, ModBlocks.INDICA_CROP), 3 * FIELD, "a plant under glass, no fan");
        fan(context, CROP.east(9), Direction.WEST, AttachFace.FLOOR, true);
        near(context, odds(context, ModBlocks.INDICA_CROP), FIELD, "a plant under glass, a running fan 9 blocks off blowing on it");
        // Two fans: still one step, never two.
        fan(context, CROP.south(3), Direction.NORTH, AttachFace.FLOOR, true);
        near(context, odds(context, ModBlocks.INDICA_CROP), FIELD, "a plant under glass with two running fans blowing on it");
        clearFan(context, CROP.east(9));
        clearFan(context, CROP.south(3));

        BlockPos root = context.absolutePos(PLANT);
        context.assertTrue(!Climate.ventilated(world, root), "a plant with no fan near it reads ventilated");
        // Every edge of the lane, from a fan east of the plant blowing west.
        for (BlockPos at : List.of(PLANT.east(), PLANT.east(9), PLANT.east(5).north(2), PLANT.east(5).south(2),
                PLANT.east(2).below(), PLANT.east(2).above(3))) {
            reaches(context, root, at, Direction.WEST, AttachFace.FLOOR, true);
        }
        for (BlockPos at : List.of(PLANT.east(10), PLANT.east(5).north(3), PLANT.east(5).south(3),
                PLANT.east(2).below(2), PLANT.east(2).above(4))) {
            reaches(context, root, at, Direction.WEST, AttachFace.FLOOR, false);
        }
        // Blowing away from the plant, across its line, past it at its side, or still.
        reaches(context, root, PLANT.east(2), Direction.EAST, AttachFace.FLOOR, false);
        reaches(context, root, PLANT.east(2), Direction.NORTH, AttachFace.FLOOR, false);
        reaches(context, root, PLANT.north(), Direction.EAST, AttachFace.FLOOR, false);
        fan(context, PLANT.east(2), Direction.WEST, AttachFace.FLOOR, false);
        context.assertTrue(!Climate.ventilated(world, root), "a still fan blowing on the plant reaches it");
        clearFan(context, PLANT.east(2));
        // From the plant's three other sides, and fixed to a wall or hung from a ceiling, as standing.
        reaches(context, root, PLANT.west(2), Direction.EAST, AttachFace.FLOOR, true);
        reaches(context, root, PLANT.north(3), Direction.SOUTH, AttachFace.FLOOR, true);
        reaches(context, root, PLANT.south(3), Direction.NORTH, AttachFace.FLOOR, true);
        reaches(context, root, PLANT.east(4), Direction.WEST, AttachFace.WALL, true);
        reaches(context, root, PLANT.east(4).above(2), Direction.WEST, AttachFace.CEILING, true);
        reaches(context, root, PLANT.east(4).above(2), Direction.EAST, AttachFace.CEILING, false);
        context.succeed();
    }

    /** A running fan at {@code at}, fixed as {@code face} and blowing {@code facing}, reaches the plant rooted at {@code root} or not. */
    private static void reaches(GameTestHelper context, BlockPos root, BlockPos at, Direction facing, AttachFace face, boolean expected) {
        fan(context, at, facing, face, true);
        context.assertTrue(Climate.ventilated(context.getLevel(), root) == expected, "a running fan " + face + " at "
                + at.subtract(PLANT) + " from the root, blowing " + facing + (expected ? ", misses it" : ", reaches it"));
        clearFan(context, at);
    }

    /**
     * A plant that grew in a running fan's wind at every step drops one stem more when it is harvested ripe; one
     * whose fan was off for a step, or that ripened before the fan came on, does not.
     */
    public static void aFanThickensTheStalk(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos root = context.absolutePos(CROP);
        fan(context, CROP.east(2), Direction.WEST, AttachFace.FLOOR, true);
        sow(context, ModBlocks.INDICA_CROP, 0);
        while (world.getBlockState(root).getValue(BlockStateProperties.AGE_7) < IndicaCropBlock.MAX_AGE) {
            grow(context);
        }
        context.assertTrue(record(context).ventilated(), "a plant grown all its life in a fan's wind reads unventilated");
        int ventilated = stems(context);

        sow(context, ModBlocks.INDICA_CROP, 0);
        grow(context);
        context.setBlock(CROP.east(2).below(), Blocks.STONE);
        grow(context);
        context.setBlock(CROP.east(2).below(), Blocks.REDSTONE_BLOCK);
        while (world.getBlockState(root).getValue(BlockStateProperties.AGE_7) < IndicaCropBlock.MAX_AGE) {
            grow(context);
        }
        context.assertTrue(!record(context).ventilated(), "a plant whose fan stopped for a step reads ventilated");
        int once = stems(context);
        context.assertTrue(ventilated == once + 1, "a ventilated plant dropped " + ventilated + " stems and one whose fan "
                + "stopped " + once + "; the fan's wind is one stem");

        // The record is what pays, never the fan standing there at harvest.
        sow(context, ModBlocks.INDICA_CROP, IndicaCropBlock.MAX_AGE);
        context.assertTrue(stems(context) == once, "a ripe plant set beside a running fan dropped " + stems(context)
                + " stems, not " + once);
        context.succeed();
    }

    // ----- helpers -----

    /**
     * A Grow Fan at {@code at}, fixed as {@code face} and blowing {@code facing}, on the block it hangs from: a
     * redstone block, so it runs, if {@code running}, else stone.
     */
    private static void fan(GameTestHelper context, BlockPos at, Direction facing, AttachFace face, boolean running) {
        BlockState state = ModBlocks.GROW_FAN.defaultBlockState().setValue(GrowFanBlock.FACING, facing)
                .setValue(GrowFanBlock.FACE, face);
        context.setBlock(fixedTo(at, state), Blocks.STONE);
        context.setBlock(at, state);
        if (running) {
            // Set after the fan, so it hears the signal come on as it would in a world.
            context.setBlock(fixedTo(at, state), Blocks.REDSTONE_BLOCK);
        }
        context.assertTrue(context.getBlockState(at).is(ModBlocks.GROW_FAN)
                        && context.getBlockState(at).getValue(GrowFanBlock.POWERED) == running,
                "test setup: the fan at " + at + " did not come up " + (running ? "running" : "still"));
    }

    /** Takes away the fan at {@code at} and the block it hangs from. */
    private static void clearFan(GameTestHelper context, BlockPos at) {
        BlockPos support = fixedTo(at, context.getBlockState(at));
        context.setBlock(at, Blocks.AIR);
        context.setBlock(support, Blocks.AIR);
    }

    /** The block a fan standing as {@code state} at {@code at} is fixed to: under it, over it, or the wall behind it. */
    private static BlockPos fixedTo(BlockPos at, BlockState state) {
        return switch (state.getValue(GrowFanBlock.FACE)) {
            case FLOOR -> at.below();
            case CEILING -> at.above();
            case WALL -> at.relative(state.getValue(GrowFanBlock.FACING).getOpposite());
        };
    }

    /** The onset odds of a fresh neutral plant of {@code crop} at age 2 under the glass roof. */
    private static float odds(GameTestHelper context, Block crop) {
        context.setBlock(ROOF, Blocks.GLASS);
        sow(context, crop, 2);
        BlockPos at = context.absolutePos(CROP);
        return Mildew.onsetFor(context.getLevel(), at, context.getLevel().getBlockState(at));
    }

    /** A neutral plant of {@code crop} at {@code age} on moist farmland at {@link #CROP}, its segments grown in, clean. */
    private static void sow(GameTestHelper context, Block crop, int age) {
        for (int y = 2; y >= 0; y--) {
            if (context.getBlockState(CROP.above(y)).getBlock() instanceof CropBlock) {
                context.setBlock(CROP.above(y), Blocks.AIR);
            }
        }
        context.setBlock(SOIL, Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE));
        context.setBlock(CROP, Defoliation.unworked(crop.defaultBlockState()).setValue(BlockStateProperties.AGE_7, age));
        Genes.neutral(context, CROP);
        ServerLevel world = context.getLevel();
        BlockPos at = context.absolutePos(CROP);
        world.getBlockState(at).randomTick(world, at, MISS);
        Mildew.write(world, at, Mildew.CLEAN);
    }

    /** One bone meal step of the plant at {@link #CROP}, on a roll that never catches mildew. */
    private static void grow(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos at = context.absolutePos(CROP);
        int before = world.getBlockState(at).getValue(BlockStateProperties.AGE_7);
        ((CropBlock) world.getBlockState(at).getBlock()).performBonemeal(world, MISS, at, world.getBlockState(at),
                BonemealSource.INTERACTION);
        context.assertTrue(world.getBlockState(at).getValue(BlockStateProperties.AGE_7) > before,
                "test setup: a bone meal step did not grow the plant");
    }

    private static PlantRecordBlockEntity record(GameTestHelper context) {
        return (PlantRecordBlockEntity) context.getLevel().getBlockEntity(context.absolutePos(CROP));
    }

    /**
     * The stems the ripe plant at {@link #CROP} drops from its root, broken with an empty hand, on one fixed seed:
     * the stems' own bonus is a roll, and the same seed draws it the same for every plant, so two plants differ
     * only by what their records say.
     */
    private static int stems(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos at = context.absolutePos(CROP);
        BlockState state = world.getBlockState(at);
        LootTable table = world.getServer().reloadableRegistries().getLootTable(state.getBlock().getLootTable().orElseThrow());
        LootParams params = new LootParams.Builder(world)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(at))
                .withParameter(LootContextParams.BLOCK_STATE, state)
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, world.getBlockEntity(at))
                .create(LootContextParamSets.BLOCK);
        return table.getRandomItems(params, STEM_SEED).stream()
                .filter(stack -> stack.is(ModItems.HEMP_STEM)).mapToInt(ItemStack::getCount).sum();
    }

    private static final long STEM_SEED = 0x57E3L;

    private static void near(GameTestHelper context, float actual, float expected, String what) {
        context.assertTrue(Math.abs(actual - expected) <= 1.0E-6F * Math.max(1.0F, expected * 1000.0F),
                what + ": odds " + actual + ", expected " + expected);
    }

    /** Sets {@code biome} over the 9-block cube round {@code at}, as {@code /fillbiome} does. */
    private static void fillBiome(GameTestHelper context, BlockPos at, String biome) {
        BlockPos a = context.absolutePos(at).offset(-4, -4, -4);
        BlockPos b = context.absolutePos(at).offset(4, 4, 4);
        var server = context.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "fillbiome " + a.getX() + " " + a.getY() + " " + a.getZ() + " " + b.getX() + " " + b.getY() + " " + b.getZ() + " " + biome);
    }

    private static RandomSource decided(float value) {
        return new SingleThreadedRandomSource(0L) {
            @Override
            public float nextFloat() {
                return value;
            }

            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
    }

    private GrowClimateGameTest() {
    }
}
