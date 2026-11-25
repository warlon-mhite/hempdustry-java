package com.warlonmhite.hempdustry.mixin;

import com.warlonmhite.hempdustry.entity.ai.StashSmell;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Where a player's smell is kept between two creepers' checks ({@link StashSmell}): when the jars they carry
 * close again, the last count of their loose buds, and the menu they last opened by using a block, with that
 * block. In memory only, never saved: a rejoin closes the jars.
 */
@Mixin(Player.class)
public abstract class PlayerSmellMixin implements StashSmell.Carrier {
    @Unique
    private long hempdustry$jarOpenUntil = Long.MIN_VALUE;
    @Unique
    private long hempdustry$countedAt = Long.MIN_VALUE;
    @Unique
    private int hempdustry$counted;
    @Unique
    private @Nullable AbstractContainerMenu hempdustry$blockMenu;
    @Unique
    private BlockPos hempdustry$blockMenuAt = BlockPos.ZERO;

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

    @Override
    public @Nullable AbstractContainerMenu hempdustry$blockMenu() {
        return hempdustry$blockMenu;
    }

    @Override
    public BlockPos hempdustry$blockMenuAt() {
        return hempdustry$blockMenuAt;
    }

    @Override
    public void hempdustry$setBlockMenu(AbstractContainerMenu menu, BlockPos pos) {
        hempdustry$blockMenu = menu;
        hempdustry$blockMenuAt = pos;
    }
}
