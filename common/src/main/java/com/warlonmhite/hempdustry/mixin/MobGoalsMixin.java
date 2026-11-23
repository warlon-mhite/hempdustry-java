package com.warlonmhite.hempdustry.mixin;

import com.warlonmhite.hempdustry.entity.ai.SeekHempCropGoal;
import com.warlonmhite.hempdustry.entity.ai.SeekStashJarGoal;
import com.warlonmhite.hempdustry.entity.ai.StashSmell;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the mobs of {@code #hempdustry:drawn_to_hemp} a reason to walk into a hemp field
 * ({@link SeekHempCropGoal}), and those of {@code #hempdustry:drawn_to_stash} one to come to a Stash Jar a hand has
 * just opened ({@link SeekStashJarGoal}). Both tags hold vanilla's creeper.
 *
 * <p>Why this has to be a mixin: <b>there is no event for adding a goal to a mob.</b> {@code Mob#goalSelector} is
 * protected, and the moment vanilla builds the list is the mob's construction.
 *
 * <h2>Why at the end of {@code Mob}'s constructor, not in {@code Creeper#registerGoals}</h2>
 *
 * The constructor calls {@code registerGoals} on a server and returns; by then every subclass has added its own
 * goals, whatever it overrides. A mob that overrides {@code registerGoals} without calling its parent's (a modded
 * creeper may) never ran an injection into the creeper's method, and a tag listing it would have done nothing. Here
 * any listed mob that walks by paths gets the goals, and a creeper's list ends exactly as it did: vanilla's goals,
 * then these two.
 *
 * <p>Appending at the end is deliberate: a {@code GoalSelector} iterates in insertion order when two goals share a
 * priority, so vanilla's {@code WaterAvoidingRandomStrollGoal} on a creeper — also priority 5 — gets first refusal on
 * every tick. That is what keeps this a drift towards the field rather than a beeline.
 *
 * <p>The tag is asked here, when the mob is made, and again by each goal every time it could start, so a mob taken
 * out of a tag by a {@code /reload} stops at once; one put in by a {@code /reload} takes the walk once it is made
 * again (a chunk loaded, a new spawn). {@code world.creepersSeekHemp} stays the one switch over both.
 */
@Mixin(Mob.class)
public abstract class MobGoalsMixin {

    @Shadow
    @Final
    protected GoalSelector goalSelector;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void hempdustry$drawnToHemp(EntityType<? extends Mob> type, Level level, CallbackInfo ci) {
        if (!(level instanceof ServerLevel) || !((Object) this instanceof PathfinderMob mob)) {
            return;
        }
        if (SeekHempCropGoal.drawnToHemp(mob)) {
            this.goalSelector.addGoal(5, new SeekHempCropGoal(mob));
        }
        if (StashSmell.drawnToStash(mob)) {
            this.goalSelector.addGoal(5, new SeekStashJarGoal(mob));
        }
    }
}
