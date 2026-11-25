package com.warlonmhite.hempdustry.test;

import com.warlonmhite.hempdustry.balance.GrowClimate;
import com.warlonmhite.hempdustry.block.ModBlocks;
import com.warlonmhite.hempdustry.block.ModDispenserBehaviors;
import com.warlonmhite.hempdustry.block.custom.Climate;
import com.warlonmhite.hempdustry.block.custom.Climate.Humidity;
import com.warlonmhite.hempdustry.block.custom.Defoliation;
import com.warlonmhite.hempdustry.block.custom.DehumidifierBlock;
import com.warlonmhite.hempdustry.block.custom.FanWind;
import com.warlonmhite.hempdustry.block.custom.HydroTrayBlock;
import com.warlonmhite.hempdustry.block.custom.HygrometerBlock;
import com.warlonmhite.hempdustry.block.custom.GrowFanBlock;
import com.warlonmhite.hempdustry.block.custom.GrowClimateLine;
import com.warlonmhite.hempdustry.block.custom.SativaCropBlock;
import com.warlonmhite.hempdustry.block.custom.TriplePlantSegment;
import net.minecraft.core.registries.BuiltInRegistries;
import com.warlonmhite.hempdustry.block.custom.IndicaCropBlock;
import com.warlonmhite.hempdustry.block.custom.Mildew;
import com.warlonmhite.hempdustry.block.entity.custom.PlantRecordBlockEntity;
import com.warlonmhite.hempdustry.item.ModItems;
import com.warlonmhite.hempdustry.util.ModTags;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.WallSide;
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
     * level the way it faces, as it does standing. For a plant hanging from a ceiling the window is turned over: from
     * one block under the fan to three over it. Run in a room 16 blocks long, for the lane and a block past it.
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
        // The palettes are asked before a block is read: no fan in the box, no closer look; one anywhere in it, a closer
        // look; and a box in chunks nobody loaded is not looked into, nor loaded.
        java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> isFan =
                state -> state.getBlock() instanceof GrowFanBlock;
        // High over the test, where its section has held nothing but air and the stone set there: a palette keeps every
        // state a section has held, so a box where fans have stood still says "maybe" after they are gone.
        BlockPos sky = new BlockPos(root.getX(), world.getMaxY() - 24, root.getZ());
        world.setBlock(sky, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        context.assertFalse(Climate.mayHold(world, sky.offset(-9, -1, -9), sky.offset(9, 3, 9), isFan),
                "a box with stone and no fan in it may hold a fan, by its palettes");
        world.setBlock(sky.above(), ModBlocks.GROW_FAN.defaultBlockState(), Block.UPDATE_CLIENTS);
        context.assertTrue(Climate.mayHold(world, sky.offset(-9, -1, -9), sky.offset(9, 3, 9), isFan),
                "a box with a fan in it holds none, by its palettes");
        world.setBlock(sky.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        world.setBlock(sky, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        BlockPos far = root.offset(30_000 * 16, 0, 0);
        context.assertFalse(Climate.mayHold(world, far, far.offset(16, 0, 16), isFan) || world.hasChunk(far.getX() >> 4,
                far.getZ() >> 4), "a box in an unloaded chunk was looked into, or loaded");
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
        // A plant that hangs (Crimson Weed) has its root on top: the window is turned over, so a fan on the floor
        // under it reaches it as one over a standing plant does, and one a block over its root still does.
        BlockPos hanging = PLANT.above(2);
        context.setBlock(hanging.above(), Blocks.NETHER_WART_BLOCK);
        context.setBlock(hanging, ModBlocks.CRIMSON_CROP);
        BlockPos hangingRoot = context.absolutePos(hanging);
        for (BlockPos at : List.of(hanging.east(2).below(3), hanging.east(2).above())) {
            reaches(context, hangingRoot, at, Direction.WEST, AttachFace.FLOOR, true);
        }
        for (BlockPos at : List.of(hanging.east(2).below(4), hanging.east(2).above(2))) {
            reaches(context, hangingRoot, at, Direction.WEST, AttachFace.FLOOR, false);
        }
        context.setBlock(hanging, Blocks.AIR);
        context.setBlock(hanging.above(), Blocks.AIR);
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
     * A running fan's puffs draw the lane its wind reaches, and nothing past it. In every placement, facing every
     * way, a puff leaves the front of the housing and is gone at the lane's far edge, inside its width. And in a
     * room, standing, on a wall and hung, blowing west and east: every block a puff passes through past the fan's
     * own is one a plant rooted there is ventilated in ({@link Climate#ventilated}), and every block of the lane at
     * the fan's height has a puff pass through it. Every puff the game can pick is walked: the three numbers that
     * place it at their ends and across their range.
     */
    public static void aFansPuffsDrawItsLane(GameTestHelper context) {
        GrowClimate.Fan lane = GrowClimate.of(context.getLevel().registryAccess()).fan();
        for (AttachFace face : AttachFace.values()) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockState state = ModBlocks.GROW_FAN.defaultBlockState().setValue(GrowFanBlock.FACING, facing)
                        .setValue(GrowFanBlock.FACE, face).setValue(GrowFanBlock.POWERED, true);
                AABB housing = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).bounds();
                Vec3 ahead = facing.getUnitVec3();
                Vec3 across = facing.getClockWise().getUnitVec3();
                for (double to : new double[] {-1.0, 0.0, 1.0}) {
                    GrowFanBlock.Puff puff = GrowFanBlock.puff(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO, lane,
                            0.0, 0.0, to);
                    String where = "a fan " + face + " blowing " + facing + "'s puff to " + to;
                    context.assertTrue(housing.contains(puff.from().subtract(ahead.scale(0.01)))
                                    && !housing.contains(puff.from().add(ahead.scale(0.01))),
                            where + " leaves from " + puff.from() + ", not the front of its housing " + housing);
                    Vec3 end = puff.from().add(puff.path().offset(puff.path().reach()))
                            .subtract(Vec3.atCenterOf(BlockPos.ZERO));
                    double far = end.dot(ahead);
                    double side = end.dot(across);
                    context.assertTrue(far > lane.ahead() + 0.4 && far < lane.ahead() + 0.5,
                            where + " is gone " + far + " blocks ahead of the fan's middle, not at the lane's far edge, "
                                    + (lane.ahead() + 0.5));
                    context.assertTrue(Math.abs(side) < lane.side() + 0.5,
                            where + " ends " + side + " blocks to the side, outside the lane's " + (2 * lane.side() + 1));
                }
            }
        }

        BlockPos west = new BlockPos(12, 3, 3);
        BlockPos east = new BlockPos(2, 3, 3);
        for (AttachFace face : AttachFace.values()) {
            puffsDrawTheLane(context, lane, west, Direction.WEST, face);
            puffsDrawTheLane(context, lane, east, Direction.EAST, face);
        }
        context.succeed();
    }

    /** The puffs of a running fan at {@code at}, fixed as {@code face} and blowing {@code facing}, draw its lane. */
    private static void puffsDrawTheLane(GameTestHelper context, GrowClimate.Fan lane, BlockPos at, Direction facing,
                                         AttachFace face) {
        puffsDrawTheLane(context, lane, at, facing, face, Set.of());
    }

    /**
     * The puffs of a running fan at {@code at}, fixed as {@code face} and blowing {@code facing}, draw the lane its
     * wind reaches with stone set at the fan's height at each of {@code walls} (blocks ahead, blocks clockwise): every
     * block a puff passes through past the fan's own is one a plant is blown on in, and is in the wind by the rule
     * worked out here, apart from the game's — a block is, if nothing stands before it in its own line; and every such
     * block has a puff pass through it, except where the lane's first block would take a puff across a wall on its
     * way out to its side.
     */
    private static void puffsDrawTheLane(GameTestHelper context, GrowClimate.Fan lane, BlockPos at, Direction facing,
                                         AttachFace face, Set<List<Integer>> walls) {
        ServerLevel world = context.getLevel();
        fan(context, at, facing, face, true);
        BlockPos fan = context.absolutePos(at);
        Direction across = facing.getClockWise();
        for (List<Integer> wall : walls) {
            world.setBlock(fan.relative(facing, wall.get(0)).relative(across, wall.get(1)), Blocks.STONE.defaultBlockState(),
                    Block.UPDATE_CLIENTS);
        }
        BlockState state = world.getBlockState(fan);
        Map<BlockPos, Boolean> ventilated = new HashMap<>();
        Set<BlockPos> passed = new HashSet<>();
        double[] ends = {-1.0, 0.0, 1.0};
        for (double side : ends) {
            for (double up : ends) {
                for (int step = -24; step <= 24; step++) {
                    GrowFanBlock.Puff puff = GrowFanBlock.puff(state, world, fan, lane, side, up, step / 24.0);
                    for (double travelled = 0.0; travelled <= puff.path().reach(); travelled += 0.05) {
                        BlockPos in = BlockPos.containing(puff.from().add(puff.path().offset(travelled)));
                        if (in.equals(fan) || !passed.add(in)) {
                            continue;
                        }
                        boolean reached = ventilated.computeIfAbsent(in, pos -> Climate.ventilated(world, pos));
                        context.assertTrue(reached, "a fan " + face + " blowing " + facing + " puffs through "
                                + in.subtract(fan) + " from it, where its wind does not reach, with stone at " + walls
                                + "; the puff: side " + side + ", up " + up + ", to " + step + "/24, " + puff.path());
                    }
                }
            }
        }
        for (int ahead = 1; ahead <= lane.ahead(); ahead++) {
            for (int side = -lane.side(); side <= lane.side(); side++) {
                BlockPos block = fan.relative(facing, ahead).relative(across, side);
                boolean inWind = true;
                for (int before = 1; before <= ahead; before++) {
                    inWind &= !walls.contains(List.of(before, side));
                }
                boolean drawn = inWind;
                for (int crossed = 0; crossed != side; crossed += Integer.signum(side)) {
                    drawn &= !walls.contains(List.of(1, crossed));
                }
                context.assertTrue(passed.contains(block) == drawn, "a puff of a fan " + face + " blowing " + facing
                        + (drawn ? " never passes through " : " passes through ") + block.subtract(fan) + " from it, with "
                        + "stone at " + walls + " (blocks ahead, blocks clockwise)");
            }
        }
        for (List<Integer> wall : walls) {
            world.setBlock(fan.relative(facing, wall.get(0)).relative(across, wall.get(1)), Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_CLIENTS);
        }
        clearFan(context, at);
    }

    /**
     * Where a fan's wind stops, block by block (#424, #426–#429): a block stops it when its collision shape, seen
     * along the wind, covers three quarters of its face or more, unless it is in {@code #hempdustry:fan_passes_through}.
     * The record's list, each state read along the axis given: what is low, thin, small or open passes, what fills the
     * face stops it, and the tag's blocks pass although they fill it.
     */
    public static void theWindStopsAtThreeQuarters(GameTestHelper context) {
        Direction.Axis x = Direction.Axis.X;
        Direction.Axis z = Direction.Axis.Z;
        // Stops: full blocks, leaves (#427), a chest (#429), stairs side-on and end-on, a closed door and a standing
        // trapdoor across the wind, a pane window and a wall joined across it, and the blocks the record measured.
        stopsWind(context, Blocks.STONE.defaultBlockState(), x, true);
        stopsWind(context, Blocks.GLASS.defaultBlockState(), z, true);
        stopsWind(context, Blocks.OAK_LEAVES.defaultBlockState(), x, true);
        stopsWind(context, Blocks.CHEST.defaultBlockState(), x, true);
        stopsWind(context, Blocks.CHEST.defaultBlockState(), z, true);
        BlockState stairs = Blocks.STONE_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        stopsWind(context, stairs, x, true);
        stopsWind(context, stairs, z, true);
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        stopsWind(context, door, x, true);
        stopsWind(context, door, z, false);
        BlockState standing = Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                .setValue(BlockStateProperties.OPEN, true);
        stopsWind(context, standing, x, true);
        stopsWind(context, standing, z, false);
        BlockState window = Blocks.GLASS_PANE.defaultBlockState().setValue(BlockStateProperties.NORTH, true)
                .setValue(BlockStateProperties.SOUTH, true);
        stopsWind(context, window, x, true);
        stopsWind(context, window, z, false);
        stopsWind(context, Blocks.GLASS_PANE.defaultBlockState(), x, false);
        BlockState wall = Blocks.COBBLESTONE_WALL.defaultBlockState().setValue(BlockStateProperties.NORTH_WALL, WallSide.LOW)
                .setValue(BlockStateProperties.SOUTH_WALL, WallSide.LOW).setValue(BlockStateProperties.UP, false);
        stopsWind(context, wall, x, true);
        stopsWind(context, Blocks.COBBLESTONE_WALL.defaultBlockState(), x, false);
        for (Block block : List.of(Blocks.ENCHANTING_TABLE, Blocks.CAULDRON, Blocks.COMPOSTER, Blocks.DECORATED_POT,
                ModBlocks.DEHUMIDIFIER)) {
            stopsWind(context, block.defaultBlockState(), x, true);
        }
        stopsWind(context, Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 7), x, true);
        stopsWind(context, Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE), x, true);
        // Passes: low, flat, thin or small.
        stopsWind(context, Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 6), x, false);
        stopsWind(context, Blocks.OAK_SLAB.defaultBlockState(), x, false);
        stopsWind(context, Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP), z, false);
        stopsWind(context, Blocks.BED.pick(DyeColor.RED).defaultBlockState(), x, false);
        stopsWind(context, Blocks.CAMPFIRE.defaultBlockState(), x, false);
        stopsWind(context, Blocks.OAK_TRAPDOOR.defaultBlockState(), x, false);
        for (Block block : List.of(Blocks.LANTERN, Blocks.IRON_CHAIN, Blocks.END_ROD, Blocks.HOPPER, Blocks.SCAFFOLDING,
                Blocks.OAK_FENCE, Blocks.AIR, Blocks.WHEAT, Blocks.TORCH)) {
            stopsWind(context, block.defaultBlockState(), x, false);
            stopsWind(context, block.defaultBlockState(), z, false);
        }
        for (BlockState fan : ModBlocks.GROW_FAN.getStateDefinition().getPossibleStates()) {
            stopsWind(context, fan, x, false);
            stopsWind(context, fan, z, false);
        }
        // The tag: shapes that fill the face, and let the wind through all the same.
        BlockState bars = Blocks.IRON_BARS.defaultBlockState().setValue(BlockStateProperties.NORTH, true)
                .setValue(BlockStateProperties.SOUTH, true);
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState().setValue(BlockStateProperties.NORTH, true)
                .setValue(BlockStateProperties.SOUTH, true);
        BlockState hempFence = ModBlocks.HEMP_PLANKS_FENCE.defaultBlockState().setValue(BlockStateProperties.NORTH, true)
                .setValue(BlockStateProperties.SOUTH, true);
        List<BlockState> open = new java.util.ArrayList<>(List.of(bars, fence, hempFence,
                Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST),
                ModBlocks.HEMP_PLANKS_FENCE_GATE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST),
                Blocks.MANGROVE_ROOTS.defaultBlockState()));
        Blocks.COPPER_GRATE.forEach(grate -> open.add(grate.defaultBlockState()));
        for (BlockState state : open) {
            float cover = FanWind.cover(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, state, x);
            context.assertTrue(cover >= FanWind.STOPS_AT, state + " covers only " + cover + " of its face, so it needs no "
                    + "place in #hempdustry:fan_passes_through to let the wind by");
            stopsWind(context, state, x, false);
        }
        context.succeed();
    }

    /** Whether {@code state} stops wind blowing along {@code axis}, as {@code expected}. */
    private static void stopsWind(GameTestHelper context, BlockState state, Direction.Axis axis, boolean expected) {
        boolean stops = FanWind.stops(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, state, axis);
        context.assertTrue(stops == expected, state + " seen along " + axis + " covers "
                + FanWind.cover(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, state, axis) + " of its face and "
                + (stops ? "stops" : "lets through") + " a fan's wind");
    }

    /**
     * A wall between a running fan and a plant stops the fan's wind: stone, leaves (#427), chests (#429) and a
     * glass-pane window across the wind (#426) leave the plant unventilated; iron bars, a joined fence, closed fence
     * gates, every copper grate and mangrove roots (#428), all in {@code #hempdustry:fan_passes_through}, let it
     * through, and so do a lone pane, panes edge-on, a bottom slab and a lantern, which cover too little. The tag is
     * read, not written into the rule (#430): stone put in it lets the wind through.
     */
    public static void aWallStopsAFansWind(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos root = context.absolutePos(PLANT);
        fan(context, PLANT.east(4), Direction.WEST, AttachFace.FLOOR, true);
        context.assertTrue(Climate.ventilated(world, root), "test setup: a running fan 4 blocks east misses the plant");
        Map<String, BlockState> stopping = new java.util.LinkedHashMap<>();
        stopping.put("stone", Blocks.STONE.defaultBlockState());
        stopping.put("leaves", Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true));
        stopping.put("chests", Blocks.CHEST.defaultBlockState());
        stopping.put("a glass-pane window", Blocks.GLASS_PANE.defaultBlockState());
        stopping.forEach((what, state) -> blowsThrough(context, root, wallAcross(), state, false, "a wall of " + what));
        Map<String, BlockState> open = new java.util.LinkedHashMap<>();
        open.put("iron bars", Blocks.IRON_BARS.defaultBlockState());
        open.put("oak fence", Blocks.OAK_FENCE.defaultBlockState());
        open.put("hemp planks fence", ModBlocks.HEMP_PLANKS_FENCE.defaultBlockState());
        open.put("closed oak fence gates", Blocks.OAK_FENCE_GATE.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        open.put("closed hemp planks fence gates", ModBlocks.HEMP_PLANKS_FENCE_GATE.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        open.put("mangrove roots", Blocks.MANGROVE_ROOTS.defaultBlockState());
        Blocks.COPPER_GRATE.forEach(grate -> open.put(grate.getDescriptionId(), grate.defaultBlockState()));
        open.forEach((what, state) -> blowsThrough(context, root, wallAcross(), state, true, "a wall of " + what));
        // In the plant's own line only: a lone pane, three panes joined along the wind, a slab, a lantern, a chest.
        blowsThrough(context, root, List.of(PLANT.east(2)), Blocks.GLASS_PANE.defaultBlockState(), true, "a lone pane");
        blowsThrough(context, root, List.of(PLANT.east(1), PLANT.east(2), PLANT.east(3)), Blocks.GLASS_PANE.defaultBlockState(),
                true, "panes joined along the wind");
        blowsThrough(context, root, List.of(PLANT.east(2)), Blocks.OAK_SLAB.defaultBlockState(), true, "a bottom slab");
        blowsThrough(context, root, List.of(PLANT.east(2)), Blocks.LANTERN.defaultBlockState(), true, "a lantern");
        blowsThrough(context, root, List.of(PLANT.east(2)), Blocks.CHEST.defaultBlockState(), false, "a chest");
        Tagged.within(Blocks.STONE, ModTags.Blocks.FAN_PASSES_THROUGH, () -> blowsThrough(context, root, wallAcross(),
                Blocks.STONE.defaultBlockState(), true, "a wall of stone in #hempdustry:fan_passes_through"));
        blowsThrough(context, root, wallAcross(), Blocks.STONE.defaultBlockState(), false, "a wall of stone, out of the tag again");
        clearFan(context, PLANT.east(4));
        context.succeed();
    }

    /** A wall across the whole lane, two blocks east of the plant: five blocks wide, a block under it to one over. */
    private static List<BlockPos> wallAcross() {
        List<BlockPos> wall = new java.util.ArrayList<>();
        for (int up = -1; up <= 1; up++) {
            for (int side = -2; side <= 2; side++) {
                wall.add(PLANT.east(2).offset(0, up, side));
            }
        }
        return wall;
    }

    /**
     * With {@code state} set at each of {@code blocks}, joined to each other as a player would have built them, the
     * plant rooted at {@code root} is blown on or not as {@code expected}; the blocks are taken away again after.
     */
    private static void blowsThrough(GameTestHelper context, BlockPos root, List<BlockPos> blocks, BlockState state,
                                     boolean expected, String what) {
        ServerLevel world = context.getLevel();
        for (BlockPos at : blocks) {
            world.setBlock(context.absolutePos(at), state, Block.UPDATE_CLIENTS);
        }
        if (blocks.size() > 1) {
            for (BlockPos at : blocks) {
                BlockPos absolute = context.absolutePos(at);
                world.setBlock(absolute, Block.updateFromNeighbourShapes(world.getBlockState(absolute), world, absolute),
                        Block.UPDATE_CLIENTS);
            }
        }
        context.assertTrue(Climate.ventilated(world, root) == expected, what + " between a running fan and a plant "
                + (expected ? "stops its wind" : "lets its wind through") + "; there: "
                + world.getBlockState(context.absolutePos(blocks.getFirst())));
        for (BlockPos at : blocks) {
            world.setBlock(context.absolutePos(at), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * A block in a fan's lane cuts only its own line (#425): the lane is straight lines along the wind, one block wide
     * and high, and a plant is in the one at its root's height and column. Stone in that line, between the plant and
     * the fan, keeps the wind off it; the plants in the lines beside it are still blown on, and stone a row up, behind
     * the plant, or in the fan's own column is not in its way. A fan hung over the plants blows on them along their
     * own row, not its own.
     */
    public static void aBlockCutsOnlyItsOwnLine(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos root = context.absolutePos(PLANT);
        fan(context, PLANT.east(4), Direction.WEST, AttachFace.FLOOR, true);
        context.setBlock(PLANT.east(2), Blocks.STONE);
        context.assertTrue(!Climate.ventilated(world, root), "stone between the fan and the plant, in its line, lets the wind by");
        context.assertTrue(Climate.ventilated(world, context.absolutePos(PLANT.north()))
                        && Climate.ventilated(world, context.absolutePos(PLANT.south())),
                "stone in one line of the lane keeps the wind off the plants in the lines beside it");
        context.setBlock(PLANT.east(2), Blocks.AIR);
        for (BlockPos elsewhere : List.of(PLANT.east(2).above(), PLANT.west(), PLANT.east(3).north())) {
            context.setBlock(elsewhere, Blocks.STONE);
            context.assertTrue(Climate.ventilated(world, root), "stone at " + elsewhere.subtract(PLANT)
                    + " from the plant, out of its line, keeps the fan's wind off it");
            context.setBlock(elsewhere, Blocks.AIR);
        }
        clearFan(context, PLANT.east(4));

        BlockPos hung = PLANT.east(4).above(2);
        fan(context, hung, Direction.WEST, AttachFace.CEILING, true);
        context.assertTrue(Climate.ventilated(world, root), "test setup: a running fan hung 2 over the plant's row misses it");
        context.setBlock(PLANT.east(2).above(2), Blocks.STONE);
        context.assertTrue(Climate.ventilated(world, root), "stone in a hung fan's own row keeps the wind off a plant two rows down");
        context.setBlock(PLANT.east(2).above(2), Blocks.AIR);
        context.setBlock(PLANT.east(4), Blocks.STONE);
        context.assertTrue(Climate.ventilated(world, root), "stone under a hung fan, in its own column, keeps its wind off the plant");
        context.setBlock(PLANT.east(4), Blocks.AIR);
        context.setBlock(PLANT.east(2), Blocks.STONE);
        context.assertTrue(!Climate.ventilated(world, root), "stone in the plant's row between it and a hung fan lets the wind by");
        context.setBlock(PLANT.east(2), Blocks.AIR);
        clearFan(context, hung);
        context.succeed();
    }

    /**
     * A fan's puffs stop where its wind stops: against a wall across the lane, before a block in one line only that
     * line's puffs, and a puff bound across the lane's first block is gone at once if a wall stands in its way there.
     * In every placement, blowing west (and east, past single blocks), every block a puff passes through is one the wind
     * reaches, and every block the wind reaches is drawn; a puff whose path runs on the edge between two lines is
     * stopped by the line it is drawn in, whichever way the lane runs.
     */
    public static void aFansPuffsStopWhereItsWindStops(GameTestHelper context) {
        GrowClimate.Fan lane = GrowClimate.of(context.getLevel().registryAccess()).fan();
        Set<List<Integer>> across = new HashSet<>();
        for (int side = -lane.side(); side <= lane.side(); side++) {
            across.add(List.of(4, side));
        }
        BlockPos at = new BlockPos(12, 3, 3);
        for (AttachFace face : AttachFace.values()) {
            puffsDrawTheLane(context, lane, at, Direction.WEST, face, across);
            puffsDrawTheLane(context, lane, at, Direction.WEST, face, Set.of(List.of(3, 2)));
            puffsDrawTheLane(context, lane, at, Direction.WEST, face, Set.of(List.of(1, 1)));
            // Single blocks either side, two lines out from each other: a puff on the edge between two lines runs in one.
            puffsDrawTheLane(context, lane, at, Direction.WEST, face, Set.of(List.of(3, 1), List.of(3, -2)));
            puffsDrawTheLane(context, lane, at.west(9), Direction.EAST, face, Set.of(List.of(3, 1), List.of(3, -2)));
        }
        context.succeed();
    }

    /**
     * Every block state the game has, read along both of a fan's axes: how much of its face it covers is a number
     * from 0 to 1, and reading it never throws. Read where a block stands, in the test's world, for the states whose
     * shape depends on it.
     */
    public static void theWindReadsEveryBlockState(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos pos = context.absolutePos(new BlockPos(1, 2, 1));
        Map<String, String> findings = new java.util.TreeMap<>();
        int seen = 0;
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            for (Direction.Axis axis : List.of(Direction.Axis.X, Direction.Axis.Z)) {
                try {
                    float cover = FanWind.cover(world, pos, state, axis);
                    FanWind.stops(world, pos, state, axis);
                    if (!(cover >= 0.0F && cover <= 1.0F)) {
                        findings.put(state + " " + axis, "covers " + cover);
                    }
                } catch (RuntimeException e) {
                    findings.put(state + " " + axis, e.toString());
                }
            }
            seen++;
        }
        Sweep.atLeast(context, "block states", seen, 20_000);
        Sweep.report(context, "every block state's cover of its face is a number from 0 to 1", findings, Map.of());
        context.succeed();
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

    /**
     * A running Dehumidifier dries every roofed plant in its reach two steps: a plant on a full Hydro Tray under
     * glass goes from damp to dry. Its reach is 9 x 9, two blocks up and down. It does nothing under the open sky,
     * nothing switched off, and nothing once its tank is full with nowhere to drip (a Hydro Tray under it is
     * nowhere); a cauldron under it starts it again.
     */
    public static void aDehumidifierDriesARoofedRoom(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        fillBiome(context, CROP, "minecraft:plains");
        context.setBlock(ROOF, Blocks.GLASS);
        near(context, onTray(context), 3 * 2 * FIELD, "a plant on a full tray under glass, no Dehumidifier");
        BlockPos drier = CROP.east(4);
        dehumidifier(context, drier, 0, true);
        near(context, onTray(context), 3 * 0.5F * FIELD, "a plant on a full tray under glass, a running Dehumidifier 4 blocks off");
        near(context, odds(context, ModBlocks.INDICA_CROP), 3 * 0.5F * FIELD,
                "a plant on farmland under glass, a running Dehumidifier 4 blocks off");

        // Switched off; full with nowhere to drip; then a cauldron under it.
        context.setBlock(drier.east(), Blocks.AIR);
        near(context, onTray(context), 3 * 2 * FIELD, "a plant on a full tray beside a Dehumidifier with no signal");
        dehumidifier(context, drier, DehumidifierBlock.MAX_LEVEL, true);
        near(context, onTray(context), 3 * 2 * FIELD, "a plant beside a running Dehumidifier with a full tank and nowhere to drip");
        context.setBlock(drier.below(), ModBlocks.HYDRO_TRAY.defaultBlockState());
        near(context, onTray(context), 3 * 2 * FIELD, "a plant beside a running Dehumidifier with a full tank over a dry Hydro Tray");
        context.setBlock(drier.below(), Blocks.CAULDRON);
        context.assertTrue(context.getBlockState(drier.below()).is(Blocks.WATER_CAULDRON)
                        && context.getBlockState(drier).getValue(DehumidifierBlock.LEVEL) == 0,
                "a cauldron set under a full Dehumidifier did not take its tank: the cauldron is "
                        + context.getBlockState(drier.below()) + ", the tank " + context.getBlockState(drier).getValue(DehumidifierBlock.LEVEL));
        near(context, onTray(context), 3 * 0.5F * FIELD, "a plant beside a running Dehumidifier with a cauldron under it");
        clearDehumidifier(context, drier);

        // Out of reach, a block further on or three blocks up.
        for (BlockPos far : List.of(CROP.east(5), CROP.east(3).above(3))) {
            dehumidifier(context, far, 0, true);
            near(context, onTray(context), 3 * 2 * FIELD, "a plant on a full tray with a running Dehumidifier at " + far.subtract(CROP));
            clearDehumidifier(context, far);
        }

        // Under the open sky: the weather is not a room.
        dehumidifier(context, drier, 0, true);
        context.setBlock(ROOF, Blocks.AIR);
        context.setBlock(new BlockPos(CROP.getX(), 8, CROP.getZ()), Blocks.AIR);
        near(context, onTray(context), 2 * FIELD, "a plant on a full tray under open sky beside a running Dehumidifier");
        context.succeed();
    }

    /**
     * The water a Dehumidifier takes out of the air is the plants' own: 64 growth steps of the roofed plants it
     * dries make a level of its tank, or drip straight into a cauldron with room under it, as a pointed dripstone's
     * water does. A Hydro Tray under it takes none. A plant under the open sky gives it nothing.
     */
    public static void aDehumidifierFillsFromThePlantsItDries(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        context.setBlock(ROOF, Blocks.GLASS);
        sow(context, ModBlocks.INDICA_CROP, 2);
        BlockPos root = context.absolutePos(CROP);
        BlockPos drier = CROP.east(2);
        dehumidifier(context, drier, 0, true);
        int perLevel = GrowClimate.of(world.registryAccess()).dehumidifier().stepsPerLevel();
        context.assertTrue(perLevel == 64, "a level of condensate is " + perLevel + " dried steps, not 64");
        for (int step = 1; step < perLevel; step++) {
            Climate.afterStep(world, root, 2, 3);
        }
        context.assertTrue(context.getBlockState(drier).getValue(DehumidifierBlock.LEVEL) == 0,
                "63 dried steps already filled a level");
        Climate.afterStep(world, root, 2, 3);
        context.assertTrue(context.getBlockState(drier).getValue(DehumidifierBlock.LEVEL) == 1,
                "64 dried steps filled no level of the tank");

        // A Hydro Tray under it takes nothing; a cauldron takes the level, and the next ones as they are made.
        context.setBlock(drier.below(), ModBlocks.HYDRO_TRAY.defaultBlockState().setValue(HydroTrayBlock.LEVEL, 1));
        context.assertTrue(context.getBlockState(drier.below()).getValue(HydroTrayBlock.LEVEL) == 1
                        && context.getBlockState(drier).getValue(DehumidifierBlock.LEVEL) == 1,
                "a tray set under a Dehumidifier with a level in it took the level");
        for (int step = 0; step < perLevel; step++) {
            Climate.afterStep(world, root, 2, 3);
        }
        context.assertTrue(context.getBlockState(drier.below()).getValue(HydroTrayBlock.LEVEL) == 1
                        && context.getBlockState(drier).getValue(DehumidifierBlock.LEVEL) == 2,
                "a level made over a tray dripped into it");
        context.setBlock(drier.below(), Blocks.CAULDRON);
        context.assertTrue(context.getBlockState(drier.below()).is(Blocks.WATER_CAULDRON)
                        && context.getBlockState(drier.below()).getValue(LayeredCauldronBlock.LEVEL) == 2
                        && context.getBlockState(drier).getValue(DehumidifierBlock.LEVEL) == 0,
                "a cauldron set under a Dehumidifier with two levels in it did not take them");
        for (int step = 0; step < perLevel; step++) {
            Climate.afterStep(world, root, 2, 3);
        }
        context.assertTrue(context.getBlockState(drier.below()).getValue(LayeredCauldronBlock.LEVEL) == 3
                        && context.getBlockState(drier).getValue(DehumidifierBlock.LEVEL) == 0,
                "a level made over a cauldron with room did not drip into it");

        // Bone meal's jump of several ages is that many steps' water, as the same ages grown one by one would be.
        context.setBlock(drier.below(), Blocks.STONE);
        Climate.afterStep(world, root, 0, perLevel);
        context.assertTrue(context.getBlockState(drier).getValue(DehumidifierBlock.LEVEL) == 1,
                "a jump of " + perLevel + " ages at once filled no level, where as many single steps fill one");

        // A field plant gives it nothing.
        context.setBlock(ROOF, Blocks.AIR);
        context.setBlock(new BlockPos(CROP.getX(), 8, CROP.getZ()), Blocks.AIR);
        context.setBlock(drier.below(), Blocks.STONE);
        for (int step = 0; step < perLevel; step++) {
            Climate.afterStep(world, root, 2, 3);
        }
        context.assertTrue(context.getBlockState(drier).getValue(DehumidifierBlock.LEVEL) == 1,
                "a plant under open sky filled the Dehumidifier beside it");
        context.succeed();
    }

    /**
     * Its tank is taken out as a cauldron's water is: a bottle a level, a bucket a full tank. A real comparator beside
     * it reads the tank, a level to a step, and follows every change of it: a bottle, a bucket, a drip into a
     * cauldron set under it.
     */
    public static void aDehumidifierEmptiesAsACauldronDoes(GameTestHelper context) {
        ServerPlayer player = Players.survival(context);
        BlockPos drier = new BlockPos(3, 2, 3);
        BlockPos comparator = drier.north();
        // The comparator first: one set down reads nothing until its input changes.
        context.setBlock(comparator.below(), Blocks.STONE);
        context.setBlock(comparator, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.SOUTH));
        dehumidifier(context, drier, 2, false);
        context.startSequence()
                .thenWaitUntil(() -> compared(context, comparator, 2, "with a tank of two"))
                .thenExecute(() -> {
                    Players.click(context, player, drier, new ItemStack(Items.GLASS_BOTTLE));
                    ItemStack bottle = player.getMainHandItem();
                    context.assertTrue(bottle.is(Items.POTION) && bottle.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER),
                            "a glass bottle on a Dehumidifier came back as " + bottle);
                    context.assertTrue(context.getBlockState(drier).getValue(DehumidifierBlock.LEVEL) == 1, "a bottle took no level");
                })
                .thenWaitUntil(() -> compared(context, comparator, 1, "after a bottle took a level"))
                .thenExecute(() -> {
                    Players.click(context, player, drier, new ItemStack(Items.BUCKET));
                    context.assertTrue(player.getMainHandItem().is(Items.BUCKET) && context.getBlockState(drier).getValue(DehumidifierBlock.LEVEL) == 1,
                            "a bucket took a tank that was not full");
                    dehumidifier(context, drier, DehumidifierBlock.MAX_LEVEL, false);
                })
                .thenWaitUntil(() -> compared(context, comparator, DehumidifierBlock.MAX_LEVEL, "with a full tank"))
                .thenExecute(() -> {
                    Players.click(context, player, drier, new ItemStack(Items.BUCKET));
                    context.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET) && context.getBlockState(drier).getValue(DehumidifierBlock.LEVEL) == 0,
                            "a bucket on a full tank came back as " + player.getMainHandItem() + ", the tank at "
                                    + context.getBlockState(drier).getValue(DehumidifierBlock.LEVEL));
                })
                .thenWaitUntil(() -> compared(context, comparator, 0, "after a bucket took the tank"))
                .thenExecute(() -> {
                    dehumidifier(context, drier, 2, false);
                })
                .thenWaitUntil(() -> compared(context, comparator, 2, "with a tank of two again"))
                .thenExecute(() -> {
                    context.setBlock(drier.below(), Blocks.CAULDRON);
                    context.assertTrue(context.getBlockState(drier).getValue(DehumidifierBlock.LEVEL) == 0,
                            "a cauldron set under a tank of two left " + context.getBlockState(drier).getValue(DehumidifierBlock.LEVEL));
                })
                .thenWaitUntil(() -> compared(context, comparator, 0, "after its tank dripped into a cauldron"))
                .thenSucceed();
    }

    /**
     * A pipe takes its water through the loader's own fluid API a level at a time, on either loader: a bottle on
     * Fabric, 333 mB on NeoForge, the whole bucket for a full tank. Nothing goes in. A rolled-back transaction leaves the
     * tank as it was, two lookups in one transaction take the water once, and a comparator beside the tank sees
     * what the pipe took.
     */
    public static void aPipeTakesADehumidifiersWater(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos drier = new BlockPos(3, 2, 3);
        BlockPos at = context.absolutePos(drier);
        BlockPos comparator = drier.north();
        context.setBlock(comparator.below(), Blocks.STONE);
        context.setBlock(comparator, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.SOUTH));
        dehumidifier(context, drier, DehumidifierBlock.MAX_LEVEL, false);
        // The comparator settles on the full tank first, so what it reads at the end is the pipe's doing.
        context.startSequence()
                .thenWaitUntil(() -> compared(context, comparator, DehumidifierBlock.MAX_LEVEL, "with a full tank"))
                .thenExecute(() -> {
                    int one = TestPlatform.INSTANCE.extractWater(world, at, Direction.UP, 1, 1, false);
                    context.assertTrue(one == 1, "a pipe asking a full tank for a bottle took " + one);
                    context.assertTrue(context.getBlockState(drier).getValue(DehumidifierBlock.LEVEL) == 2,
                            "the tank after a pipe asked for a bottle: " + context.getBlockState(drier).getValue(DehumidifierBlock.LEVEL));
                })
                .thenExecute(() -> dehumidifier(context, drier, DehumidifierBlock.MAX_LEVEL, false))
                .thenWaitUntil(() -> compared(context, comparator, DehumidifierBlock.MAX_LEVEL, "with a full tank again"))
                .thenExecute(() -> {
                    int aborted = TestPlatform.INSTANCE.extractWater(world, at, Direction.UP, 3, 1, true);
                    context.assertTrue(aborted == 3 && context.getBlockState(drier).getValue(DehumidifierBlock.LEVEL) == 3,
                            "a rolled-back bucket took " + aborted + " and left the tank at " + context.getBlockState(drier).getValue(DehumidifierBlock.LEVEL));
                    int twice = TestPlatform.INSTANCE.extractWater(world, at, Direction.UP, 3, 2, false);
                    context.assertTrue(twice == 3 && context.getBlockState(drier).getValue(DehumidifierBlock.LEVEL) == 0,
                            "two lookups asking a full tank for a bucket each took " + twice + " bottles in all, the tank left at "
                                    + context.getBlockState(drier).getValue(DehumidifierBlock.LEVEL));
                })
                .thenWaitUntil(() -> compared(context, comparator, 0, "after a pipe emptied the tank"))
                .thenSucceed();
    }

    /**
     * A dispenser holding shears trims the hemp plant in front of it in a trim window it has not used, as a
     * player's shears do: the window closes, a leaf drops, the shears wear a point. It never rubs a ripening
     * plant (that is a hand's work), and anything else it shears as vanilla does. Another mod's shears trim too, as
     * by hand: anything in {@code #c:tools/shear} a dispenser already uses (here flint and steel, which the test
     * pack puts in the tag, standing in for them).
     */
    public static void aDispenserWithShearsTrimsHemp(GameTestHelper context) {
        BlockPos dispenserAt = CROP.west();
        BlockPos pulse = dispenserAt.below();
        context.setBlock(dispenserAt, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        DispenserBlockEntity dispenser = context.getBlockEntity(dispenserAt, DispenserBlockEntity.class);
        dispenser.setItem(0, new ItemStack(Items.SHEARS));
        sow(context, ModBlocks.INDICA_CROP, 3);
        context.pulseRedstone(pulse, 1);

        context.runAtTickTime(10, () -> {
            context.assertTrue(context.getBlockState(CROP).getValue(Defoliation.TRIMMED_EARLY),
                    "a dispenser's shears left a plant in its early window untrimmed");
            context.assertTrue(dispenser.getItem(0).getDamageValue() == 1,
                    "the dispenser's shears wore " + dispenser.getItem(0).getDamageValue() + " points for a trim");
            context.assertTrue(leaves(context) == 1, "a dispenser's trim dropped " + leaves(context) + " leaves");
            sow(context, ModBlocks.INDICA_CROP, 6);
            context.pulseRedstone(pulse, 1);
        });
        context.runAtTickTime(20, () -> {
            context.assertTrue(!context.getBlockState(CROP).getValue(Defoliation.RUBBED),
                    "a dispenser's shears rubbed a ripening plant");
            context.assertTrue(dispenser.getItem(0).getDamageValue() == 1,
                    "a dispenser's shears wore a point on a plant they did nothing to");
            sow(context, ModBlocks.INDICA_CROP, 4);
            context.pulseRedstone(pulse, 1);
        });
        context.runAtTickTime(30, () -> {
            context.assertTrue(context.getBlockState(CROP).getValue(Defoliation.TRIMMED_LATE),
                    "a dispenser's shears left a plant in its late window untrimmed");
            context.assertTrue(ModDispenserBehaviors.trimsWith(Items.FLINT_AND_STEEL),
                    "a dispenser does not trim with flint and steel, though the test pack puts it in #c:tools/shear");
            dispenser.setItem(0, new ItemStack(Items.FLINT_AND_STEEL));
            sow(context, ModBlocks.INDICA_CROP, 3);
            context.pulseRedstone(pulse, 1);
        });
        context.runAtTickTime(40, () -> {
            context.assertTrue(context.getBlockState(CROP).getValue(Defoliation.TRIMMED_EARLY),
                    "a dispenser holding another pair of #c:tools/shear left a plant in its early window untrimmed");
            context.succeed();
        });
    }

    /**
     * A Hygrometer's needle shows the air where it hangs as a plant there would feel it, read once a second: normal
     * in a plains room, damp with a pool beside it, dry once a running Dehumidifier takes two steps off. A comparator
     * reads the room's own damp, the Dehumidifier left out, so a dial wired to one never switches it off for drying
     * the room: 8 while the room is damp of itself, nothing once it is not. It hangs on a wall, and falls with it.
     */
    public static void aHygrometerReadsTheAir(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        BlockPos dial = new BlockPos(3, 3, 3);
        BlockPos at = context.absolutePos(dial);
        fillBiome(context, dial, "minecraft:plains");
        context.setBlock(dial.south(), Blocks.STONE);
        context.setBlock(dial, ModBlocks.HYGROMETER.defaultBlockState());
        context.startSequence()
                .thenWaitUntil(() -> needle(context, dial, Humidity.NORMAL, "in a plains room"))
                .thenExecute(() -> {
                    context.assertTrue(signal(context, dial) == 0, "a comparator by a dial in normal air reads " + signal(context, dial));
                    pool(context, dial.west(2).below());
                })
                .thenWaitUntil(() -> needle(context, dial, Humidity.DAMP, "with a pool two blocks off"))
                .thenExecute(() -> {
                    context.assertTrue(signal(context, dial) == HygrometerBlock.DAMP_SIGNAL,
                            "a comparator by a dial in a damp room reads " + signal(context, dial));
                    dehumidifier(context, dial.east(3), 0, true);
                })
                .thenWaitUntil(() -> needle(context, dial, Humidity.DRY, "with a pool and a running Dehumidifier"))
                .thenExecute(() -> {
                    context.assertTrue(signal(context, dial) == HygrometerBlock.DAMP_SIGNAL,
                            "a comparator by a dial a Dehumidifier dries reads " + signal(context, dial)
                                    + ": it reads the room's own damp, the Dehumidifier left out");
                    context.setBlock(dial.west(2).below(), Blocks.STONE);
                })
                .thenWaitUntil(() -> needle(context, dial, Humidity.DRY, "with a running Dehumidifier and no pool"))
                .thenExecute(() -> {
                    context.assertTrue(signal(context, dial) == 0,
                            "a comparator by a dial in a room no longer damp reads " + signal(context, dial));
                    context.setBlock(dial.south(), Blocks.AIR);
                    context.assertTrue(context.getBlockState(dial).isAir(), "a dial stayed up when its wall went");
                    context.assertTrue(!world.getBlockState(at).is(ModBlocks.HYGROMETER), "the dial is still there");
                })
                .thenSucceed();
    }

    /**
     * A dial reads the plants around it, not the air where it hangs: the dampest plant out of water within a
     * Dehumidifier's reach of it (9 x 9, two blocks up and down), as that plant feels it. Hung high on a hydro
     * room's wall, it reads the plant on a tray two blocks below it (a tray with no plant counts for nothing); a
     * running Dehumidifier drying the plants moves its needle but not its comparator; a plant three blocks under it
     * is out of its reach.
     */
    public static void aHygrometerReadsThePlantsAroundIt(GameTestHelper context) {
        BlockPos dial = new BlockPos(3, 5, 3);
        BlockPos onTray = new BlockPos(1, 3, 3);
        BlockPos onSoil = new BlockPos(4, 3, 1);
        BlockPos drier = new BlockPos(5, 3, 5);
        for (BlockPos at : List.of(dial, onTray, onSoil, onTray.below(2))) {
            fillBiome(context, at, "minecraft:plains");
        }
        context.setBlock(dial.south(), Blocks.STONE);
        context.setBlock(dial, ModBlocks.HYGROMETER.defaultBlockState());
        context.setBlock(onTray.below(), ModBlocks.HYDRO_TRAY.defaultBlockState().setValue(HydroTrayBlock.LEVEL, HydroTrayBlock.MAX_LEVEL));
        context.startSequence()
                .thenWaitUntil(() -> needle(context, dial, Humidity.NORMAL, "over a hydro tray with no plant on it"))
                .thenExecute(() -> {
                    plant(context, onTray);
                    context.setBlock(onSoil.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE));
                    plant(context, onSoil);
                })
                .thenWaitUntil(() -> needle(context, dial, Humidity.DAMP, "two blocks over a plant on a tray and one on farmland"))
                .thenExecute(() -> {
                    context.assertTrue(signal(context, dial) == HygrometerBlock.DAMP_SIGNAL,
                            "a comparator by a dial over a plant on a tray reads " + signal(context, dial));
                    dehumidifier(context, drier, 0, true);
                })
                .thenWaitUntil(() -> needle(context, dial, Humidity.DRY, "over plants a running Dehumidifier dries"))
                .thenExecute(() -> {
                    context.assertTrue(signal(context, dial) == HygrometerBlock.DAMP_SIGNAL,
                            "a comparator by a dial over plants a Dehumidifier dries reads " + signal(context, dial)
                                    + ": it reads their own damp, the Dehumidifier left out");
                    clearDehumidifier(context, drier);
                    // The tray plant moved a block down: three under the dial, out of its reach.
                    context.setBlock(onTray, Blocks.AIR);
                    context.setBlock(onTray.below(), Blocks.AIR);
                    context.setBlock(onTray.below(2), ModBlocks.HYDRO_TRAY.defaultBlockState().setValue(HydroTrayBlock.LEVEL, HydroTrayBlock.MAX_LEVEL));
                    plant(context, onTray.below());
                })
                .thenWaitUntil(() -> needle(context, dial, Humidity.NORMAL, "with the tray plant three blocks under it"))
                .thenSucceed();
    }

    /** A fresh neutral Purple Kush at age 2 at {@code root}, one block tall, on whatever bed is under it. */
    private static void plant(GameTestHelper context, BlockPos root) {
        context.setBlock(root, Defoliation.unworked(ModBlocks.INDICA_CROP.defaultBlockState()).setValue(BlockStateProperties.AGE_7, 2));
        context.assertTrue(context.getBlockState(root).is(ModBlocks.INDICA_CROP), "test setup: no plant came up at " + root);
    }

    /**
     * The line a block-info overlay shows under a plant ({@link GrowClimateLine}) reads the plant's climate as its mildew
     * does: the air and humidity of its root, whichever block of it is looked at, and its mildew when it has any. A
     * three-block sativa under glass reads stirred air; a running fan blowing on it, the open's; water beside it, damp;
     * a stone roof, once the light has settled, still air. A crop with no mildew state, ours or vanilla's, has no line.
     */
    public static void aPlantsClimateLineReadsItsRoot(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        fillBiome(context, CROP, "minecraft:plains");
        context.setBlock(SOIL, Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE));
        BlockState ripe = Defoliation.unworked(ModBlocks.SATIVA_CROP.defaultBlockState()).setValue(BlockStateProperties.AGE_7, 7);
        context.setBlock(CROP, ripe.setValue(SativaCropBlock.SEGMENT, TriplePlantSegment.LOWER));
        context.setBlock(CROP.above(), ripe.setValue(SativaCropBlock.SEGMENT, TriplePlantSegment.MIDDLE));
        context.setBlock(CROP.above(2), ripe.setValue(SativaCropBlock.SEGMENT, TriplePlantSegment.UPPER));
        context.setBlock(ROOF, Blocks.GLASS);
        BlockPos root = context.absolutePos(CROP);
        Mildew.write(world, root, Mildew.CLEAN);

        line(context, Climate.Air.STIRRED, Humidity.NORMAL, Mildew.CLEAN, "a sativa under glass");
        fan(context, CROP.east(4), Direction.WEST, AttachFace.FLOOR, true);
        line(context, Climate.Air.OPEN, Humidity.NORMAL, Mildew.CLEAN, "a sativa under glass with a running fan on it");
        clearFan(context, CROP.east(4));
        pool(context, CROP.east().south());
        line(context, Climate.Air.STIRRED, Humidity.DAMP, Mildew.CLEAN, "a sativa under glass beside water");
        Mildew.write(world, root, Mildew.MILDEWED);
        line(context, Climate.Air.STIRRED, Humidity.DAMP, Mildew.MILDEWED, "a mildewed sativa under glass beside water");
        List<net.minecraft.network.chat.Component> lines = GrowClimateLine.lines(GrowClimateLine.of(world, root).orElseThrow());
        context.assertTrue(lines.size() == 2, "a mildewed plant's line names its mildew on a line of its own: " + lines);
        Mildew.write(world, root, Mildew.CLEAN);
        context.assertTrue(GrowClimateLine.lines(GrowClimateLine.of(world, root).orElseThrow()).size() == 1,
                "a clean plant's line names mildew it does not have");

        // Neither a crop of ours without a mildew state nor vanilla's wheat has a line.
        BlockPos other = new BlockPos(5, 3, 5);
        context.setBlock(other.below(), Blocks.FARMLAND);
        for (Block crop : List.of(ModBlocks.RUDERALIS_CROP, Blocks.WHEAT)) {
            context.setBlock(other, crop);
            context.assertTrue(GrowClimateLine.of(world, context.absolutePos(other)).isEmpty(),
                    BuiltInRegistries.BLOCK.getKey(crop) + " has a grow climate line");
        }

        // A roof the sun does not reach: the top block's sky light moves a tick or two after the stone is set.
        context.setBlock(ROOF, Blocks.STONE);
        context.runAfterDelay(4, () -> {
            line(context, Climate.Air.STILL, Humidity.DAMP, Mildew.CLEAN, "a sativa under stone beside water");
            context.succeed();
        });
    }

    /**
     * The line on every block of the plant at {@link #CROP} is {@code air}, {@code humidity} and {@code mildew}, and is
     * what the plant's own rules read at its root.
     */
    private static void line(GameTestHelper context, Climate.Air air, Humidity humidity, Mildew mildew, String where) {
        ServerLevel world = context.getLevel();
        BlockPos root = context.absolutePos(CROP);
        boolean roofed = Mildew.roofed(world, root);
        GrowClimateLine.Reading rules = new GrowClimateLine.Reading(Climate.air(world, root, 3, roofed),
                Climate.humidity(world, root, roofed, Climate.onTray(world, root)), world.getBlockState(root).getValue(Mildew.PROPERTY));
        GrowClimateLine.Reading expected = new GrowClimateLine.Reading(air, humidity, mildew);
        context.assertTrue(rules.equals(expected), "test setup: " + where + " reads " + rules + " by its own rules, not " + expected);
        for (int y = 0; y < 3; y++) {
            GrowClimateLine.Reading shown = GrowClimateLine.of(world, root.above(y)).orElse(null);
            context.assertTrue(expected.equals(shown), where + ": its block " + y + " up shows " + shown + ", not " + expected);
        }
    }

    // ----- helpers -----

    private static void needle(GameTestHelper context, BlockPos dial, Humidity expected, String where) {
        Humidity now = context.getBlockState(dial).getValue(HygrometerBlock.HUMIDITY);
        context.assertTrue(now == expected, "a dial " + where + " reads " + now + ", not " + expected);
    }

    private static int signal(GameTestHelper context, BlockPos dial) {
        BlockPos at = context.absolutePos(dial);
        return context.getBlockState(dial).getAnalogOutputSignal(context.getLevel(), at, Direction.NORTH);
    }

    /** A water source at {@code at}, walled in on every side but the top, so it stands and never flows. */
    private static void pool(GameTestHelper context, BlockPos at) {
        context.setBlock(at.below(), Blocks.STONE);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            context.setBlock(at.relative(side), Blocks.STONE);
        }
        context.setBlock(at, Blocks.WATER);
    }

    private static int leaves(GameTestHelper context) {
        AABB box = new AABB(context.absolutePos(BlockPos.ZERO)).inflate(8);
        return context.getLevel().getEntitiesOfClass(ItemEntity.class, box, item -> item.getItem().is(ModItems.HEMP_LEAF))
                .stream().mapToInt(item -> item.getItem().getCount()).sum();
    }

    /** A comparator at {@code comparator} puts out {@code expected}. */
    private static void compared(GameTestHelper context, BlockPos comparator, int expected, String when) {
        int signal = context.getBlockEntity(comparator, ComparatorBlockEntity.class).getOutputSignal();
        context.assertTrue(signal == expected, "a comparator by a Dehumidifier " + when + " reads " + signal + ", not " + expected);
    }

    /** A Dehumidifier at {@code at} holding {@code level}, on stone, running if {@code running} (a redstone block east of it). */
    private static void dehumidifier(GameTestHelper context, BlockPos at, int level, boolean running) {
        context.setBlock(at.below(), Blocks.STONE);
        context.setBlock(at, ModBlocks.DEHUMIDIFIER.defaultBlockState().setValue(DehumidifierBlock.LEVEL, level));
        if (running) {
            context.setBlock(at.east(), Blocks.REDSTONE_BLOCK);
        }
        context.assertTrue(context.getBlockState(at).getValue(DehumidifierBlock.POWERED) == running
                        && context.getBlockState(at).getValue(DehumidifierBlock.LEVEL) == level,
                "test setup: the Dehumidifier at " + at + " did not come up as set");
    }

    private static void clearDehumidifier(GameTestHelper context, BlockPos at) {
        context.setBlock(at.east(), Blocks.AIR);
        context.setBlock(at, Blocks.AIR);
        context.setBlock(at.below(), Blocks.AIR);
    }

    /** The onset odds of a fresh neutral Purple Kush at age 2 on a fed, full Hydro Tray at {@link #SOIL}. */
    private static float onTray(GameTestHelper context) {
        for (int y = 2; y >= 0; y--) {
            if (context.getBlockState(CROP.above(y)).getBlock() instanceof CropBlock) {
                context.setBlock(CROP.above(y), Blocks.AIR);
            }
        }
        context.setBlock(SOIL, ModBlocks.HYDRO_TRAY.defaultBlockState().setValue(HydroTrayBlock.LEVEL, HydroTrayBlock.MAX_LEVEL));
        context.setBlock(CROP, Defoliation.unworked(ModBlocks.INDICA_CROP.defaultBlockState()).setValue(BlockStateProperties.AGE_7, 2));
        Genes.neutral(context, CROP);
        BlockPos at = context.absolutePos(CROP);
        return Mildew.onsetFor(context.getLevel(), at, context.getLevel().getBlockState(at));
    }

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

    /** The onset odds of a fresh neutral plant of {@code crop} at age 2 on moist farmland under the glass roof. */
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
