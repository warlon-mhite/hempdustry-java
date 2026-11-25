package com.warlonmhite.hempdustry.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.warlonmhite.hempdustry.entity.ai.StashSmell;
import com.warlonmhite.hempdustry.item.custom.StashJarItem;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two things every menu does for the mod:
 *
 * <ul>
 *   <li>The bundle menu's choice, sent in vanilla's packet, reaches a Stash Jar too: the server sets it on
 *       {@code hempdustry:stash_contents}, where {@code BundleItem.toggleSelectedItem} only knows
 *       {@code bundle_contents}.</li>
 *   <li>A click that takes buds out of a placed block's slots lets that block's smell out ({@link StashSmell}): every
 *       click a client sends comes through {@code clicked}, whatever kind it is, so the buds in the block's slots are
 *       read before and after it.</li>
 * </ul>
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Shadow
    @Final
    public NonNullList<Slot> slots;

    @Inject(method = "setSelectedBundleItemIndex", at = @At("HEAD"))
    private void hempdustry$selectInStashJar(int slotIndex, int selectedItemIndex, CallbackInfo ci) {
        if (slotIndex >= 0 && slotIndex < slots.size()) {
            ItemStack stack = slots.get(slotIndex).getItem();
            if (stack.getItem() instanceof StashJarItem) {
                StashJarItem.toggleSelected(stack, selectedItemIndex);
            }
        }
    }

    @WrapMethod(method = "clicked")
    private void hempdustry$smellWhatATakeOutLetsOut(int slotIndex, int button, ContainerInput input, Player player,
                                                     Operation<Void> original) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        ItemStack[] before = StashSmell.budsInBlockSlots(menu, player);
        original.call(slotIndex, button, input, player);
        StashSmell.afterClick(menu, player, before);
    }
}
