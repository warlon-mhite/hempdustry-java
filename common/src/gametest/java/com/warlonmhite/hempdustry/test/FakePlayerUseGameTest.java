package com.warlonmhite.hempdustry.test;

import com.warlonmhite.hempdustry.block.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Another mod's machine right-clicks each machine that opens a screen, the way a deployer does, and
 * the server survives it.
 *
 * <p>A machine's hand is the loader's fake player: a {@code ServerPlayer} with no network behind it.
 * Opening a menu for it once asked that missing network what the client could receive, and threw on
 * the server thread. The deployer clicks again every time its chunk loads, so the world crashed for
 * whoever walked back to it.
 */
public final class FakePlayerUseGameTest {

    private static final List<Block> MACHINES = List.of(
            ModBlocks.HEMP_PRESS, ModBlocks.INFUSER, ModBlocks.DECARBOXYLATOR);

    @GameTest(template = HempdustryGameTests.EMPTY)
    public void aFakePlayerCanClickEveryMachine(GameTestHelper context) {
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
}
