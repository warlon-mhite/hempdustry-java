package com.warlonmhite.hempdustry.test;

import com.warlonmhite.hempdustry.component.ModComponents;
import com.warlonmhite.hempdustry.item.ModItems;
import com.warlonmhite.hempdustry.item.custom.DeviceType;
import com.warlonmhite.hempdustry.item.custom.Smoking;
import com.warlonmhite.hempdustry.item.custom.SmokeContents;
import com.warlonmhite.hempdustry.strain.ModStrains;
import com.warlonmhite.hempdustry.strain.Strain;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Nothing burns with the smoker's head under water, as water puts out a campfire: a spliff, a pipe, a bong and a
 * vaporizer all go out before they are lit, and the hit costs nothing -- no effect, no charge, no spliff, no
 * cooldown, no draw begun, no fall-through to the other hand; a draw begun above the water and finished under it
 * goes out too. Head above the water, in the same pool, each lights as ever, and a held use key hisses once.
 */
public final class SmokeUnderWaterGameTest {

    public static void everySmokeGoesOutUnderWater(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Holder<Strain> haze = world.registryAccess().lookupOrThrow(Strain.REGISTRY_KEY).getOrThrow(ModStrains.SATIVA);
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                context.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                for (int y = 2; y <= 4; y++) {
                    context.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                }
            }
        }
        Vec3 under = Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(4, 2, 4)));
        Vec3 wading = Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(4, 4, 4)));

        int drawn = 0;
        for (Item item : List.of(ModItems.SPLIFF, ModItems.WOODEN_PIPE, ModItems.BONG, ModItems.VAPORIZER)) {
            ServerPlayer smoker = Players.survival(context);
            smoker.setPos(under);
            ItemStack stack = loaded(item, haze);
            int charges = stack.getOrDefault(ModComponents.CHARGES, 0);
            smoker.setItemInHand(InteractionHand.MAIN_HAND, stack);
            InteractionResult result = smoker.gameMode.useItem(smoker, world, stack, InteractionHand.MAIN_HAND);
            String name = BuiltInRegistries.ITEM.getKey(item).toString();
            context.assertTrue(result.consumesAction(), name + " passed a try under water on to the other hand: " + result);
            context.assertTrue(!smoker.hasEffect(MobEffects.SPEED), name + " was smoked with the head under water");
            context.assertTrue(!smoker.isUsingItem(), name + " began a draw with the head under water");
            context.assertTrue(!smoker.getCooldowns().isOnCooldown(stack), name + " went on cooldown under water");
            context.assertValueEqual(1, stack.getCount(), name + "'s count after a hit under water");
            context.assertValueEqual(charges, stack.getOrDefault(ModComponents.CHARGES, 0), name + "'s charges after a hit under water");

            // A draw begun with the head above the water and finished under it goes out too: the bowl is kept.
            if (draws(item)) {
                smoker.setPos(wading);
                smoker.gameMode.useItem(smoker, world, stack, InteractionHand.MAIN_HAND);
                context.assertTrue(smoker.isUsingItem(), name + " began no draw with the head above the water");
                smoker.setPos(under);
                smoker.getUseItem().finishUsingItem(world, smoker);
                smoker.stopUsingItem();
                context.assertTrue(!smoker.hasEffect(MobEffects.SPEED), name + " was smoked by a draw finished under water");
                context.assertValueEqual(charges, stack.getOrDefault(ModComponents.CHARGES, 0),
                        name + "'s charges after a draw finished under water");
                drawn++;
            }

            // Head out of the water, feet still in it: it lights.
            smoker.setPos(wading);
            smoker.gameMode.useItem(smoker, world, stack, InteractionHand.MAIN_HAND);
            if (smoker.isUsingItem()) {
                smoker.getUseItem().finishUsingItem(world, smoker);
                smoker.stopUsingItem();
            }
            context.assertTrue(smoker.hasEffect(MobEffects.SPEED), name + " did not light with the head above the water");
        }
        context.assertValueEqual(4, drawn, "smokeables whose draw was finished under water");

        // One hiss a press: a held use key tries again every 4 ticks, and only a pause makes a new press.
        ServerPlayer diver = Players.survival(context);
        context.assertTrue(Smoking.hisses(diver, 100), "the first try under water did not hiss");
        context.assertTrue(!Smoking.hisses(diver, 104) && !Smoking.hisses(diver, 108), "a held use key hissed again every 4 ticks");
        context.assertTrue(Smoking.hisses(diver, 130), "a new press after a pause did not hiss");
        context.succeed();
    }

    /** Whether {@code item} is smoked by holding a draw, the hit landing when the draw is done: every smokeable, since 2.2. */
    private static boolean draws(Item item) {
        return Smoking.isSmokeable(item);
    }

    private static ItemStack loaded(Item item, Holder<Strain> strain) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModComponents.SMOKE_CONTENTS, SmokeContents.of(strain, 1));
        for (DeviceType device : DeviceType.values()) {
            if (ModItems.devices().get(device) == item) {
                stack.set(ModComponents.CHARGES, device.builtInBowl().hits());
            }
        }
        return stack;
    }
}
