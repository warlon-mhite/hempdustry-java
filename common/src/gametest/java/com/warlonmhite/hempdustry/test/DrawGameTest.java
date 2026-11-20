package com.warlonmhite.hempdustry.test;

import com.warlonmhite.hempdustry.component.ModComponents;
import com.warlonmhite.hempdustry.item.ModItems;
import com.warlonmhite.hempdustry.item.custom.DeviceType;
import com.warlonmhite.hempdustry.item.custom.Draw;
import com.warlonmhite.hempdustry.item.custom.SmokeContents;
import com.warlonmhite.hempdustry.strain.ModStrains;
import com.warlonmhite.hempdustry.strain.Strain;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.UseEffects;

/**
 * Every smokeable is drawn, not clicked: the hit lands at the end of the hold, and letting go spends
 * nothing. The spliff draws 16 ticks, the pipe 24, the bong 30 and the vaporizer 32.
 *
 * <p>Three quiet failures. A draw that hit on the click as well as at the end would be two hits for
 * one — the generous direction nobody reports. A release that still spent a charge (or a spliff)
 * would make the cancel a trap that looks like lag. And a smokeable whose {@code use_effects} were
 * not vanilla's default would let a player sprint through a draw that eating stops.
 */
public final class DrawGameTest {

    public static void everySmokeableIsDrawnNotClicked(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Map<Item, Integer> draws = Map.of(
                ModItems.SPLIFF, Draw.SPLIFF_TICKS,
                ModItems.devices().get(DeviceType.PIPE), 24,
                ModItems.devices().get(DeviceType.BONG), 30,
                ModItems.devices().get(DeviceType.VAPORIZER), 32);
        for (Map.Entry<Item, Integer> draw : draws.entrySet()) {
            Item item = draw.getKey();
            String name = BuiltInRegistries.ITEM.getKey(item).toString();
            // A fresh player each: a finished draw starts the cooldown every smokeable shares.
            ServerPlayer player = Players.survival(context);
            ItemStack stack = packed(world, item);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);

            player.gameMode.useItem(player, world, stack, InteractionHand.MAIN_HAND);
            context.assertTrue(player.isUsingItem(), "clicking a packed " + name + " did not start a draw");
            context.assertValueEqual(draw.getValue(), player.getUseItemRemainingTicks(), name + "'s draw in ticks");
            context.assertTrue(player.getUseItem().getUseAnimation() == ItemUseAnimation.NONE,
                    name + " hands vanilla a use animation: the draw's pose is the mod's own");
            // Every item carries use_effects on its prototype; what it holds must be vanilla's default.
            context.assertValueEqual(UseEffects.DEFAULT,
                    player.getUseItem().getOrDefault(DataComponents.USE_EFFECTS, UseEffects.DEFAULT), name + "'s use effects (vanilla's: no sprint, a fifth of the speed)");
            context.assertTrue(unspent(player, stack), "the click alone took a hit from " + name);

            player.releaseUsingItem();
            context.assertTrue(unspent(player, stack), "letting go of " + name + " early spent it");
            context.assertTrue(!player.getCooldowns().isOnCooldown(stack), "letting go of " + name + " early started a cooldown");

            Players.smoke(player, InteractionHand.MAIN_HAND);
            ItemStack after = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (item == ModItems.SPLIFF) {
                context.assertValueEqual(1, after.getCount(), "spliffs left after one finished draw of two");
            } else {
                context.assertValueEqual(charges(item) - 1, after.getOrDefault(ModComponents.CHARGES, 0),
                        name + "'s charges after one finished draw");
            }
            context.assertTrue(player.getCooldowns().isOnCooldown(after), "a finished draw of " + name + " started no cooldown");
            context.assertTrue(!player.isUsingItem(), "the player still draws " + name + " after the hit");
        }
        context.succeed();
    }

    private static boolean unspent(ServerPlayer player, ItemStack stack) {
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        return held.getItem() == ModItems.SPLIFF
                ? held.getCount() == 2
                : held.getOrDefault(ModComponents.CHARGES, 0) == charges(held.getItem()) && held.getDamageValue() == 0;
    }

    private static int charges(Item item) {
        for (DeviceType device : DeviceType.values()) {
            if (ModItems.devices().get(device) == item) {
                return device.builtInBowl().hits();
            }
        }
        return 0;
    }

    /** Two of a spliff, so one smoked leaves one to read; a full bowl of a device. */
    private static ItemStack packed(ServerLevel world, Item item) {
        ItemStack stack = new ItemStack(item, item == ModItems.SPLIFF ? 2 : 1);
        stack.set(ModComponents.SMOKE_CONTENTS, SmokeContents.of(world.registryAccess()
                .lookupOrThrow(Strain.REGISTRY_KEY).getOrThrow(ModStrains.INDICA), 1));
        if (item != ModItems.SPLIFF) {
            stack.set(ModComponents.CHARGES, charges(item));
        }
        return stack;
    }
}
