package com.warlonmhite.hempdustry.test;

import com.warlonmhite.hempdustry.network.PlayerGesture;
import com.warlonmhite.hempdustry.block.ModBlocks;
import com.warlonmhite.hempdustry.component.ModComponents;
import com.warlonmhite.hempdustry.item.ModItems;
import com.warlonmhite.hempdustry.item.custom.DeviceType;
import com.warlonmhite.hempdustry.item.custom.SmokeContents;
import com.warlonmhite.hempdustry.strain.ModStrains;
import com.warlonmhite.hempdustry.strain.Strain;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Another mod's machine works ours the way a player would: it right-clicks each machine that opens
 * a screen, the way a deployer does, and the server survives it; it smokes, and the smoke stays its own.
 *
 * <p>A machine's hand is the loader's fake player: a {@code ServerPlayer} with no network behind it.
 * Opening a menu for it once asked that missing network what the client could receive, and threw on
 * the server thread. The deployer clicks again every time its chunk loads, so the world crashed for
 * whoever walked back to it.
 */
public final class FakePlayerUseGameTest {

    private static final List<Block> MACHINES = List.of(
            ModBlocks.HEMP_PRESS, ModBlocks.INFUSER, ModBlocks.DECARBOXYLATOR);

    public static void aFakePlayerCanClickEveryMachine(GameTestHelper context) {
        ServerPlayer hand = TestPlatform.INSTANCE.fakePlayer(context.getLevel());
        for (int i = 0; i < MACHINES.size(); i++) {
            Block machine = MACHINES.get(i);
            BlockPos pos = new BlockPos(1 + 2 * i, 1, 1);
            context.setBlock(pos, machine);
            BlockPos absolute = context.absolutePos(pos);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
            InteractionResult result = context.getLevel().getBlockState(absolute)
                    .useWithoutItem(context.getLevel(), hand, hit);
            context.assertTrue(result.consumesAction(),
                    machine + " did not take a fake player's click: " + result);
        }
        context.succeed();
    }
    /**
     * A player gesture (a cough, a sniff at a jar) goes to everyone tracking the player and to the player, but a
     * fake player's connection has no network channel under it, and NeoForge 21.11 throws on asking such a
     * connection whether it took the gesture's channel. A machine that smokes coughs too.
     */
    public static void aFakePlayersGestureGoesNowhere(GameTestHelper context) {
        ServerPlayer hand = TestPlatform.INSTANCE.fakePlayer(context.getLevel());
        for (PlayerGesture.Kind kind : PlayerGesture.Kind.values()) {
            PlayerGesture.send(hand, kind, true);
        }
        context.succeed();
    }

    /** The pipe's exhale: {@code Smoking.EXHALE_DELAY_TICKS}, with no sound delay. */
    private static final int EXHALE_TICKS = 38;

    /**
     * A machine that smokes keeps the smoke. Create's deployer holds its owner's name and UUID, so
     * a delayed part of the hit looked up by UUID -- the exhale's puff, its sound, Beldía's
     * Blindness -- found the owner instead, wherever they stood, and blinded them.
     */
    public static void aFakePlayersSmokeStaysWithIt(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer owner = Players.survival(context);
        ServerPlayer hand = TestPlatform.INSTANCE.fakePlayer(world, owner.getGameProfile());
        context.assertTrue(hand != owner && hand.getUUID().equals(owner.getUUID()),
                "the fake player does not carry its owner's UUID");

        Holder<Strain> beldia = world.registryAccess().lookupOrThrow(Strain.REGISTRY_KEY).getOrThrow(ModStrains.BELDIA);
        ItemStack pipe = new ItemStack(ModItems.WOODEN_PIPE);
        pipe.set(ModComponents.SMOKE_CONTENTS, SmokeContents.of(beldia, 1));
        pipe.set(ModComponents.CHARGES, DeviceType.PIPE.builtInBowl().hits());
        hand.setItemInHand(InteractionHand.MAIN_HAND, pipe);
        hand.getCooldowns().removeCooldown(BuiltInRegistries.ITEM.getKey(ModItems.WOODEN_PIPE));
        // The deployer's own route: the item's use, straight on its fake player, and when that began a
        // draw, its end at once (Create's DeployerHandler finishes any use it started, then lets go).
        InteractionResult result = pipe.getItem().use(world, hand, InteractionHand.MAIN_HAND);
        context.assertTrue(result.consumesAction(), "the fake player could not smoke: " + result);
        context.assertTrue(hand.isUsingItem(), "the pipe began no draw");
        Players.finishDraw(hand);
        // No list holds it, so nothing could find it after a wait: what the exhale holds back lands now.
        context.assertTrue(hand.hasEffect(MobEffects.BLINDNESS), "the fake player's exhale did not land on itself");

        context.runAtTickTime(EXHALE_TICKS + 5, () -> {
            context.assertTrue(!owner.hasEffect(MobEffects.BLINDNESS),
                    "the fake player's exhale blinded the player whose UUID it carries");
            context.succeed();
        });
    }
}
