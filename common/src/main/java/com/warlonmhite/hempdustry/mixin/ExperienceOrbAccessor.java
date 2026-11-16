package com.warlonmhite.hempdustry.mixin;

import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * An orb's count: how many orbs of its value it stands for once merged. Echo OG eats them one at a time,
 * as {@code ExperienceOrb#playerTouch} gives a player one a tick, and vanilla keeps the count private.
 */
@Mixin(ExperienceOrb.class)
public interface ExperienceOrbAccessor {
    @Accessor("count")
    int hempdustry$getCount();

    @Accessor("count")
    void hempdustry$setCount(int count);
}
