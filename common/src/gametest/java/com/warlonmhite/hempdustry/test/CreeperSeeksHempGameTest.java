package com.warlonmhite.hempdustry.test;

import com.warlonmhite.hempdustry.block.ModBlocks;
import com.warlonmhite.hempdustry.block.custom.IndicaCropBlock;
import com.warlonmhite.hempdustry.entity.ai.SeekHempCropGoal;
import com.warlonmhite.hempdustry.util.ModTags;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Creepers now and then wander into an open, grown hemp field — and never gather at one.
 *
 * <p>Everything random about the goal is stepped over, not waited for: {@code trigger}
 * skips the per-tick roll (and the despawn-counter rule, which a test world with no player would
 * otherwise trip), and {@link SeekHempCropGoal#findCover} is asked with a sample count that cannot
 * miss a 7×7 field inside a range-3 square. What is left is the rules, which is what can break.
 *
 * <p>Which mobs take the walk at all is {@code #hempdustry:drawn_to_hemp} ({@link #onlyAMobDrawnToHempSeeksIt}).
 *
 * <p>Every rule here fails the same way when it breaks: a creeper that goes to a field it should not,
 * or one that never leaves. Neither is an error in a log, and both were the old goal's behaviour.
 */
public final class CreeperSeeksHempGameTest {

    private static final int FIELD = 7;
    private static final int FLOWERING_AGE = com.warlonmhite.hempdustry.block.custom.GrowLight.FLOWERING_AGE;
    private static final BlockPos CENTRE = new BlockPos(4, 2, 4);
    /** Samples enough to hit a 7×7 field in a 7×7 search square every time. */
    private static final int ALWAYS = 256;
    /** The longest lurk is 120 ticks. */
    private static final int LURK_OVER_BY_TICK = 140;

    public static void creeperSeeksHempCrop(GameTestHelper context) {
        floor(context);
        openSky(context);
        Creeper creeper = interested(context, CENTRE);

        context.assertTrue(goalOf(creeper) != null,
                "MobGoalsMixin did not add SeekHempCropGoal to a creeper");

        context.assertTrue(SeekHempCropGoal.findCover(creeper, 3, ALWAYS) == null,
                "a creeper found cover in bare ground");

        field(context, 1);
        context.assertTrue(SeekHempCropGoal.findCover(creeper, 3, ALWAYS) == null,
                "a field of seedlings drew a creeper; nothing a creeper could hide in grows yet");

        field(context, IndicaCropBlock.MAX_AGE);
        BlockPos cover = SeekHempCropGoal.findCover(creeper, 3, ALWAYS);
        context.assertTrue(cover != null, "a grown field under open sky did not draw the creeper");
        context.assertTrue(context.getLevel().getBlockState(cover).is(ModBlocks.INDICA_CROP),
                "findCover returned " + cover + ", which is not the root of a hemp plant");

        roof(context, Blocks.GLASS);
        context.assertTrue(SeekHempCropGoal.findCover(creeper, 3, ALWAYS) == null,
                "a field under a glass roof drew a creeper; only open fields may");
        roof(context, Blocks.AIR);

        // A flowering field holds one creeper (a ripe one two: ripeHempDrawsMoreCreepers).
        field(context, FLOWERING_AGE);
        Creeper first = interested(context, CENTRE.offset(1, 0, 1));
        context.assertTrue(SeekHempCropGoal.findCover(creeper, 3, ALWAYS) == null,
                "a flowering field another creeper is already in drew a second one");
        first.discard();
        field(context, IndicaCropBlock.MAX_AGE);


        // It notices the field sooner or later -- a glance is only 8 columns in a 49x49 square, so
        // this retries rather than expecting the first one to land. A miss has no side effects.
        SeekHempCropGoal goal = goalOf(creeper);
        boolean noticed = false;
        for (int glance = 0; glance < 2000 && !noticed; glance++) {
            goal.trigger();
            noticed = goal.canUse();
        }
        context.assertTrue(noticed, "a creeper standing in a grown field never noticed it in 2000 glances");
        goal.start();
        goal.stop();

        // Then it prowls at most MAX_PROWLS times and walks away on its own. A prowl that misses
        // the field ends the visit early, which is allowed; staying for ever is not.
        int prowls = 0;
        while (prowls < 20) {
            goal.trigger();
            if (!goal.canUse()) {
                break;
            }
            goal.start();
            goal.stop();
            prowls++;
        }
        context.assertTrue(prowls <= 6, "a creeper prowled a field " + prowls
                + " times in a row; it should stop after 3-6 -- 20 means it never leaves");
        for (int glance = 0; glance < 500; glance++) {
            goal.trigger();
            context.assertFalse(goal.canUse(),
                    "a creeper that has just left a field went straight back to it instead of resting");
        }

        // A creeper that cares only for ripe hemp never notices a flowering field, however many times it
        // looks. Alone in the field: with another creeper in it the "taken" rule refuses first and this
        // proves nothing.
        creeper.discard();
        field(context, FLOWERING_AGE);
        Creeper indifferent = context.spawn(EntityTypes.CREEPER, CENTRE.offset(-1, 0, -1));
        indifferent.setUUID(new UUID(0L, 3L));
        SeekHempCropGoal indifferentGoal = goalOf(indifferent);
        for (int glance = 0; glance < 2000; glance++) {
            indifferentGoal.trigger();
            context.assertFalse(indifferentGoal.canUse(),
                    "a creeper whose UUID says it cares only for ripe hemp noticed a flowering field");
        }

        indifferent.discard();

        // A creeper that has arrived lurks -- the goal holds on with the navigation idle -- and
        // then lets go by itself. A lurk with no end is the old goal's parking, back again. The
        // AI is off so nothing else moves the navigation; the goal is driven by hand.
        Creeper lurker = interested(context, CENTRE);
        lurker.setNoAi(true);
        SeekHempCropGoal lurk = new SeekHempCropGoal(lurker);
        lurk.start();
        lurker.getNavigation().stop();
        context.assertTrue(lurk.canContinueToUse(), "a creeper that arrived did not lurk at all");
        context.runAtTickTime(LURK_OVER_BY_TICK, () -> {
            context.assertFalse(lurk.canContinueToUse(),
                    "a creeper was still lurking " + LURK_OVER_BY_TICK + " ticks after it arrived; a lurk is 2-6 s");
            context.succeed();
        });
    }

    /**
     * The pull grows with the crop: half of all creepers care for a grown plant, three quarters for a
     * flowering one, all for a ripe one, read off the plant the glance lands on; a ripe field holds two
     * creepers, a third switches it off.
     */
    public static void ripeHempDrawsMoreCreepers(GameTestHelper context) {
        // Who cares for what, off the UUID's two low bits.
        int[] needed = {SeekHempCropGoal.GROWN, SeekHempCropGoal.FLOWERING, SeekHempCropGoal.GROWN, SeekHempCropGoal.RIPE};
        for (int bits = 0; bits < 4; bits++) {
            Creeper probe = context.spawn(EntityTypes.CREEPER, new BlockPos(0, 2, 0));
            probe.setUUID(new UUID(7L, 4L * 1234L + bits));
            context.assertTrue(SeekHempCropGoal.pullNeeded(probe) == needed[bits], "a creeper with UUID bits " + bits
                    + " needs pull " + SeekHempCropGoal.pullNeeded(probe) + ", not " + needed[bits]);
            probe.discard();
        }
        // The stage each age pulls with.
        context.assertTrue(SeekHempCropGoal.hempPull(crop(FLOWERING_AGE - 1)) == SeekHempCropGoal.GROWN,
                "a plant before flowering pulled as " + SeekHempCropGoal.hempPull(crop(FLOWERING_AGE - 1)));
        context.assertTrue(SeekHempCropGoal.hempPull(crop(FLOWERING_AGE)) == SeekHempCropGoal.FLOWERING,
                "a flowering plant pulled as " + SeekHempCropGoal.hempPull(crop(FLOWERING_AGE)));
        context.assertTrue(SeekHempCropGoal.hempPull(crop(IndicaCropBlock.MAX_AGE)) == SeekHempCropGoal.RIPE,
                "a ripe plant pulled as " + SeekHempCropGoal.hempPull(crop(IndicaCropBlock.MAX_AGE)));
        context.assertTrue(SeekHempCropGoal.hempPull(Blocks.WHEAT.defaultBlockState()) == SeekHempCropGoal.NO_PULL,
                "wheat pulled a creeper");

        floor(context);
        openSky(context);
        field(context, FLOWERING_AGE);
        Creeper ripeOnly = creeper(context, CENTRE, 3L);
        context.assertTrue(SeekHempCropGoal.findCover(ripeOnly, 3, ALWAYS) == null,
                "a creeper that cares only for ripe hemp noticed a flowering field");
        ripeOnly.discard();
        Creeper flowering = creeper(context, CENTRE, 1L);
        context.assertTrue(SeekHempCropGoal.findCover(flowering, 3, ALWAYS) != null,
                "a creeper that cares for flowering hemp missed a flowering field");
        flowering.discard();

        field(context, IndicaCropBlock.MAX_AGE);
        ripeOnly = creeper(context, CENTRE, 3L);
        context.assertTrue(SeekHempCropGoal.findCover(ripeOnly, 3, ALWAYS) != null,
                "a creeper missed a ripe field; every creeper cares for ripe hemp");
        Creeper second = creeper(context, CENTRE.offset(1, 0, 1), 2L);
        context.assertTrue(SeekHempCropGoal.findCover(ripeOnly, 3, ALWAYS) != null,
                "a ripe field with one other creeper in it drew no second; a ripe field holds two");
        Creeper third = creeper(context, CENTRE.offset(-1, 0, -1), 0L);
        context.assertTrue(SeekHempCropGoal.findCover(ripeOnly, 3, ALWAYS) == null,
                "a ripe field with two creepers in it drew a third");
        second.discard();
        third.discard();
        ripeOnly.discard();
        context.succeed();
    }

    /**
     * Which mobs take the walk is {@code #hempdustry:drawn_to_hemp}, vanilla's creeper alone by default. A creeper out
     * of it never notices a grown field however often it looks, and one made while it is out is given no walk; back
     * in, it notices. A pig the tag lists is made with the walk and takes it; out of the tag again, the walk it was
     * given never starts, and a pig no tag lists is given none.
     */
    public static void onlyAMobDrawnToHempSeeksIt(GameTestHelper context) {
        floor(context);
        openSky(context);
        field(context, IndicaCropBlock.MAX_AGE);
        Creeper creeper = interested(context, CENTRE);
        SeekHempCropGoal goal = goalOf(creeper);
        context.assertTrue(goal != null, "a creeper, which the tag lists by default, was made without the walk");
        Tagged.without(EntityTypes.CREEPER, ModTags.EntityTypes.DRAWN_TO_HEMP, () -> {
            context.assertFalse(noticed(goal, 2000),
                    "a creeper out of #hempdustry:drawn_to_hemp noticed a grown field under open sky");
            Creeper untagged = interested(context, CENTRE.offset(-2, 0, -2));
            context.assertTrue(goalOf(untagged) == null, "a creeper made while out of the tag was given the walk");
            untagged.discard();
        });
        context.assertTrue(noticed(goal, 2000), "a creeper back in the tag never noticed a grown field in 2000 glances");
        // A walk under way stops the moment its mob leaves the tag, or finds a target: the tag is asked on every tick
        // of the walk, and a modded mob whose attack sits under the walk's priority must not lurk with a target.
        goal.start();
        context.assertTrue(goal.canContinueToUse(), "a creeper that just started its walk did not keep on");
        Tagged.without(EntityTypes.CREEPER, ModTags.EntityTypes.DRAWN_TO_HEMP, () -> context.assertFalse(
                goal.canContinueToUse(), "a creeper taken out of #hempdustry:drawn_to_hemp kept on walking"));
        Pig target = pig(context, CENTRE.offset(-3, 0, 3));
        creeper.setTarget(target);
        context.assertFalse(goal.canContinueToUse(), "a creeper with a target kept on walking into the field");
        context.assertFalse(noticed(goal, 2000), "a creeper with a target noticed a field");
        creeper.setTarget(null);
        target.discard();
        goal.stop();
        creeper.discard();

        Pig[] listed = new Pig[1];
        Tagged.within(EntityTypes.PIG, ModTags.EntityTypes.DRAWN_TO_HEMP, () -> {
            listed[0] = pig(context, CENTRE);
            SeekHempCropGoal pigGoal = goalOf(listed[0]);
            context.assertTrue(pigGoal != null, "a pig the tag lists was made without the walk");
            context.assertTrue(noticed(pigGoal, 2000), "a pig the tag lists never noticed a grown field in 2000 glances");
        });
        context.assertFalse(noticed(goalOf(listed[0]), 2000), "a pig the tag no longer lists still noticed a field");
        Pig plain = pig(context, CENTRE.offset(1, 0, 1));
        context.assertTrue(goalOf(plain) == null, "a pig no tag lists was given the walk");
        listed[0].discard();
        plain.discard();

        // A mob a Brain walks takes no walk, whatever the tags say: a goat's walk target would fight the goal.
        Tagged.within(EntityTypes.GOAT, ModTags.EntityTypes.DRAWN_TO_HEMP, () ->
                Tagged.within(EntityTypes.GOAT, ModTags.EntityTypes.DRAWN_TO_STASH, () -> {
                    Mob goat = context.spawn(EntityTypes.GOAT, CENTRE.offset(-1, 0, 1));
                    context.assertTrue(goals(goat).stream().noneMatch(goal1 -> goal1 instanceof SeekHempCropGoal
                                    || goal1 instanceof com.warlonmhite.hempdustry.entity.ai.SeekStashJarGoal),
                            "a goat, which a Brain walks, was given a walk by the tags: " + goals(goat));
                    goat.discard();
                }));
        context.succeed();
    }

    /** Whether {@code goal} notices a field within {@code glances} glances. A miss has no side effects. */
    private static boolean noticed(SeekHempCropGoal goal, int glances) {
        for (int glance = 0; glance < glances; glance++) {
            goal.trigger();
            if (goal.canUse()) {
                return true;
            }
        }
        return false;
    }

    /** A pig whose UUID puts it in the half that notices hemp, held still. */
    private static Pig pig(GameTestHelper context, BlockPos at) {
        Pig pig = context.spawn(EntityTypes.PIG, at);
        pig.setUUID(new UUID(pig.getUUID().getMostSignificantBits(), 2L));
        pig.setNoAi(true);
        return pig;
    }

    private static Creeper creeper(GameTestHelper context, BlockPos at, long bits) {
        Creeper creeper = context.spawn(EntityTypes.CREEPER, at);
        creeper.setUUID(new UUID(creeper.getUUID().getMostSignificantBits(), bits));
        creeper.setNoAi(true);
        return creeper;
    }

    private static net.minecraft.world.level.block.state.BlockState crop(int age) {
        return ModBlocks.INDICA_CROP.defaultBlockState().setValue(IndicaCropBlock.AGE, age)
                .setValue(IndicaCropBlock.HALF, DoubleBlockHalf.LOWER);
    }

    /** A creeper whose UUID puts it in the half that notices hemp. */
    private static Creeper interested(GameTestHelper context, BlockPos at) {
        // spawn, NOT spawnWithNoFreeWill: GameTestHelper.spawnWithNoFreeWill strips every goal off the mob.
        Creeper creeper = context.spawn(EntityTypes.CREEPER, at);
        creeper.setUUID(new UUID(creeper.getUUID().getMostSignificantBits(), 2L));
        context.assertTrue(SeekHempCropGoal.caresAboutHemp(creeper), "test setup: UUID parity");
        return creeper;
    }

    /** Every goal {@code mob}'s goal selector holds. */
    private static List<net.minecraft.world.entity.ai.goal.Goal> goals(Mob mob) {
        try {
            var field = Mob.class.getDeclaredField("goalSelector");
            field.setAccessible(true);
            return ((GoalSelector) field.get(mob)).getAvailableGoals().stream().map(prioritized -> prioritized.getGoal())
                    .map(net.minecraft.world.entity.ai.goal.Goal.class::cast).toList();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static SeekHempCropGoal goalOf(Mob mob) {
        try {
            var field = Mob.class.getDeclaredField("goalSelector");
            field.setAccessible(true);
            return ((GoalSelector) field.get(mob)).getAvailableGoals().stream()
                    .map(prioritized -> prioritized.getGoal())
                    .filter(SeekHempCropGoal.class::isInstance)
                    .map(SeekHempCropGoal.class::cast)
                    .findFirst().orElse(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void floor(GameTestHelper context) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                context.setBlock(new BlockPos(x, 1, z), Blocks.FARMLAND);
            }
        }
    }

    /**
     * The test runner roofs every test box with barriers, so no field in a game test has open sky
     * until the roof over this one is lifted. The goal refusing roofed fields is the point of the
     * glass check below, so the barrier has to go rather than the check.
     */
    private static void openSky(GameTestHelper context) {
        var world = context.getLevel();
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                BlockPos column = context.absolutePos(new BlockPos(x, 0, z));
                int top = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ());
                for (int y = column.getY() + 3; y < top; y++) {
                    world.setBlockAndUpdate(new BlockPos(column.getX(), y, column.getZ()), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /** A 7×7 field around the creeper, at {@code age}. Seedlings are one block tall. */
    private static void field(GameTestHelper context, int age) {
        for (int x = 1; x <= FIELD; x++) {
            for (int z = 1; z <= FIELD; z++) {
                BlockPos root = new BlockPos(x, 2, z);
                context.setBlock(root.above(), Blocks.AIR);
                context.setBlock(root, ModBlocks.INDICA_CROP.defaultBlockState()
                        .setValue(IndicaCropBlock.AGE, age).setValue(IndicaCropBlock.HALF, DoubleBlockHalf.LOWER));
                if (age >= 4) {
                    context.setBlock(root.above(), ModBlocks.INDICA_CROP.defaultBlockState()
                            .setValue(IndicaCropBlock.AGE, age).setValue(IndicaCropBlock.HALF, DoubleBlockHalf.UPPER));
                }
            }
        }
    }

    private static void roof(GameTestHelper context, net.minecraft.world.level.block.Block block) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                context.setBlock(new BlockPos(x, 6, z), block);
            }
        }
    }
}
