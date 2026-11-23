package com.warlonmhite.hempdustry.entity.ai;

import com.warlonmhite.hempdustry.config.ModGameRules;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Walks a creeper to a Stash Jar a hand has just opened, and keeps it there while the jar smells.
 *
 * <h2>The hemp wander, for a jar, with fewer brakes</h2>
 *
 * {@link SeekHempCropGoal} hides a creeper in a field now and then, and is built so a player can never point
 * at it. This one is the opposite on purpose: the smell lasts only {@link StashSmell#OPEN_TICKS} after a
 * player opened the jar, and the puff told them they let it out, so the creeper is meant to come:
 *
 * <ul>
 *   <li>every creeper in reach cares, not half;</li>
 *   <li>it checks every second ({@link #CHECK_TICKS}), not one glance in twelve seconds: a stack draws a
 *       creeper within seconds, a lone bud about one creeper in three over the two minutes
 *       ({@link StashSmell#noticeOpenedJar});</li>
 *   <li>no limit on how many come to one jar;</li>
 *   <li>it stays by the jar until the smell fades, then is an ordinary creeper again, with no rest;</li>
 *   <li>any height in reach, wherever it finds a path: a cellar jar draws creepers up out of the caves.</li>
 * </ul>
 *
 * <p>No open-sky rule: the creeper walks toward the jar as far as it can find a path and waits there, at the
 * door or under the window.
 *
 * <h2>What is kept</h2>
 *
 * Priority 5, beside vanilla's wandering, so a creeper with a target always attacks it first: the attack
 * goal at 4 takes the movement from this one. It attacks only a player it sees, as ever. It walks at its
 * wandering pace and does not turn to stare at the jar. Under {@code world.creepersSeekHemp}, read at each
 * check, and only for a mob of {@code #hempdustry:drawn_to_stash} (vanilla's creeper by default), asked at each
 * check too.
 *
 * <p>Nothing is saved: the smell is the jar's, in memory, and a chunk reloaded is a jar shut again.
 */
public class SeekStashJarGoal extends Goal {

    /** {@code WaterAvoidingRandomStrollGoal}'s speed on a creeper: it comes at its wandering pace. */
    private static final double SPEED = 0.8;
    /** A creeper asks whether it smells an opened jar once a second. */
    public static final int CHECK_TICKS = 20;
    /** Within this many blocks of the jar it stands; further, it walks back. */
    private static final double BY_THE_JAR = 2.5;
    /** How often a walking creeper asks for a new path, in this goal's ticks: a far jar is out of one path's reach. */
    private static final int REPATH_TICKS = 10;

    private final PathfinderMob mob;
    private @Nullable BlockPos jar;
    private long nextCheck;
    private int repathIn;

    public SeekStashJarGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    /**
     * Once a second, any opened jar in reach may draw it, at odds that grow with the buds inside
     * ({@link StashSmell#noticeOpenedJar}). A creeper with a target never gets here.
     */
    @Override
    public boolean canUse() {
        if (!ModGameRules.Switch.CREEPERS_SEEK_HEMP.on(mob.level()) || !StashSmell.drawnToStash(mob) || mob.hasControllingPassenger()
                || mob.getTarget() != null) {
            return false;
        }
        long now = mob.level().getGameTime();
        if (now < nextCheck) {
            return false;
        }
        nextCheck = now + CHECK_TICKS;
        jar = StashSmell.noticeOpenedJar(mob);
        return jar != null;
    }

    /** While the jar still smells, and only while. */
    @Override
    public boolean canContinueToUse() {
        return jar != null && ModGameRules.Switch.CREEPERS_SEEK_HEMP.on(mob.level()) && StashSmell.drawnToStash(mob)
                && !mob.hasControllingPassenger() && StashSmell.smellsOpen(mob.level(), jar);
    }

    @Override
    public void start() {
        repathIn = 0;
    }

    @Override
    public void tick() {
        if (jar == null) {
            return;
        }
        Vec3 at = Vec3.atBottomCenterOf(jar);
        if (mob.position().distanceToSqr(at) <= BY_THE_JAR * BY_THE_JAR) {
            mob.getNavigation().stop();
            return;
        }
        if (--repathIn <= 0 || mob.getNavigation().isDone()) {
            // As far as a path goes: a jar behind a wall is waited for at the wall.
            mob.getNavigation().moveTo(at.x, at.y, at.z, SPEED);
            repathIn = REPATH_TICKS;
        }
    }

    @Override
    public void stop() {
        jar = null;
        mob.getNavigation().stop();
    }

    /** The jar this creeper is drawn to, or null. */
    public @Nullable BlockPos jar() {
        return jar;
    }

    /** Lets the next {@link #canUse} check at once, for a test that does not wait a second per check. */
    public void checkNow() {
        nextCheck = 0L;
    }
}
