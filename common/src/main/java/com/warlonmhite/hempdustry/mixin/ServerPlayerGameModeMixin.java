package com.warlonmhite.hempdustry.mixin;

import com.warlonmhite.hempdustry.entity.ai.StashSmell;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Notes which block a player's use opened a menu on, so buds a hand takes out of that menu later are taken out of
 * that block ({@link StashSmell#usedBlock}). Every right-click on a block a client sends comes through
 * {@code useItemOn}, whichever block or mod answers it.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void hempdustry$noteTheBlockAMenuIsOpenOn(ServerPlayer player, Level level, ItemStack stack,
                                                     InteractionHand hand, BlockHitResult hit,
                                                     CallbackInfoReturnable<InteractionResult> cir) {
        StashSmell.usedBlock(player, hit.getBlockPos());
    }
}
