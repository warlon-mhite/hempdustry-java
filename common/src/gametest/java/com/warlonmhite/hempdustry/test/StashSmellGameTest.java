package com.warlonmhite.hempdustry.test;

import com.warlonmhite.hempdustry.block.ModBlocks;
import com.warlonmhite.hempdustry.component.StashContents;
import com.warlonmhite.hempdustry.entity.ai.StashSmell;
import com.warlonmhite.hempdustry.item.ModItems;
import com.warlonmhite.hempdustry.item.custom.StashJarItem;
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
 * Loose buds give a player away: a creeper notices their carrier from further, and a shut Stash Jar hides
 * them until a hand opens it.
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
        context.succeed();
    }

    /**
     * The smell multiplies vanilla's reach and leaves its line of sight alone: a creeper with a 4-block reach
     * notices a player 4.5 off only with a bud on them, one 5.5 off only with a stack, never through a wall;
     * and it keeps a target as far as it noticed it.
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

        player.setPos(from.add(4.5, 0.0, 0.0));
        context.assertFalse(reach4.test(context.getLevel(), creeper, player),
                "a creeper with a 4-block reach noticed a player 4.5 off carrying nothing");
        give(player, new ItemStack(ModItems.INDICA_BUDS, 1));
        context.assertTrue(reach4.test(context.getLevel(), creeper, player),
                "a creeper with a 4-block reach (5 with the smell) missed a player 4.5 off carrying a bud");

        player.setPos(from.add(5.5, 0.0, 0.0));
        context.assertFalse(reach4.test(context.getLevel(), creeper, player),
                "a bud lengthened a creeper's reach past 1.25");
        give(player, new ItemStack(ModItems.INDICA_BUDS, 64));
        context.assertTrue(reach4.test(context.getLevel(), creeper, player),
                "a creeper with a 4-block reach (6 with a stack) missed a player 5.5 off carrying 64 buds");

        // Vanilla's factors still apply: sneaking takes 6 down to 4.8.
        player.setShiftKeyDown(true);
        context.assertFalse(reach4.test(context.getLevel(), creeper, player),
                "a sneaking player carrying a stack was noticed 5.5 off; 4 × 1.5 × 0.8 is 4.8");
        player.setShiftKeyDown(false);

        // A creeper keeps a target as far as it noticed it: its follow range set to 4 here.
        creeper.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(4.0);
        NearestAttackableTargetGoal<?> targeting = targetGoal(creeper);
        creeper.setTarget(player);
        context.assertTrue(targeting.canContinueToUse(),
                "a creeper gave up a player 5.5 off carrying a stack; its reach on them is 6");
        give(player, ItemStack.EMPTY);
        context.assertFalse(targeting.canContinueToUse(),
                "a creeper kept a player 5.5 off carrying nothing past its follow range of 4");
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
