package com.warlonmhite.hempdustry.test;

import com.warlonmhite.hempdustry.block.ModBlocks;
import com.warlonmhite.hempdustry.block.custom.stash.StashNose;
import com.warlonmhite.hempdustry.block.custom.stash.TerpenePuff;
import com.warlonmhite.hempdustry.block.entity.custom.StashJarBlockEntity;
import com.warlonmhite.hempdustry.component.StashContents;
import com.warlonmhite.hempdustry.item.ModItems;
import com.warlonmhite.hempdustry.particle.TerpenePuffOption;
import com.warlonmhite.hempdustry.strain.BudTier;
import com.warlonmhite.hempdustry.strain.Chemotype;
import com.warlonmhite.hempdustry.strain.Strain;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The terpene puff: how a Stash Jar smells when it is opened, read off the whole jar, and who sees it.
 */
public final class StashPuffGameTest {

    /** The agreed table: the mean of the jar's tiers (schwag −4), less one per other kind, rounded down. */
    public static void aJarsNoseIsTheWholeJar(GameTestHelper context) {
        Item kush = ModItems.INDICA_BUDS;
        Item haze = ModItems.SATIVA_BUDS;
        expect(context, "128 Epic Purple Kush", StashNose.EPIC, buds(kush, 128, BudTier.EPIC));
        expect(context, "128 Top Shelf Purple Kush", StashNose.STRONG, buds(kush, 128, BudTier.TOP_SHELF));
        expect(context, "128 Dank Purple Kush", StashNose.DECENT, buds(kush, 128, BudTier.DANK));
        expect(context, "128 Mids Purple Kush", StashNose.SOFT, buds(kush, 128, BudTier.MIDS));
        expect(context, "120 Epic + 8 schwag", StashNose.DECENT, buds(kush, 120, BudTier.EPIC), schwag(8));
        expect(context, "127 Epic + 1 schwag", StashNose.DECENT, buds(kush, 127, BudTier.EPIC), schwag(1));
        expect(context, "127 Epic + 1 Mids, one strain", StashNose.STRONG,
                buds(kush, 127, BudTier.EPIC), buds(kush, 1, BudTier.MIDS));
        expect(context, "64 Epic Purple Kush + 64 Epic Lemon Haze", StashNose.STRONG,
                buds(kush, 64, BudTier.EPIC), buds(haze, 64, BudTier.EPIC));
        expect(context, "96 Epic Purple Kush + 32 Mids Lemon Haze", StashNose.DECENT,
                buds(kush, 96, BudTier.EPIC), buds(haze, 32, BudTier.MIDS));
        expect(context, "96 Epic + 32 schwag", StashNose.SOFT, buds(kush, 96, BudTier.EPIC), schwag(32));
        expect(context, "64 Epic + 64 schwag", StashNose.ROTTEN, buds(kush, 64, BudTier.EPIC), schwag(64));
        expect(context, "a jar of schwag", StashNose.ROTTEN, schwag(40));
        // Two schwag chemotypes are two kinds: schwag pays by its weight and as a kind.
        expect(context, "schwag of two chemotypes", StashNose.ROTTEN, schwag(10),
                Chemotype.stamp(new ItemStack(ModItems.SCHWAG, 10), Chemotype.CBD_DOMINANT));
        // Stale with no schwag to blame: three kinds of Mids.
        expect(context, "Mids of three strains", StashNose.STALE, buds(kush, 20, BudTier.MIDS),
                buds(haze, 20, BudTier.MIDS), buds(ModItems.RUDERALIS_BUDS, 20, BudTier.MIDS));
        context.assertTrue(StashNose.of(StashContents.EMPTY) == StashNose.STALE, "an empty jar smells of something");
        // The order things went in changes nothing: the sums decide.
        StashContents one = jar(buds(kush, 50, BudTier.EPIC), schwag(3), buds(kush, 50, BudTier.MIDS));
        StashContents other = jar(buds(kush, 50, BudTier.MIDS), buds(kush, 50, BudTier.EPIC), schwag(3));
        context.assertTrue(StashNose.of(one) == StashNose.of(other), "the nose moved with the order things went in: "
                + StashNose.of(one) + " and " + StashNose.of(other));
        context.succeed();
    }

    /**
     * An empty hand opening a placed jar lets off the puff for everyone within particle range (and an epic
     * jar its chime within a block sound's 16), the opener
     * too, in the colour of what came out and at the whole jar's nose; putting buds in lets off none, and
     * nor does a click that takes nothing out.
     */
    public static void anOpenedJarPuffsForEveryoneNear(GameTestHelper context) {
        BlockPos floor = new BlockPos(3, 1, 3);
        BlockPos jarPos = floor.above();
        context.setBlock(floor, Blocks.STONE);
        context.setBlock(jarPos, ModBlocks.DIAMOND_STASH_JAR.defaultBlockState());
        StashJarBlockEntity jar = (StashJarBlockEntity) context.getLevel().getBlockEntity(context.absolutePos(jarPos));
        jar.setContents(jar(buds(ModItems.INDICA_BUDS, 128, BudTier.EPIC)));

        Vec3 at = Vec3.atCenterOf(context.absolutePos(jarPos));
        Players.Watched opener = Players.watched(context);
        Players.Watched near = Players.watched(context);
        Players.Watched far = Players.watched(context);
        opener.player().setPos(at.add(1.5, 0.0, 0.0));
        near.player().setPos(at.add(0.0, 0.0, 12.0));
        far.player().setPos(at.add(0.0, 0.0, 40.0));
        clear(opener, near, far);

        Players.click(context, opener.player(), jarPos, ItemStack.EMPTY);
        context.assertTrue(opener.player().getMainHandItem().is(ModItems.INDICA_BUDS), "an empty hand took nothing out");
        int smoke = Strain.fromBuds(context.getLevel().registryAccess(), ModItems.INDICA_BUDS).orElseThrow().value()
                .smokeTint(context.getLevel().registryAccess()).orElse(TerpenePuff.PLAIN);
        TerpenePuffOption expected = new TerpenePuffOption(smoke, StashNose.EPIC);
        List<Packet<?>> openerSaw = opener.sent();
        List<Packet<?>> nearSaw = near.sent();
        context.assertTrue(puffs(openerSaw).equals(List.of(expected)), "the opener saw " + puffs(openerSaw) + ", not " + expected);
        context.assertTrue(puffs(nearSaw).equals(List.of(expected)), "a player 12 blocks off saw " + puffs(nearSaw));
        context.assertTrue(puffs(far.sent()).isEmpty(), "a player 40 blocks off saw a puff");
        // An epic jar rings, for everyone near.
        context.assertTrue(chimes(openerSaw) == 1 && chimes(nearSaw) == 1,
                "an epic jar opened chimed " + chimes(openerSaw) + " time(s) for the opener and " + chimes(nearSaw) + " nearby");

        // Putting buds back in is no opening.
        Players.click(context, opener.player(), jarPos, opener.player().getMainHandItem());
        context.assertTrue(puffs(near.sent()).isEmpty(), "putting buds into a jar let off a puff");

        // An empty jar opened gives nothing and smells of nothing.
        jar.setContents(StashContents.EMPTY);
        Players.click(context, opener.player(), jarPos, ItemStack.EMPTY);
        context.assertTrue(puffs(opener.sent()).isEmpty(), "an empty jar let off a puff");

        // A spoiled jar reeks in stale hay whatever comes out of it.
        jar.setContents(jar(schwag(64), buds(ModItems.INDICA_BUDS, 64, BudTier.EPIC)));
        Players.click(context, opener.player(), jarPos, ItemStack.EMPTY);
        List<Packet<?>> spoiled = opener.sent();
        List<TerpenePuffOption> rotten = puffs(spoiled);
        context.assertTrue(chimes(spoiled) == 0, "a rotten jar opened chimed");
        context.assertTrue(rotten.equals(List.of(new TerpenePuffOption(TerpenePuff.STALE_HAY, StashNose.ROTTEN))),
                "64 Epic over 64 schwag opened let off " + rotten);
        context.succeed();
    }

    private static void clear(Players.Watched... players) {
        for (Players.Watched player : players) {
            player.sent();
        }
    }

    private static long chimes(List<Packet<?>> sent) {
        return sent.stream().filter(packet -> packet instanceof ClientboundSoundPacket sound
                && sound.getSound().value() == SoundEvents.AMETHYST_BLOCK_CHIME).count();
    }

    private static List<TerpenePuffOption> puffs(List<Packet<?>> sent) {
        return sent.stream()
                .filter(packet -> packet instanceof ClientboundLevelParticlesPacket)
                .map(packet -> ((ClientboundLevelParticlesPacket) packet).getParticle())
                .filter(particle -> particle instanceof TerpenePuffOption)
                .map(particle -> (TerpenePuffOption) particle)
                .toList();
    }

    private static void expect(GameTestHelper context, String what, StashNose nose, ItemStack... stacks) {
        StashNose got = StashNose.of(jar(stacks));
        context.assertTrue(got == nose, what + " smells " + got + ", not " + nose);
    }

    private static StashContents jar(ItemStack... stacks) {
        StashContents.Mutable stash = StashContents.EMPTY.mutable();
        for (ItemStack stack : stacks) {
            stash.insert(stack.copy(), Integer.MAX_VALUE);
        }
        return stash.toImmutable();
    }

    private static ItemStack buds(Item item, int count, BudTier tier) {
        return BudTier.stamp(new ItemStack(item, count), tier);
    }

    private static ItemStack schwag(int count) {
        return new ItemStack(ModItems.SCHWAG, count);
    }
}
