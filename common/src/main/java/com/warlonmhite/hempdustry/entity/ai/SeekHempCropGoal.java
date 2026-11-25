package com.warlonmhite.hempdustry.entity.ai;

import com.warlonmhite.hempdustry.block.ModBlocks;
import com.warlonmhite.hempdustry.block.custom.GrowLight;
import com.warlonmhite.hempdustry.block.custom.SativaCropBlock;
import com.warlonmhite.hempdustry.config.ModGameRules;
import com.warlonmhite.hempdustry.util.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Lets a creeper's ordinary wandering end up in a hemp field now and then.
 *
 * <h2>What this is for</h2>
 *
 * A creeper is green, and a grown hemp plant is two or three blocks of green. <b>A creeper standing
 * in a grown field is almost invisible</b>, and a player harvesting one is looking down at the crop
 * rather than out at the horizon. An open field should be a place you glance around before walking
 * into — never a place creepers visibly queue up at.
 *
 * <h2>It is a wander, and nothing else</h2>
 *
 * The first version was a {@code MoveToBlockGoal}, which walks to the nearest target and then
 * <em>holds on for up to a minute</em>; its next search finds the plant it is standing beside, so a
 * creeper that reached a field stayed there for good, and every creeper within 16 blocks of one
 * plant came over. That is a beacon, and a beacon is a mob farm.
 *
 * <p>This is a {@link RandomStrollGoal} instead: the same speed, the same per-tick odds of starting,
 * the same stop-on-arrival and the same "nobody is near, so stand still" rule as the wandering it
 * sits beside at priority 5. The only thing that differs is <em>where</em> it wanders to, so there is
 * no moment a player could point at. Priority 5 also keeps vanilla's rules: a creeper with a target
 * always attacks, because the attack goal at 4 takes the movement control from anything at 5.
 *
 * <h2>How a field is noticed</h2>
 *
 * A creeper does not scan for the nearest plant. It picks a few random columns around itself and
 * looks at what stands in them, so <b>the odds of noticing a field grow with the field's area</b>:
 * a 9×9 field is found in a minute or so, a lone plant almost never, and nothing has to count plants.
 * A column only counts if:
 *
 * <ul>
 *   <li><b>the plant is at least two blocks tall</b> — tall enough to hide a creeper. A freshly sown
 *       field draws nothing, so the danger arrives as the crop comes in, which a player can see
 *       happening;</li>
 *   <li><b>the plant is the top of its column</b> ({@code WORLD_SURFACE}) — open sky. A roof, glass
 *       included, hides a field, so anything grown indoors or in a dark mob-farm room draws
 *       nothing;</li>
 *   <li><b>no other creeper is already in it</b> ({@link #TAKEN_RADIUS}), or one other at most where the
 *       plant is ripe. A field draws one creeper at a time, two once ripe, which is the whole anti-farm
 *       ceiling: throughput can never exceed "a creeper or two wandered in", however much hemp is planted
 *       round a kill chamber, and a third creeper switches it off.</li>
 * </ul>
 *
 * <h2>What a creeper does once it is there</h2>
 *
 * It prowls: a few short wanders from one plant to another in the same field
 * ({@link #MIN_PROWLS}–{@link #MAX_PROWLS}), each still at vanilla's pace, and after each one it
 * <b>lurks</b> — stands in the crop for two to six seconds. A creeper standing still among the
 * plants is the moment a player walks into, and it is the part vanilla wandering cannot supply:
 * between two ordinary wanders a creeper is idle, and vanilla's own wander would take it out of the
 * field a third of the time. Holding the movement control through the pause is what keeps a visit
 * a visit. Then it loses interest for a minute or three ({@link #MIN_REST}–{@link #MAX_REST}) and
 * is an ordinary creeper again.
 *
 * <p><b>Measured</b>, in a pen with ripe fields and seedling fields of equal area: the old goal kept
 * 89% of all creeper time inside hemp, 94% of it standing still, seedlings and a lone plant
 * included. Without the lurk this goal was indistinguishable from vanilla. With it, four creepers
 * over two game hours spent about four times vanilla's share of their time in a grown field, never
 * more than two at once and moving about as much as any wandering creeper.
 *
 * <h2>The pull grows with the crop</h2>
 *
 * <b>How many creepers care depends on the plant the glance lands on</b>: about half for a grown plant
 * that has not started flowering, three quarters once it flowers, every creeper once it is ripe, each
 * creeper's threshold read off its UUID so it holds across saves without storing anything
 * ({@link #pullNeeded}). A field's pull so ramps as it ripens, plant by plant, and a ripe field holds two
 * creepers at once where any other holds one. Noticing stays a sampled glance, so a creeper still heads
 * where its glance landed and never to the nearest ripe plant: a ripe field is more often prowled, never
 * beelined. That the rest ignore a growing field is most of why the behaviour reads as chance rather than
 * as a rule.
 *
 * <p>Nothing about the goal is saved: prowls and rest reset when a chunk reloads, which only ever
 * makes a creeper look at the fields around it once more.
 *
 * <h2>Strain-agnostic, and it stays that way</h2>
 *
 * The plant test is membership of {@code #hempdustry:hemp_crops}, so a third strain's crop counts
 * the day it joins the tag, and a datapack can add or remove plants without any code knowing. It is
 * deliberately not {@code #minecraft:crops}: creepers hunting wheat is a different mod.
 *
 * <h2>Endermen, and one plant</h2>
 *
 * The same goal walks an enderman into a patch of Ender Haze, and only Ender Haze: an enderman
 * standing in an overworld hemp field is the wrong joke. Every rule above holds for it unchanged —
 * the half that care, the glance, the prowl and lurk, the rest, one mob to a field, the off switch —
 * at the enderman's own wandering pace. Their pull does not grow as the patch ripens: that is the
 * creepers' alone.
 *
 * <h2>Drowned, and the sea floor</h2>
 *
 * Drowned are drawn the same way to a ripe patch of Maui Wowie, and only to a ripe one, in the warm shallows it
 * grows in; a lit farm spawns none of them, and a conduit attacks them. Under water no plant is the top of its
 * column, so a drowned reads the sea floor instead ({@code OCEAN_FLOOR}, where the motion-blocking ground ends)
 * and counts the plant up from there; and it swims, so a root is in reach {@link #SWIMMER_Y_DIFFERENCE} above or
 * below it. It never breaks a block: drowned already hunt turtle eggs, this only sends them wandering.
 */
public class SeekHempCropGoal extends RandomStrollGoal {

    /** {@code WaterAvoidingRandomStrollGoal}'s speed on a creeper. Walking faster towards a field would give it away. */
    private static final double CREEPER_SPEED = 0.8;
    /** One in this many ticks a creeper glances around for a field — half as often as it wanders. */
    private static final int NOTICE_CHANCE = 240;
    /** Half the side of the square a creeper looks across, in blocks. */
    private static final int NOTICE_RANGE = 24;
    /** Columns looked at per glance. A 9×9 field 15 blocks off is hit about one glance in four. */
    private static final int NOTICE_SAMPLES = 8;
    /** Odds of the next prowl step once a lurk is over: six times vanilla's, so the field usually wins. */
    private static final int PROWL_CHANCE = 20;
    private static final int PROWL_RANGE = 5;
    private static final int PROWL_SAMPLES = 6;
    private static final int MIN_PROWLS = 3;
    private static final int MAX_PROWLS = 6;
    /** Two to six seconds standing in the crop after each step. */
    private static final int MIN_LURK = 40;
    private static final int MAX_LURK = 120;
    /** One to three minutes before the same creeper looks at hemp again. */
    private static final int MIN_REST = 1200;
    private static final int MAX_REST = 3600;
    /** A creeper this close to a column is already in that field. */
    private static final double TAKEN_RADIUS = 8.0;
    /** How many creepers a ripe field holds at once; any other holds one. */
    private static final int RIPE_FIELD_HOLDS = 2;
    /** A plant's roots this far above or below the creeper's feet are out of reach. */
    private static final int MAX_Y_DIFFERENCE = 3;
    /** As far for a mob that swims to the plant, across the 3 to 6 blocks of water a sea plant grows in. */
    private static final int SWIMMER_Y_DIFFERENCE = 8;
    /** {@code RandomStrollGoal}'s speed on a drowned. */
    private static final double DROWNED_SPEED = 1.0;
    /** {@code WaterAvoidingRandomStrollGoal}'s speed on an enderman. */
    private static final double ENDERMAN_SPEED = 1.0;

    /** Not a plant to stand in. */
    public static final int NO_PULL = 0;
    /** A grown plant, two blocks tall or more, not yet flowering: about half of all creepers care. */
    public static final int GROWN = 1;
    /** A flowering plant: three quarters of all creepers care. */
    public static final int FLOWERING = 2;
    /** A ripe plant: every creeper cares, and two may stand in its field. */
    public static final int RIPE = 3;

    /** How hard each plant pulls ({@link #NO_PULL} for anything that is not one to stand in). */
    private final ToIntFunction<BlockState> pull;
    /** The most any plant pulls this goal's mob: a mob needing more never looks. */
    private final int strongestPull;
    /** Whether the plant is looked for on the sea floor rather than at the open-sky top of its column. */
    private final boolean seaFloor;
    /** Whether the mob must stay in {@code #hempdustry:drawn_to_hemp} to start: the creepers' walk, not the others'. */
    private final boolean tagged;

    private int prowlsLeft;
    private long restingUntil;
    /** When the current lurk ends; 0 while still walking. */
    private long lurkingUntil;

    /**
     * A creeper's, and any mob's in {@code #hempdustry:drawn_to_hemp}: any hemp plant, at a creeper's wandering pace.
     * It starts only while the mob is in the tag ({@link #drawnToHemp}).
     */
    public SeekHempCropGoal(PathfinderMob creeper) {
        this(creeper, CREEPER_SPEED, SeekHempCropGoal::hempPull, RIPE, false, true);
    }

    /**
     * A wander into whatever {@code cover} accepts, at {@code speed} — the mob's own wandering speed,
     * so nothing gives the walk away.
     */
    public SeekHempCropGoal(PathfinderMob mob, double speed, Predicate<BlockState> cover) {
        this(mob, speed, flat(cover), GROWN, false, false);
    }

    private SeekHempCropGoal(PathfinderMob mob, double speed, ToIntFunction<BlockState> pull, int strongestPull,
                             boolean seaFloor, boolean tagged) {
        super(mob, speed, NOTICE_CHANCE);
        this.pull = pull;
        this.strongestPull = strongestPull;
        this.seaFloor = seaFloor;
        this.tagged = tagged;
    }

    /** An enderman's: Ender Haze, and only Ender Haze, at its wandering pace. */
    public static SeekHempCropGoal enderman(PathfinderMob enderman) {
        return new SeekHempCropGoal(enderman, ENDERMAN_SPEED, state -> state.is(ModBlocks.ENDER_HAZE_CROP));
    }

    /** A drowned's: a ripe Maui Wowie on the sea floor, at its wandering pace. */
    public static SeekHempCropGoal drowned(PathfinderMob drowned) {
        return new SeekHempCropGoal(drowned, DROWNED_SPEED, flat(SeekHempCropGoal::ripeMauiWowie), GROWN, true, false);
    }

    /** Whether {@code mob} is one that hemp draws: {@code #hempdustry:drawn_to_hemp}, vanilla's creeper by default. */
    public static boolean drawnToHemp(Entity mob) {
        return mob.is(ModTags.EntityTypes.DRAWN_TO_HEMP);
    }

    /** Cover that pulls as a grown plant whatever its stage: the endermen's and the drowned's. */
    private static ToIntFunction<BlockState> flat(Predicate<BlockState> cover) {
        return state -> cover.test(state) ? GROWN : NO_PULL;
    }

    /**
     * How hard a block of a hemp plant pulls a creeper, by the plant's stage: {@link #RIPE} at its last
     * age, {@link #FLOWERING} from the age it starts flowering, {@link #GROWN} before, and {@link #NO_PULL}
     * for anything outside {@code #hempdustry:hemp_crops}. Every block of a plant answers its root's age,
     * so the glance reads the plant whichever block it lands on. Whether it is tall enough to hide in is
     * the column's own test.
     */
    public static int hempPull(BlockState state) {
        if (!state.is(ModTags.Blocks.HEMP_CROPS)) {
            return NO_PULL;
        }
        if (!(state.getBlock() instanceof CropBlock crop)) {
            return GROWN;
        }
        if (crop.isMaxAge(state)) {
            return RIPE;
        }
        return crop.getAge(state) >= GrowLight.FLOWERING_AGE ? FLOWERING : GROWN;
    }

    /** Any block of a ripe Maui Wowie: every segment mirrors its root's age. */
    public static boolean ripeMauiWowie(BlockState state) {
        return state.is(ModBlocks.MAUI_WOWIE_CROP) && state.getValue(SativaCropBlock.AGE) == SativaCropBlock.MAX_AGE;
    }

    /**
     * Whether this creeper is one of the half that notice a grown plant, before it flowers. Read off the
     * UUID, which already survives saving, so the answer never changes for a given creeper.
     */
    public static boolean caresAboutHemp(Entity creeper) {
        return pullNeeded(creeper) == GROWN;
    }

    /**
     * The least pull a plant needs for this mob to notice it, off the two low bits of its UUID: half the
     * mobs {@link #GROWN} (the half that always cared), a quarter {@link #FLOWERING}, a quarter
     * {@link #RIPE}. So half care for a grown plant, three quarters for a flowering one, all for a ripe one.
     */
    public static int pullNeeded(Entity mob) {
        long bits = mob.getUUID().getLeastSignificantBits() & 3L;
        return (bits & 1L) == 0L ? GROWN : bits == 1L ? FLOWERING : RIPE;
    }

    /**
     * The config's off switch lives here rather than at the mixin, so
     * {@code /hempdustry reload} takes effect on creepers that already exist. Goals are built in
     * the entity's constructor; a check at the mixin would freeze the setting at spawn time. The tag
     * is asked here too, and on every tick of the walk ({@link #canContinueToUse}), so a mob a {@code /reload}
     * takes out of it stops at once. A mob with a target never starts: a modded mob whose attack sits below
     * this goal's priority would otherwise lurk in the crop with a player in its sights.
     */
    @Override
    public boolean canUse() {
        if (!ModGameRules.Switch.MOBS_DRAWN_TO_HEMP.on(mob.level()) || tagged && !drawnToHemp(mob)
                || mob.getTarget() != null
                || pullNeeded(mob) > strongestPull || mob.level().getGameTime() < restingUntil) {
            return false;
        }
        setInterval(prowlsLeft > 0 ? PROWL_CHANCE : NOTICE_CHANCE);
        return super.canUse();
    }

    /**
     * Walking, then lurking where the walk ended, until the mob finds a target, leaves the tag or the switch goes
     * off. A creeper's attack goal at priority 4 takes the movement control from this one anyway; a mob in the tag
     * whose attack sits lower (a modded one at 5 or more) is stopped here instead.
     */
    @Override
    public boolean canContinueToUse() {
        if (!ModGameRules.Switch.MOBS_DRAWN_TO_HEMP.on(mob.level()) || mob.hasControllingPassenger()
                || tagged && !drawnToHemp(mob) || mob.getTarget() != null) {
            return false;
        }
        if (!mob.getNavigation().isDone()) {
            return true;
        }
        long now = mob.level().getGameTime();
        if (lurkingUntil == 0L) {
            lurkingUntil = now + mob.getRandom().nextIntBetweenInclusive(MIN_LURK, MAX_LURK);
        }
        return now < lurkingUntil;
    }

    @Override
    public void stop() {
        super.stop();
        lurkingUntil = 0L;
    }

    @Override
    protected @Nullable Vec3 getPosition() {
        boolean prowling = prowlsLeft > 0;
        BlockPos plant = prowling
                ? findCover(mob, PROWL_RANGE, PROWL_SAMPLES, pull, seaFloor, pullNeeded(mob))
                : findCover(mob, NOTICE_RANGE, NOTICE_SAMPLES, pull, seaFloor, pullNeeded(mob));
        if (plant == null) {
            // A prowl that finds no more field has walked out of it, or been led out; either way
            // the visit is over.
            if (prowling) {
                rest();
            }
            return null;
        }
        return Vec3.atBottomCenterOf(plant);
    }

    @Override
    public void start() {
        super.start();
        RandomSource random = mob.getRandom();
        if (prowlsLeft == 0) {
            prowlsLeft = random.nextIntBetweenInclusive(MIN_PROWLS, MAX_PROWLS);
        } else if (--prowlsLeft == 0) {
            rest();
        }
    }

    private void rest() {
        prowlsLeft = 0;
        restingUntil = mob.level().getGameTime() + mob.getRandom().nextIntBetweenInclusive(MIN_REST, MAX_REST);
    }

    /**
     * A creeper's glance: any hemp plant, pulling by its stage ({@link #hempPull}), counted only where it
     * pulls as hard as this creeper needs ({@link #pullNeeded}).
     */
    public static @Nullable BlockPos findCover(PathfinderMob creeper, int range, int samples) {
        return findCover(creeper, range, samples, SeekHempCropGoal::hempPull, false, pullNeeded(creeper));
    }

    /**
     * Looks at {@code samples} random columns within {@code range} of the creeper and returns the
     * root of the first grown, open-sky plant {@code cover} accepts that no other mob of its kind is
     * standing by, or null. Every plant {@code cover} accepts counts, whatever its stage: how hard a plant
     * must pull for this mob ({@link #pullNeeded}) is asked only by the glance above, and whether the mob
     * walks at all by its goal ({@link #canUse}).
     */
    public static @Nullable BlockPos findCover(PathfinderMob creeper, int range, int samples,
                                             Predicate<BlockState> cover) {
        return findCover(creeper, range, samples, flat(cover), false, GROWN);
    }

    /**
     * As above; with {@code seaFloor}, the plant is the one standing on the sea floor of each column
     * ({@code OCEAN_FLOOR}), counted upwards, and in a swimmer's reach.
     */
    public static @Nullable BlockPos findCover(PathfinderMob creeper, int range, int samples,
                                             Predicate<BlockState> cover, boolean seaFloor) {
        return findCover(creeper, range, samples, flat(cover), seaFloor, GROWN);
    }

    /**
     * The glance itself: a plant counts where its pull, read at the root, is at least {@code needed}, and
     * a ripe one is taken only once two mobs stand by it.
     */
    private static @Nullable BlockPos findCover(PathfinderMob creeper, int range, int samples,
                                              ToIntFunction<BlockState> pull, boolean seaFloor, int needed) {
        Level world = creeper.level();
        RandomSource random = creeper.getRandom();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < samples; i++) {
            int x = creeper.getBlockX() + random.nextIntBetweenInclusive(-range, range);
            int z = creeper.getBlockZ() + random.nextIntBetweenInclusive(-range, range);
            // Never load a chunk to answer a wandering mob.
            if (!world.isLoaded(pos.set(x, creeper.getBlockY(), z))) {
                continue;
            }
            int height = 0;
            BlockPos root;
            if (seaFloor) {
                root = pos.setY(world.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z)).immutable();
                while (pull.applyAsInt(world.getBlockState(pos)) > NO_PULL) {
                    pos.move(0, 1, 0);
                    height++;
                }
            } else {
                pos.setY(world.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
                while (pull.applyAsInt(world.getBlockState(pos)) > NO_PULL) {
                    pos.move(0, -1, 0);
                    height++;
                }
                root = pos.above();
            }
            if (height < 2) {
                continue;
            }
            int stage = pull.applyAsInt(world.getBlockState(root));
            if (stage < needed
                    || Math.abs(root.getY() - creeper.getBlockY()) > (seaFloor ? SWIMMER_Y_DIFFERENCE : MAX_Y_DIFFERENCE)
                    || isTaken(world, root, creeper, stage == RIPE ? RIPE_FIELD_HOLDS : 1)) {
                continue;
            }
            return root;
        }
        return null;
    }

    /** Whether {@code holds} mobs of {@code self}'s kind besides it already stand by the plant at {@code root}. */
    private static boolean isTaken(Level world, BlockPos root, Entity self, int holds) {
        return world.getEntitiesOfClass(self.getClass(), new AABB(root).inflate(TAKEN_RADIUS),
                other -> other != self).size() >= holds;
    }
}
