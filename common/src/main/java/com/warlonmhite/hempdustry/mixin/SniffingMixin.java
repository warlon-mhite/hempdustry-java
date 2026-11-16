package com.warlonmhite.hempdustry.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.warlonmhite.hempdustry.effect.HushEffect;
import com.warlonmhite.hempdustry.effect.ModEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.warden.Sniffing;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A Warden smells a Hushed player from twice as far: Hush silences your body, but not the smoke on it.
 *
 * <p>When a Warden finishes sniffing, vanilla's {@code Sniffing#stop} grows its anger at the nearest
 * target it can attack if that target is {@code closerThan(target, 6, 20)}: within 6 blocks flat and 20
 * up or down. For a target under Hush the flat distance is {@link HushEffect#SNIFF_DISTANCE}; the height
 * stays vanilla's. The test sits in a lambda, which the wrap reaches by its call, so nothing of
 * vanilla's is replaced.
 */
@Mixin(Sniffing.class)
public abstract class SniffingMixin {

    @WrapOperation(method = "*", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/monster/warden/Warden;closerThan(Lnet/minecraft/world/entity/Entity;DD)Z"))
    private static boolean hempdustry$smellTheSmoke(Warden warden, Entity target, double horizontal, double vertical,
                                                    Operation<Boolean> original) {
        boolean hushed = target instanceof LivingEntity living && living.hasEffect(ModEffects.HUSH);
        return original.call(warden, target, hushed ? Math.max(horizontal, HushEffect.SNIFF_DISTANCE) : horizontal,
                vertical);
    }
}
