package com.warlonmhite.hempdustry.test;

import com.warlonmhite.hempdustry.config.ModGameRules;
import com.warlonmhite.hempdustry.block.ModBlocks;
import com.warlonmhite.hempdustry.component.StashContents;
import com.warlonmhite.hempdustry.block.entity.custom.StashJarBlockEntity;
import com.warlonmhite.hempdustry.entity.ai.SeekStashJarGoal;
import com.warlonmhite.hempdustry.entity.ai.StashSmell;
import com.warlonmhite.hempdustry.item.ModItems;
import com.warlonmhite.hempdustry.item.custom.StashJarItem;
import com.warlonmhite.hempdustry.util.ModTags;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Creepers smell a stash: an opened Stash Jar draws them, and loose buds give a player away, a creeper
 * noticing their carrier from further, while a shut jar hides them until a hand opens it.
 */
public final class StashSmellGameTest {

    /**
     * What counts and by how much: nothing ×1, any loose bud ×1.25, a stack ×1.5; schwag and buds in a bundle
     * count, kief and a spliff never; a carried jar's contents only for the two minutes after buds came out of
     * it; and with the switch off, nothing at all.
     */
    public static void looseBudsLengthenACreepersReach(GameTestHelper context) {
        ServerPlayer player = Players.survival(context);
        expect(context, player, "an empty inventory", 1.0);

        give(player, new ItemStack(ModItems.INDICA_BUDS, 1));
        expect(context, player, "one loose bud", StashSmell.SOME_BUDS);
        give(player, new ItemStack(ModItems.INDICA_BUDS, 63));
        expect(context, player, "63 loose buds", StashSmell.SOME_BUDS);
        player.getInventory().setItem(1, new ItemStack(ModItems.SCHWAG, 1));
        expect(context, player, "63 buds and a schwag", StashSmell.A_STACK);

        give(player, new ItemStack(ModItems.KIEF, 64));
        player.getInventory().setItem(1, new ItemStack(ModItems.SPLIFF, 16));
        expect(context, player, "kief and spliffs", 1.0);

        ItemStack bundle = new ItemStack(Items.BUNDLE);
        bundle.set(DataComponents.BUNDLE_CONTENTS,
                new BundleContents(List.of(new ItemStackTemplate(ModItems.SATIVA_BUDS, 8))));
        give(player, bundle);
        player.getInventory().setItem(1, ItemStack.EMPTY);
        expect(context, player, "buds in a bundle", StashSmell.SOME_BUDS);

        // A shut jar keeps its smell in, however full.
        ItemStack jar = new ItemStack(ModBlocks.STASH_JAR);
        StashContents.Mutable inside = StashContents.EMPTY.mutable();
        inside.insert(new ItemStack(ModItems.SATIVA_BUDS, 63), 128);
        inside.insert(new ItemStack(ModItems.INDICA_BUDS, 1), 128);
        StashJarItem.setContents(jar, inside.toImmutable());
        give(player, jar);
        expect(context, player, "a shut jar of 64 buds", 1.0);
        context.assertFalse(StashSmell.carriedJarOpen(player), "a jar was open before anything came out of it");

        // Putting a bud in is no opening.
        ItemStack bud = new ItemStack(ModItems.INDICA_BUDS, 1);
        ((StashJarItem) jar.getItem()).overrideOtherStackedOnMe(jar, bud, new Slot(player.getInventory(), 0, 0, 0),
                ClickAction.PRIMARY, player, net.minecraft.world.entity.SlotAccess.of(() -> ItemStack.EMPTY, stack -> { }));
        expect(context, player, "a jar a bud was put into", 1.0);

        // Taking the last strain's buds out onto an empty slot opens it: its 63 count with the two that came out.
        ((StashJarItem) jar.getItem()).overrideStackedOnOther(jar, new Slot(player.getInventory(), 1, 0, 0),
                ClickAction.SECONDARY, player);
        context.assertTrue(player.getInventory().getItem(1).getCount() == 2, "the take-out took "
                + player.getInventory().getItem(1));
        context.assertTrue(StashSmell.carriedJarOpen(player), "taking buds out of a carried jar did not open it");
        expect(context, player, "an opened jar of 63 and two buds beside it", StashSmell.A_STACK);

        // Two minutes later it is shut again, and only the loose bud is left to smell.
        ((StashSmell.Carrier) player).hempdustry$setCarriedJarOpenUntil(player.level().getGameTime());
        expect(context, player, "a jar two minutes after it was opened, and two buds", StashSmell.SOME_BUDS);

        // A machine's hand never opens a jar.
        ServerPlayer machine = TestPlatform.INSTANCE.fakePlayer(context.getLevel(), player.getGameProfile());
        StashSmell.openCarriedJar(machine);
        context.assertFalse(StashSmell.carriedJarOpen(machine), "a fake player opened a carried jar");

        ConfigGameTest.withConfig("{ \"world\": { \"creepersSeekHemp\": false } }", () ->
                expect(context, player, "a bud, with creepersSeekHemp off", 1.0));
        GameRuleGameTest.withRule(context, ModGameRules.MOBS_DRAWN_TO_HEMP, false, () ->
                expect(context, player, "a bud, with the mobs_drawn_to_hemp game rule off", 1.0));
        context.succeed();
    }

    /**
     * A box holds the smell in and a bag lets it out: a shulker box, a chest or a barrel carried full of buds hides
     * them, a bundle does not, and any other item that holds buds is read through its loader's item API, as a
     * shulker box's contents are. Taking buds out of a jar standing in a chest's slot opens none of the jars the
     * player carries; out of one in their own inventory, it does.
     */
    public static void aBoxHoldsTheSmellInAndABagLetsItOut(GameTestHelper context) {
        ServerPlayer player = Players.survival(context);
        List<ItemStack> buds = List.of(new ItemStack(ModItems.INDICA_BUDS, 64));
        for (net.minecraft.world.item.Item box : List.of(Items.SHULKER_BOX, Items.CHEST, Items.BARREL)) {
            ItemStack full = new ItemStack(box);
            full.set(DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(buds));
            give(player, full);
            expect(context, player, "a " + box + " of 64 buds", 1.0);
        }
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(buds));
        List<ItemStack> seen = com.warlonmhite.hempdustry.platform.Services.TRANSFER.contents(shulker);
        context.assertTrue(seen.size() == 1 && seen.getFirst().is(ModItems.INDICA_BUDS) && seen.getFirst().getCount() == 64,
                "the loader's item API shows " + seen + " in a shulker box of 64 buds: a backpack would not be read");

        // A jar in a chest's slot, right-clicked open: the jar the player carries stays shut.
        ItemStack carried = new ItemStack(ModBlocks.STASH_JAR);
        StashContents.Mutable inside = StashContents.EMPTY.mutable();
        inside.insert(new ItemStack(ModItems.SATIVA_BUDS, 64), 128);
        StashJarItem.setContents(carried, inside.toImmutable());
        give(player, carried);
        ItemStack inTheChest = carried.copy();
        Slot chestSlot = new Slot(new net.minecraft.world.SimpleContainer(1), 0, 0, 0);
        chestSlot.set(inTheChest);
        ItemStack[] cursor = {ItemStack.EMPTY};
        ((StashJarItem) inTheChest.getItem()).overrideOtherStackedOnMe(inTheChest, ItemStack.EMPTY, chestSlot,
                ClickAction.SECONDARY, player, net.minecraft.world.entity.SlotAccess.of(() -> cursor[0], stack -> cursor[0] = stack));
        context.assertTrue(cursor[0].is(ModItems.SATIVA_BUDS), "test setup: nothing came out of the jar in the chest");
        context.assertFalse(StashSmell.carriedJarOpen(player),
                "taking buds out of a jar in a chest opened the jar the player carries");
        cursor[0] = ItemStack.EMPTY;
        ((StashJarItem) carried.getItem()).overrideOtherStackedOnMe(carried, ItemStack.EMPTY,
                new Slot(player.getInventory(), 0, 0, 0), ClickAction.SECONDARY, player,
                net.minecraft.world.entity.SlotAccess.of(() -> cursor[0], stack -> cursor[0] = stack));
        context.assertTrue(StashSmell.carriedJarOpen(player), "taking buds out of a carried jar did not open it");
        context.succeed();
    }

    /**
     * The smell gives away a player vanilla sees less of, and never takes a creeper's reach past its follow range:
     * a creeper with a 4-block reach notices a sneaking player (3.2) 3.5 off only with a bud on them, at the full 4
     * with a bud or a stack, never 4.5 off whatever they carry, and never through a wall. Behind a creeper head
     * (0.5, 2 blocks) a bud makes 2.5 and a stack 3. A creeper drops a target past its follow range, as vanilla does.
     */
    public static void aCreeperSmellsBudsButStillNeedsToSeeThem(GameTestHelper context) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                context.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        Creeper creeper = creeper(context, new BlockPos(1, 1, 1));
        ServerPlayer player = Players.survival(context);
        Vec3 from = creeper.position();
        TargetingConditions reach4 = TargetingConditions.forCombat().range(4.0);

        player.setShiftKeyDown(true);
        player.setPos(from.add(3.5, 0.0, 0.0));
        context.assertFalse(reach4.test(context.getLevel(), creeper, player),
                "a creeper with a 4-block reach noticed a sneaking player 3.5 off carrying nothing; 4 × 0.8 is 3.2");
        give(player, new ItemStack(ModItems.INDICA_BUDS, 1));
        context.assertTrue(reach4.test(context.getLevel(), creeper, player),
                "a creeper with a 4-block reach missed a sneaking player 3.5 off carrying a bud; 4 × 0.8 × 1.25 is 4");

        player.setPos(from.add(4.5, 0.0, 0.0));
        give(player, new ItemStack(ModItems.INDICA_BUDS, 64));
        context.assertFalse(reach4.test(context.getLevel(), creeper, player),
                "a stack took a creeper's 4-block reach past 4, sneaking");
        player.setShiftKeyDown(false);
        context.assertFalse(reach4.test(context.getLevel(), creeper, player),
                "a stack took a creeper's 4-block reach past 4, standing");
        context.assertTrue(StashSmell.visibility(1.0, player) == 1.0 && StashSmell.visibility(0.5, player) == 0.75,
                "a stack's visibility is " + StashSmell.visibility(1.0, player) + " in plain sight and "
                        + StashSmell.visibility(0.5, player) + " behind a head, not 1 and 0.75");
        give(player, new ItemStack(ModItems.INDICA_BUDS, 1));
        context.assertTrue(StashSmell.visibility(0.5, player) == 0.625,
                "a bud's visibility behind a head is " + StashSmell.visibility(0.5, player) + ", not 0.625");

        // A creeper drops a target past its follow range, set to 4 here, whatever the target carries.
        player.setPos(from.add(5.5, 0.0, 0.0));
        give(player, new ItemStack(ModItems.INDICA_BUDS, 64));
        creeper.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(4.0);
        NearestAttackableTargetGoal<?> targeting = targetGoal(creeper);
        creeper.setTarget(player);
        context.assertFalse(targeting.canContinueToUse(),
                "a creeper kept a player 5.5 off carrying a stack past its follow range of 4");
        targeting.stop();

        // A wall between them hides a player whatever they carry. Another creeper, as each one keeps what it
        // saw for the rest of the tick.
        give(player, new ItemStack(ModItems.INDICA_BUDS, 64));
        player.setPos(from.add(4.5, 0.0, 0.0));
        for (int y = 1; y <= 3; y++) {
            for (int z = 0; z < 8; z++) {
                context.setBlock(new BlockPos(4, y, z), Blocks.STONE);
            }
        }
        Creeper walled = creeper(context, new BlockPos(1, 1, 2));
        player.setPos(walled.position().add(4.5, 0.0, 0.0));
        context.assertFalse(reach4.test(context.getLevel(), walled, player),
                "a creeper noticed a player carrying buds through a wall");
        context.succeed();
    }

    /** The agreed numbers: 8 blocks plus 4 per full slot of 64, 24 at most; buds in 256, sure from four stacks. */
    public static void anOpenedJarsPullIsItsBuds(GameTestHelper context) {
        int[][] reaches = {{0, 8}, {1, 8}, {63, 8}, {64, 12}, {127, 12}, {128, 16}, {192, 20}, {256, 24}, {1024, 24}};
        for (int[] row : reaches) {
            context.assertTrue(StashSmell.reach(row[0]) == row[1],
                    "an opened jar of " + row[0] + " was noticed within " + StashSmell.reach(row[0]) + ", not " + row[1]);
        }
        java.util.Random seeds = new java.util.Random(55L);
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(seeds.nextLong());
        int stack = 0;
        for (int roll = 0; roll < 4000; roll++) {
            context.assertTrue(StashSmell.draws(random, 256), "four stacks missed a check");
            context.assertFalse(StashSmell.draws(random, 0), "an empty jar drew a creeper");
            if (StashSmell.draws(random, 64)) {
                stack++;
            }
        }
        // A stack is one check in four: 1000 of 4000, give or take.
        context.assertTrue(stack > 880 && stack < 1120, "a stack drew " + stack + " checks of 4000, not about 1000");
        context.succeed();
    }

    /**
     * Only an opened jar draws: a shut one, buds put in and a machine's hand never do; a real hand taking buds
     * out does, a creeper in reach goes for it, one out of reach never, and it lets go when the smell fades,
     * with the switch off, or for a target.
     */
    public static void onlyAnOpenedJarDrawsCreepers(GameTestHelper context) {
        BlockPos floor = new BlockPos(1, 1, 1);
        BlockPos jarPos = floor.above();
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                context.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        context.setBlock(floor, Blocks.STONE);
        context.setBlock(jarPos, ModBlocks.STASH_JAR.defaultBlockState());
        BlockPos at = context.absolutePos(jarPos);
        StashJarBlockEntity jar = (StashJarBlockEntity) context.getLevel().getBlockEntity(at);
        StashContents.Mutable inside = StashContents.EMPTY.mutable();
        inside.insert(new ItemStack(ModItems.INDICA_BUDS, 64), 128);
        inside.insert(new ItemStack(ModItems.SATIVA_BUDS, 63), 128);
        jar.setContents(inside.toImmutable());

        Creeper near = creeper(context, new BlockPos(4, 1, 2));
        Creeper far = creeper(context, new BlockPos(7, 1, 7));
        SeekStashJarGoal nearGoal = jarGoalOf(near);
        SeekStashJarGoal farGoal = jarGoalOf(far);
        context.assertTrue(nearGoal != null, "MobGoalsMixin did not add SeekStashJarGoal to a creeper");

        // Other tests' jars share this level, so each check asks whether this jar is the one noticed.
        context.assertFalse(notices(nearGoal, at, 500), "a creeper noticed a shut jar");
        ServerPlayer player = Players.survival(context);
        player.setPos(Vec3.atCenterOf(at).add(0.0, 0.0, -1.5));
        Players.click(context, player, jarPos, new ItemStack(ModItems.INDICA_BUDS, 1));
        context.assertFalse(StashSmell.smellsOpen(context.getLevel(), at), "putting a bud in opened the jar");
        ServerPlayer machine = TestPlatform.INSTANCE.fakePlayer(context.getLevel(), player.getGameProfile());
        StashSmell.openPlacedJar(context.getLevel(), at, jar, machine);
        context.assertFalse(StashSmell.smellsOpen(context.getLevel(), at), "a fake player's take-out opened the jar");

        // A hand takes a stack out: about 64 buds left, 12 blocks of reach, about one check in four.
        Players.click(context, player, jarPos, ItemStack.EMPTY);
        context.assertFalse(player.getMainHandItem().isEmpty(), "an empty hand took nothing out of the jar");
        context.assertTrue(StashSmell.smellsOpen(context.getLevel(), at), "a hand taking buds out did not open the jar");
        // Nobody to chase while the creepers think: the player steps out of their sight.
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);

        context.assertTrue(notices(nearGoal, at, 500), "a creeper 3 blocks from an opened jar of 64 never noticed it in 500 checks");
        nearGoal.start();
        context.assertTrue(nearGoal.canContinueToUse(), "a creeper let go of a jar that still smells");
        // The box is 8 wide, so the far creeper, 8.5 blocks off, is put out of reach by the jar's count: under a
        // stack, the jar is noticed within 8.
        StashContents.Mutable fewer = StashContents.EMPTY.mutable();
        fewer.insert(new ItemStack(ModItems.INDICA_BUDS, 63), 128);
        jar.setContents(fewer.toImmutable());
        context.assertTrue(far.position().distanceTo(Vec3.atCenterOf(at)) > StashSmell.reach(63),
                "test setup: the far creeper is in reach");
        context.assertFalse(notices(farGoal, at, 500), "a creeper out of an opened jar's reach noticed it");

        // A target comes first (a player in survival: a creeper takes no creative one), and the switch turns it off.
        ServerPlayer prey = Players.survival(context);
        near.setTarget(prey);
        context.assertTrue(near.getTarget() == prey, "test setup: the creeper took no target");
        nearGoal.checkNow();
        context.assertFalse(nearGoal.canUse(), "a creeper with a target went for a jar instead");
        near.setTarget(null);
        ConfigGameTest.withConfig("{ \"world\": { \"creepersSeekHemp\": false } }", () -> {
            context.assertFalse(noticesWithin(nearGoal, 200), "a creeper noticed an opened jar with creepersSeekHemp off");
            context.assertFalse(nearGoal.canContinueToUse(), "a creeper stayed by a jar with creepersSeekHemp off");
        });
        GameRuleGameTest.withRule(context, ModGameRules.MOBS_DRAWN_TO_HEMP, false, () -> {
            context.assertFalse(noticesWithin(nearGoal, 200),
                    "a creeper noticed an opened jar with the mobs_drawn_to_hemp game rule off");
            context.assertFalse(nearGoal.canContinueToUse(),
                    "a creeper stayed by a jar with the mobs_drawn_to_hemp game rule off");
        });

        // Two minutes on, the jar is shut and the creeper is an ordinary creeper again.
        jar.letSmellOut(context.getLevel().getGameTime());
        context.assertFalse(nearGoal.canContinueToUse(), "a creeper stayed by a jar whose smell had faded");
        context.assertFalse(notices(nearGoal, at, 200), "a creeper noticed a jar whose smell had faded");
        nearGoal.stop();
        context.succeed();
    }

    /**
     * A creeper thinking for itself walks to an opened jar and waits by it. A stack only, so the jar draws
     * nothing past 12 blocks while the tests around run, and shut again once the creeper is there.
     */
    public static void aCreeperWalksToAnOpenedJar(GameTestHelper context) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                context.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        BlockPos jarPos = new BlockPos(1, 1, 1);
        context.setBlock(jarPos, ModBlocks.DIAMOND_STASH_JAR.defaultBlockState());
        BlockPos at = context.absolutePos(jarPos);
        StashJarBlockEntity jar = (StashJarBlockEntity) context.getLevel().getBlockEntity(at);
        StashContents.Mutable inside = StashContents.EMPTY.mutable();
        inside.insert(new ItemStack(ModItems.INDICA_BUDS, 64), 512);
        inside.insert(new ItemStack(ModItems.SATIVA_BUDS, 64), 512);
        jar.setContents(inside.toImmutable());
        ServerPlayer player = Players.survival(context);
        player.setPos(Vec3.atCenterOf(at).add(0.0, 0.0, 1.5));
        Players.click(context, player, jarPos, ItemStack.EMPTY);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        context.assertTrue(StashSmell.smellsOpen(context.getLevel(), at), "the take-out did not open the jar");

        Creeper creeper = context.spawn(EntityTypes.CREEPER, new BlockPos(6, 1, 6));
        context.succeedWhen(() -> {
            double away = creeper.position().distanceTo(Vec3.atBottomCenterOf(at));
            context.assertTrue(away <= 3.0, "the creeper is " + String.format("%.1f", away)
                    + " blocks from an opened jar of 64 buds");
            jar.letSmellOut(context.getLevel().getGameTime());
        });
    }

    /**
     * Which mobs a stash draws is {@code #hempdustry:drawn_to_stash}, vanilla's creeper alone by default. A creeper out
     * of it never goes for an opened jar, notices a player carrying a stack no further than one carrying nothing, and
     * one made while it is out is given no jar goal; back in, it smells both again. A pig the tag lists is made with
     * the jar goal, goes for the jar and smells the stack; out of the tag again, it smells neither.
     */
    public static void onlyAMobDrawnToAStashSmellsIt(GameTestHelper context) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                context.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        BlockPos jarPos = new BlockPos(1, 1, 1);
        context.setBlock(jarPos, ModBlocks.STASH_JAR.defaultBlockState());
        BlockPos at = context.absolutePos(jarPos);
        StashJarBlockEntity jar = (StashJarBlockEntity) context.getLevel().getBlockEntity(at);
        StashContents.Mutable inside = StashContents.EMPTY.mutable();
        inside.insert(new ItemStack(ModItems.INDICA_BUDS, 64), 128);
        inside.insert(new ItemStack(ModItems.SATIVA_BUDS, 64), 128);
        jar.setContents(inside.toImmutable());
        ServerPlayer opener = Players.survival(context);
        opener.setPos(Vec3.atCenterOf(at).add(1.5, 0.0, 0.0));
        Players.click(context, opener, jarPos, ItemStack.EMPTY);
        opener.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        context.assertTrue(StashSmell.smellsOpen(context.getLevel(), at), "the take-out did not open the jar");

        // A creeper with a 4-block reach notices a sneaking player (3.2) 3.5 off only by a stack's smell.
        TargetingConditions reach4 = TargetingConditions.forCombat().range(4.0);
        ServerPlayer carrier = Players.survival(context);
        carrier.setShiftKeyDown(true);
        give(carrier, new ItemStack(ModItems.INDICA_BUDS, 64));
        Creeper near = creeper(context, new BlockPos(1, 1, 6));
        SeekStashJarGoal goal = jarGoalOf(near);
        context.assertTrue(goal != null, "a creeper, which the tag lists by default, was made without the jar goal");
        carrier.setPos(near.position().add(3.5, 0.0, 0.0));
        context.assertTrue(reach4.test(context.getLevel(), near, carrier),
                "a creeper in the tag missed a sneaking player 3.5 off carrying a stack");
        Tagged.without(EntityTypes.CREEPER, ModTags.EntityTypes.DRAWN_TO_STASH, () -> {
            context.assertFalse(reach4.test(context.getLevel(), near, carrier),
                    "a creeper out of #hempdustry:drawn_to_stash noticed a sneaking player 3.5 off by the smell of a stack");
            context.assertFalse(notices(goal, at, 500), "a creeper out of #hempdustry:drawn_to_stash went for an opened jar");
            Creeper untagged = creeper(context, new BlockPos(3, 1, 3));
            context.assertTrue(jarGoalOf(untagged) == null, "a creeper made while out of the tag was given the jar goal");
            untagged.discard();
        });
        context.assertTrue(notices(goal, at, 500), "a creeper back in the tag never went for the opened jar in 500 checks");
        near.discard();

        Mob[] listed = new Mob[1];
        Tagged.within(EntityTypes.PIG, ModTags.EntityTypes.DRAWN_TO_STASH, () -> {
            listed[0] = context.spawn(EntityTypes.PIG, new BlockPos(1, 1, 4));
            listed[0].setNoAi(true);
            SeekStashJarGoal pigGoal = jarGoalOf(listed[0]);
            context.assertTrue(pigGoal != null, "a pig the tag lists was made without the jar goal");
            context.assertTrue(notices(pigGoal, at, 500), "a pig the tag lists never went for the opened jar in 500 checks");
            carrier.setPos(listed[0].position().add(3.5, 0.0, 0.0));
            context.assertTrue(reach4.test(context.getLevel(), listed[0], carrier),
                    "a pig the tag lists missed a sneaking player 3.5 off carrying a stack");
        });
        context.assertFalse(reach4.test(context.getLevel(), listed[0], carrier),
                "a pig the tag no longer lists noticed a player by the smell of a stack");
        context.assertFalse(notices(jarGoalOf(listed[0]), at, 500), "a pig the tag no longer lists went for an opened jar");
        listed[0].discard();
        jar.letSmellOut(context.getLevel().getGameTime());
        context.succeed();
    }

    /** Whether {@code goal} goes for the jar at {@code jar} within {@code checks} checks. */
    private static boolean notices(SeekStashJarGoal goal, BlockPos jar, int checks) {
        for (int check = 0; check < checks; check++) {
            goal.checkNow();
            if (goal.canUse() && jar.equals(goal.jar())) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code goal} goes for any jar within {@code checks} checks. */
    private static boolean noticesWithin(SeekStashJarGoal goal, int checks) {
        for (int check = 0; check < checks; check++) {
            goal.checkNow();
            if (goal.canUse()) {
                return true;
            }
        }
        return false;
    }

    private static SeekStashJarGoal jarGoalOf(Mob mob) {
        try {
            var field = Mob.class.getDeclaredField("goalSelector");
            field.setAccessible(true);
            return ((GoalSelector) field.get(mob)).getAvailableGoals().stream()
                    .map(wrapped -> wrapped.getGoal())
                    .filter(SeekStashJarGoal.class::isInstance)
                    .map(SeekStashJarGoal.class::cast)
                    .findFirst().orElse(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Creeper creeper(GameTestHelper context, BlockPos at) {
        Creeper creeper = context.spawn(EntityTypes.CREEPER, at);
        // Driven by hand: a creeper left thinking would walk off, or swell at the player.
        creeper.setNoAi(true);
        return creeper;
    }

    private static NearestAttackableTargetGoal<?> targetGoal(Mob mob) {
        try {
            var field = Mob.class.getDeclaredField("targetSelector");
            field.setAccessible(true);
            return ((GoalSelector) field.get(mob)).getAvailableGoals().stream()
                    .map(wrapped -> wrapped.getGoal())
                    .filter(NearestAttackableTargetGoal.class::isInstance)
                    .map(goal -> (NearestAttackableTargetGoal<?>) goal)
                    .findFirst().orElseThrow();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Puts {@code stack} in the first slot, and forgets the count kept for this tick. */
    private static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().setItem(0, stack);
        forget(player);
    }

    private static void forget(ServerPlayer player) {
        ((StashSmell.Carrier) player).hempdustry$setBudsCounted(Long.MIN_VALUE, 0);
    }

    private static void expect(GameTestHelper context, ServerPlayer player, String what, double factor) {
        forget(player);
        double actual = StashSmell.reachFactor(player);
        context.assertTrue(actual == factor, what + " made a creeper's reach ×" + actual + ", not ×" + factor);
    }
}
