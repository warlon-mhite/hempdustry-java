package com.warlonmhite.hempdustry.test;

import com.warlonmhite.hempdustry.entity.ai.SeekStashJarGoal;
import com.warlonmhite.hempdustry.entity.ai.StashSmell;
import com.warlonmhite.hempdustry.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Opened storage lets the smell out, as an opened Stash Jar does: a real player's hand taking buds or schwag out of a
 * placed block's menu, whichever way they leave its slots, lets that block's smell out for two minutes, each
 * take-out starting them again, and creepers come for it at the jar's numbers. Opening, looking and putting in let
 * nothing out; nor do a hopper, a machine's hand or an ender chest, whose things are its opener's.
 */
public final class StorageSmellGameTest {

    /** The chest menu's own slots come first: 27 for a chest, then the player's 27 and their hotbar's 9. */
    private static final int CHEST_SLOTS = 27;

    /**
     * Each way a hand takes buds out of an opened chest lets its smell out: a click, a shift-click, a number key, a
     * double-click gathering, a drop with Q. Opening it and putting buds in (a shift-click, a drag) let nothing out.
     * A take-out starts the two minutes again; a creeper in reach goes for the chest; the smell fades.
     */
    public static void takingBudsOutOfAChestLetsItsSmellOut(GameTestHelper context) {
        floor(context);
        BlockPos chestPos = new BlockPos(1, 1, 1);
        context.setBlock(chestPos, Blocks.CHEST);
        BlockPos at = context.absolutePos(chestPos);
        Container chest = (Container) context.getLevel().getBlockEntity(at);
        chest.setItem(0, new ItemStack(ModItems.INDICA_BUDS, 64));
        chest.setItem(1, new ItemStack(ModItems.SATIVA_BUDS, 64));
        chest.setItem(2, new ItemStack(ModItems.SCHWAG, 10));
        ServerPlayer player = Players.survival(context);
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(at).add(0.0, 0.0, -1.5));

        AbstractContainerMenu menu = open(context, player, chestPos);
        context.assertFalse(StashSmell.smellsOpen(context.getLevel(), at), "opening a chest let its smell out");
        player.getInventory().setItem(0, new ItemStack(ModItems.INDICA_BUDS, 5));
        menu.clicked(hotbar(0), 0, ContainerInput.QUICK_MOVE, player);
        context.assertTrue(player.getInventory().getItem(0).isEmpty(), "test setup: the shift-click put nothing in");
        context.assertFalse(StashSmell.smellsOpen(context.getLevel(), at), "putting buds in a chest let its smell out");
        menu.setCarried(new ItemStack(ModItems.SATIVA_BUDS, 4));
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ContainerInput.QUICK_CRAFT, player);
        menu.clicked(5, AbstractContainerMenu.getQuickcraftMask(1, 0), ContainerInput.QUICK_CRAFT, player);
        menu.clicked(6, AbstractContainerMenu.getQuickcraftMask(1, 0), ContainerInput.QUICK_CRAFT, player);
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ContainerInput.QUICK_CRAFT, player);
        context.assertTrue(chest.getItem(5).getCount() == 2, "test setup: the drag put " + chest.getItem(5) + " in");
        context.assertFalse(StashSmell.smellsOpen(context.getLevel(), at), "dragging buds into a chest let its smell out");

        takeOut(context, player, menu, at, "a click", () -> {
            menu.clicked(0, 0, ContainerInput.PICKUP, player);
            context.assertTrue(menu.getCarried().is(ModItems.INDICA_BUDS), "test setup: the click took " + menu.getCarried());
            menu.clicked(0, 0, ContainerInput.PICKUP, player);
        });
        takeOut(context, player, menu, at, "a shift-click", () -> menu.clicked(1, 0, ContainerInput.QUICK_MOVE, player));
        takeOut(context, player, menu, at, "a number key", () -> menu.clicked(2, 3, ContainerInput.SWAP, player));
        takeOut(context, player, menu, at, "a double-click gathering", () -> {
            // Gathering starts from an empty slot (one holding something would be picked up instead): the
            // player's first, the shift-click having filled the hotbar from its end.
            context.assertTrue(menu.getSlot(CHEST_SLOTS).getItem().isEmpty(), "test setup: the slot gathered on is full");
            menu.setCarried(new ItemStack(ModItems.INDICA_BUDS, 1));
            menu.clicked(CHEST_SLOTS, 0, ContainerInput.PICKUP_ALL, player);
            context.assertTrue(menu.getCarried().getCount() > 1, "test setup: the double-click gathered nothing");
            menu.setCarried(ItemStack.EMPTY);
        });
        takeOut(context, player, menu, at, "a drop with Q", () -> menu.clicked(5, 0, ContainerInput.THROW, player));

        // Each take-out starts the two minutes again: a smell about to fade is still out ticks later.
        StashSmell.letSmellOut(context.getLevel(), at, context.getLevel().getGameTime() + 1);
        menu.clicked(6, 0, ContainerInput.THROW, player);
        player.setGameMode(GameType.CREATIVE);
        Creeper near = StashSmellGameTest.creeper(context, new BlockPos(4, 1, 2));
        SeekStashJarGoal goal = StashSmellGameTest.jarGoalOf(near);
        context.runAfterDelay(3, () -> {
            context.assertTrue(StashSmell.smellsOpen(context.getLevel(), at), "a take-out did not start the two minutes again");
            // A stack more, as a hopper above might bring: a creeper goes for it about one check in four.
            chest.setItem(10, new ItemStack(ModItems.SATIVA_BUDS, 64));
            context.assertTrue(StashSmellGameTest.notices(goal, at, 500),
                    "a creeper 3 blocks from an opened chest of buds never noticed it in 500 checks");
            // Emptied for a moment, it draws nobody; filled again inside its two minutes, it smells again.
            java.util.List<ItemStack> held = new java.util.ArrayList<>();
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                held.add(chest.removeItemNoUpdate(slot));
            }
            context.assertFalse(StashSmellGameTest.notices(goal, at, 50), "a creeper noticed an emptied chest");
            for (int slot = 0; slot < held.size(); slot++) {
                chest.setItem(slot, held.get(slot));
            }
            context.assertTrue(StashSmellGameTest.notices(goal, at, 500),
                    "a chest emptied for a moment and filled again inside its two minutes never smelt again");
            StashSmell.letSmellOut(context.getLevel(), at, context.getLevel().getGameTime());
            context.assertFalse(StashSmell.smellsOpen(context.getLevel(), at), "a chest's smell did not fade");
            context.assertFalse(StashSmellGameTest.notices(goal, at, 200), "a creeper noticed a chest whose smell had faded");
            player.closeContainer();
            context.succeed();
        });
    }

    /**
     * Only a real hand's take-out lets storage smell: a hopper pulling buds out of a chest, a machine's hand and the
     * menu of no block (the player's own inventory) never do, and an ender chest, whose things are its opener's,
     * never smells. A double chest is one stash: either half opened lets the whole of it out, counted across both.
     */
    public static void onlyAHandLetsStorageSmell(GameTestHelper context) {
        floor(context);
        ServerPlayer player = Players.survival(context);

        // A hopper under a chest takes its buds and lets nothing out.
        BlockPos above = new BlockPos(1, 2, 1);
        context.setBlock(new BlockPos(1, 1, 1), Blocks.HOPPER);
        context.setBlock(above, Blocks.CHEST);
        BlockPos hopperChest = context.absolutePos(above);
        ((Container) context.getLevel().getBlockEntity(hopperChest)).setItem(0, new ItemStack(ModItems.INDICA_BUDS, 64));

        // A machine's hand opens nothing.
        BlockPos machinePos = new BlockPos(3, 1, 1);
        context.setBlock(machinePos, Blocks.BARREL);
        BlockPos machineAt = context.absolutePos(machinePos);
        ((Container) context.getLevel().getBlockEntity(machineAt)).setItem(0, new ItemStack(ModItems.INDICA_BUDS, 64));
        ServerPlayer machine = TestPlatform.INSTANCE.fakePlayer(context.getLevel(), player.getGameProfile());
        StashSmell.openPlacedStorage(context.getLevel(), machineAt, machine);
        context.assertFalse(StashSmell.smellsOpen(context.getLevel(), machineAt), "a fake player let a barrel's smell out");

        // The player's own inventory is no block's menu.
        player.getInventory().setItem(9, new ItemStack(ModItems.INDICA_BUDS, 64));
        player.inventoryMenu.clicked(9, 0, ContainerInput.QUICK_MOVE, player);

        // An ender chest's things are its opener's: taking buds out of it lets nothing out.
        BlockPos enderPos = new BlockPos(5, 1, 1);
        context.setBlock(enderPos, Blocks.ENDER_CHEST);
        BlockPos enderAt = context.absolutePos(enderPos);
        player.getEnderChestInventory().setItem(0, new ItemStack(ModItems.SATIVA_BUDS, 64));
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(enderAt).add(0.0, 0.0, -1.5));
        AbstractContainerMenu ender = open(context, player, enderPos);
        ender.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
        context.assertTrue(player.getEnderChestInventory().getItem(0).isEmpty(), "test setup: nothing left the ender chest");
        context.assertFalse(StashSmell.smellsOpen(context.getLevel(), enderAt), "an ender chest let a smell out");
        context.assertFalse(StashSmell.isStorage(context.getLevel(), enderAt), "an ender chest is storage whose smell gets out");
        player.closeContainer();

        // A double chest: 30 buds in each half; a bud taken out of the right half lets both halves' 59 out.
        BlockPos left = new BlockPos(1, 1, 5);
        BlockPos right = left.relative(Direction.EAST);
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
        context.setBlock(left, chest.setValue(ChestBlock.TYPE, ChestType.LEFT));
        context.setBlock(right, chest.setValue(ChestBlock.TYPE, ChestType.RIGHT));
        BlockPos leftAt = context.absolutePos(left);
        BlockPos rightAt = context.absolutePos(right);
        context.assertTrue(context.getLevel().getBlockState(leftAt).getValue(ChestBlock.TYPE) == ChestType.LEFT
                && context.getLevel().getBlockState(rightAt).getValue(ChestBlock.TYPE) == ChestType.RIGHT,
                "test setup: the two chests are no double chest");
        ((Container) context.getLevel().getBlockEntity(leftAt)).setItem(0, new ItemStack(ModItems.INDICA_BUDS, 30));
        ((Container) context.getLevel().getBlockEntity(rightAt)).setItem(0, new ItemStack(ModItems.INDICA_BUDS, 30));
        context.assertTrue(StashSmell.stashAt(context.getLevel(), leftAt).equals(StashSmell.stashAt(context.getLevel(), rightAt)),
                "the two halves of a double chest are two stashes");
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(rightAt).add(0.0, 0.0, -1.5));
        AbstractContainerMenu both = open(context, player, right);
        context.assertTrue(both instanceof ChestMenu chestMenu && chestMenu.getRowCount() == 6,
                "test setup: the double chest opened " + both);
        int bud = -1;
        for (int slot = 0; slot < 54 && bud < 0; slot++) {
            if (!both.getSlot(slot).getItem().isEmpty()) {
                bud = slot;
            }
        }
        both.clicked(bud, 0, ContainerInput.THROW, player);
        BlockPos stash = StashSmell.stashAt(context.getLevel(), rightAt);
        context.assertTrue(StashSmell.smellsOpen(context.getLevel(), stash), "a take-out from a double chest let nothing out");
        context.assertTrue(StashSmell.budsAt(context.getLevel(), stash) == 59,
                "a double chest of 30 and 30, less one, counts " + StashSmell.budsAt(context.getLevel(), stash));
        StashSmell.letSmellOut(context.getLevel(), stash, context.getLevel().getGameTime());
        player.closeContainer();

        context.runAfterDelay(40, () -> {
            int left64 = ((Container) context.getLevel().getBlockEntity(hopperChest)).getItem(0).getCount();
            context.assertTrue(left64 < 64, "test setup: the hopper took nothing from the chest above it");
            context.assertFalse(StashSmell.smellsOpen(context.getLevel(), hopperChest), "a hopper let a chest's smell out");
            context.succeed();
        });
    }

    /** A take-out of {@code what}, in a menu whose smell was shut before it: afterwards the block smells. */
    private static void takeOut(GameTestHelper context, ServerPlayer player, AbstractContainerMenu menu, BlockPos at,
                                String what, Runnable click) {
        StashSmell.letSmellOut(context.getLevel(), at, context.getLevel().getGameTime());
        context.assertFalse(StashSmell.smellsOpen(context.getLevel(), at), "test setup: the chest was still open");
        click.run();
        context.assertTrue(StashSmell.smellsOpen(context.getLevel(), at), what + " took buds out and let no smell out");
    }

    private static AbstractContainerMenu open(GameTestHelper context, ServerPlayer player, BlockPos pos) {
        Players.click(context, player, pos, ItemStack.EMPTY);
        context.assertTrue(player.containerMenu != player.inventoryMenu, "test setup: clicking " + pos + " opened nothing");
        return player.containerMenu;
    }

    /** The chest menu's slot for the player's hotbar slot {@code index}. */
    private static int hotbar(int index) {
        return CHEST_SLOTS + 27 + index;
    }

    private static void floor(GameTestHelper context) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                context.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }
}
