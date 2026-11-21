package com.warlonmhite.hempdustry.mixin;

import com.warlonmhite.hempdustry.entity.ai.StashSmell;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Where a player's smell is kept between two creepers' checks ({@link StashSmell}): when the jars they carry
 * close again, and the last count of their loose buds. In memory only, never saved: a rejoin closes the jars.
 */
@Mixin(Player.class)
public abstract class PlayerSmellMixin implements StashSmell.Carrier {
    @Unique
    private long hempdustry$jarOpenUntil = Long.MIN_VALUE;
    @Unique
    private long hempdustry$countedAt = Long.MIN_VALUE;
    @Unique
    private int hempdustry$counted;

    @Override
    public long hempdustry$carriedJarOpenUntil() {
        return hempdustry$jarOpenUntil;
    }

    @Override
    public void hempdustry$setCarriedJarOpenUntil(long gameTime) {
        hempdustry$jarOpenUntil = gameTime;
    }

    @Override
    public long hempdustry$budsCountedAt() {
        return hempdustry$countedAt;
    }

    @Override
    public int hempdustry$budsCounted() {
        return hempdustry$counted;
    }

    @Override
    public void hempdustry$setBudsCounted(long gameTime, int count) {
        hempdustry$countedAt = gameTime;
        hempdustry$counted = count;
    }
}
