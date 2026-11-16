package com.warlonmhite.hempdustry.mixin;

import com.warlonmhite.hempdustry.entity.ai.SeekHempCropGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drowned and Maui Wowie: they wander into a ripe patch on the sea floor now and then, the creepers' wander
 * ({@link SeekHempCropGoal#drowned}) reading the sea floor instead of the open sky. Priority 7, beside their own
 * wander: under going to water, the attack, the beach and swimming up at night (1-6), so a drowned with something
 * to do does it. An {@code @Inject}, as on the creeper and the enderman: nothing of vanilla's is replaced. The
 * class extends {@code Zombie} only so {@code goalSelector} resolves.
 */
@Mixin(Drowned.class)
public abstract class DrownedMixin extends Zombie {

    protected DrownedMixin(EntityType<? extends Zombie> entityType, Level world) {
        super(entityType, world);
    }

    @Inject(method = "addBehaviourGoals", at = @At("TAIL"))
    private void hempdustry$drawnToTheReef(CallbackInfo ci) {
        this.goalSelector.addGoal(7, SeekHempCropGoal.drowned((Drowned) (Object) this));
    }
}
