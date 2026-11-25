package com.warlonmhite.hempdustry.test;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import com.warlonmhite.hempdustry.component.StashContents;
import com.warlonmhite.hempdustry.block.entity.custom.StashJarBlockEntity;
import com.warlonmhite.hempdustry.block.ModBlocks;
import net.minecraft.server.level.ClientInformation;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import com.warlonmhite.hempdustry.component.ModComponents;
import com.warlonmhite.hempdustry.item.ModItems;
import com.warlonmhite.hempdustry.item.custom.DeviceType;
import com.warlonmhite.hempdustry.item.custom.SmokeContents;
import com.warlonmhite.hempdustry.strain.ModStrains;
import com.warlonmhite.hempdustry.strain.Strain;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

/**
 * Another mod's machine works ours the way a player would: it smokes, and the smoke stays its own.
 *
 * <p>A machine's hand is the loader's fake player: a {@code ServerPlayer} with no network behind it,
 * and in no player list.
 */
public final class FakePlayerUseGameTest {

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

    /**
     * A fake player with no connection at all, as some mods make one, opens a Stash Jar with an empty hand and keeps
     * the stack it took. The sniff it leans into is sent to the players watching and to itself; asking whether a player
     * with no connection listens threw on Fabric, after the jar had given the stack up and before the hand took it.
     */
    public static void aPlayerWithNoConnectionOpensAJar(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ServerPlayer bare = new ServerPlayer(world.getServer(), world,
                new GameProfile(UUID.randomUUID(), "bare-fake-player"), ClientInformation.createDefault());
        context.assertTrue(bare.connection == null, "test setup: the bare player has a connection");
        BlockPos floor = new BlockPos(3, 1, 3);
        context.setBlock(floor, Blocks.STONE);
        context.setBlock(floor.above(), ModBlocks.STASH_JAR);
        StashJarBlockEntity jar = (StashJarBlockEntity) world.getBlockEntity(context.absolutePos(floor.above()));
        StashContents.Mutable stash = StashContents.EMPTY.mutable();
        stash.insert(new ItemStack(ModItems.INDICA_BUDS, 5), 128);
        jar.setContents(stash.toImmutable());
        Players.click(context, bare, floor.above(), ItemStack.EMPTY);
        context.assertTrue(bare.getMainHandItem().is(ModItems.INDICA_BUDS) && bare.getMainHandItem().getCount() == 5
                        && jar.contents().isEmpty(),
                "a player with no connection opening a jar holds " + bare.getMainHandItem() + ", the jar "
                        + jar.contents().count());
        context.succeed();
    }
}
