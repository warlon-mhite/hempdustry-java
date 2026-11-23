package com.warlonmhite.hempdustry.test;

import net.minecraft.network.chat.Component;
import com.warlonmhite.hempdustry.strain.ResinStep;
import com.warlonmhite.hempdustry.strain.internal.Resins;
import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.warlonmhite.hempdustry.compat.ViewerRecipes;
import com.warlonmhite.hempdustry.recipe.ModRecipes;
import com.warlonmhite.hempdustry.recipe.SiftingRecipe;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import com.warlonmhite.hempdustry.block.ModBlocks;
import com.warlonmhite.hempdustry.block.custom.SiftingBoxBlock;
import com.warlonmhite.hempdustry.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Dry Sifter's whole cycle, and the two ways it could quietly pay out wrong.
 *
 * <p>Both failures this guards are invisible in play. <b>A screen that does not charge for what it
 * takes</b> looks exactly like a screen that does, because the plant matter is going in either way
 * and nobody counts their trim. And <b>a full screen that can be collected twice</b> is a
 * duplication bug that presents as good luck: the second right-click would hand over a second lump
 * and the level would already be back at zero, so there is nothing on screen to notice.
 *
 * <p>Buds rather than leaf on purpose: {@code FLOWER_CHANCE} is 1.0, so the fill is exactly
 * {@code FULL_LEVEL} uses and the test has no randomness in it at all. The trim rate is a balance
 * number, not a mechanism, and a test that rolled dice for it would be measuring the RNG.
 */
public final class SiftingBoxGameTest {

    private static final BlockPos BOX = new BlockPos(1, 1, 1);
    /** Comfortably past {@code PRESS_DELAY}, which is 20. */
    private static final int READY_BY_TICK = 40;

    /**
     * A real right-click on the screen, through {@code ServerPlayerGameMode}.
     *
     * <p><b>This has to be the interaction manager and nothing else.</b> The bug this test exists
     * for — the block refusing a click with {@code InteractionResult.PASS}, which swallows it so
     * {@code useWithoutItem} never runs and a full screen can never be emptied — is invisible to both of the
     * convenient ways of faking a click. Calling {@code useWithoutItem} directly never asks the question, and
     * {@code GameTestHelper#useBlock} falls through to {@code useWithoutItem} on <em>any</em> non-accepted
     * result where the real manager insists on {@code TryEmptyHandInteraction} specifically. Both
     * were perfectly happy with the broken block while a player could not get the bar out.
     */
    private static InteractionResult rightClick(GameTestHelper context, ItemStack held) {
        return Players.click(context, Players.survival(context), BOX, held);
    }

    public static void dryScreenFillsAndYieldsKief(GameTestHelper context) {
        context.setBlock(BOX, ModBlocks.SIFTING_BOX.defaultBlockState());

        ServerLevel world = context.getLevel();
        BlockPos pos = context.absolutePos(BOX);
        Player player = context.makeMockPlayer(GameType.SURVIVAL);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);

        // A pickaxe, not a block: right-clicking with a BlockItem legitimately *places* it, which
        // is vanilla behaviour and would be measuring the wrong thing.
        ItemStack notPlantMatter = new ItemStack(Items.IRON_PICKAXE);
        rightClick(context, notPlantMatter);
        context.assertValueEqual(context.getBlockState(BOX).getValue(SiftingBoxBlock.LEVEL), 0,
                "the screen took something that is not plant matter");

        ItemStack buds = new ItemStack(ModItems.INDICA_BUDS, 64);
        for (int i = 0; i < SiftingBoxBlock.FULL_LEVEL; i++) {
            world.getBlockState(pos).useItemOn(buds, world, player, InteractionHand.MAIN_HAND, hit);
        }
        context.assertValueEqual(context.getBlockState(BOX).getValue(SiftingBoxBlock.LEVEL),
                SiftingBoxBlock.FULL_LEVEL,
                "buds fill at chance 1.0, so " + SiftingBoxBlock.FULL_LEVEL + " of them should fill the screen");
        context.assertValueEqual(buds.getCount(), 64 - SiftingBoxBlock.FULL_LEVEL,
                "the screen did not charge for exactly what it took");

        // A full screen takes nothing more: the powder is waiting to be pressed, not to be topped up.
        world.getBlockState(pos).useItemOn(buds, world, player, InteractionHand.MAIN_HAND, hit);
        context.assertValueEqual(buds.getCount(), 64 - SiftingBoxBlock.FULL_LEVEL,
                "a full screen went on eating buds");

        context.runAtTickTime(READY_BY_TICK, () -> {
            context.assertValueEqual(context.getBlockState(BOX).getValue(SiftingBoxBlock.LEVEL),
                    SiftingBoxBlock.READY_LEVEL,
                    "the scheduled tick never pressed the powder into a lump");

            // The reported bug, driven exactly as a player drives it: a real right-click, holding
            // something the screen will not take. Empty-handed works too, but this is the case that
            // was broken — and the case a player is in, since they arrive holding trim.
            rightClick(context, new ItemStack(Items.IRON_PICKAXE));
            context.assertValueEqual(context.getBlockState(BOX).getValue(SiftingBoxBlock.LEVEL), 0,
                    "collecting the lump did not empty the screen");

            // And it is empty for real: a second collection must produce nothing.
            rightClick(context, ItemStack.EMPTY);

            context.runAfterDelay(2, () -> {
                // Nine loose pieces of KIEF, and NOT a bar. This block does not press; that is the
                // Hemp Press's job and the whole reason the two are separate blocks.
                context.assertItemEntityCountIs(ModItems.KIEF, BOX, 3.0, SiftingBoxBlock.YIELD);
                context.assertItemEntityNotPresent(ModBlocks.HASHISH_BAR.asItem(), BOX, 3.0);
                context.assertItemEntityNotPresent(ModItems.BUBBLE_HASH, BOX, 3.0);
                context.succeed();
            });
        });
    }

    /**
     * The water changes the product, and the jacket is what makes the wash run at all.
     *
     * <p><b>Both halves fail silently.</b> A jacket check that is missing or inverted turns the box
     * into a free upgrade — no error, no message, nothing on screen that looks wrong. And a jacket
     * that melts <em>while the player is using it</em>, because a torch went up, is the failure the
     * design actually invites: vanilla melts the ice, the box quietly refuses, and the only tell is
     * that the frost stopped.
     *
     * <p>Blue ice throughout, so the rate is 1.0 and every roll is a certainty. The tiered rates are
     * balance numbers; a test that rolled them would be measuring the RNG rather than the mechanism.
     */
    public static void iceWashNeedsItsJacket(GameTestHelper context) {
        context.setBlock(BOX, ModBlocks.SIFTING_BOX.defaultBlockState());

        ServerLevel world = context.getLevel();
        BlockPos pos = context.absolutePos(BOX);
        Player player = context.makeMockPlayer(GameType.SURVIVAL);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        ItemStack buds = new ItemStack(ModItems.INDICA_BUDS, 64);

        // A real right-click with a bucket. The box is empty, so it takes the water.
        rightClick(context, new ItemStack(Items.WATER_BUCKET));
        context.assertTrue(context.getBlockState(BOX).getValue(SiftingBoxBlock.FILLED),
                "a water bucket did not fill the box");

        // Filled but not jacketed: the wash cannot run, and the refusal must PASS THE CLICK ON
        // rather than swallow it -- see sifterRefusesAnUnjacketedWash's sibling case below.
        InteractionResult bare = world.getBlockState(pos).useItemOn(buds, world, player, InteractionHand.MAIN_HAND, hit);
        context.assertTrue(bare instanceof InteractionResult.TryEmptyHandInteraction,
                "an unjacketed wash swallowed the click instead of passing it on");
        context.assertValueEqual(context.getBlockState(BOX).getValue(SiftingBoxBlock.LEVEL), 0,
                "a filled box with no ice around it washed a bud");
        context.assertValueEqual(buds.getCount(), 64, "an unjacketed box charged for a bud it refused");

        // Jacket it in blue ice and the same box runs at rate 1.0.
        for (Direction side : Direction.Plane.HORIZONTAL) {
            context.setBlock(BOX.relative(side), Blocks.BLUE_ICE.defaultBlockState());
        }
        for (int i = 0; i < SiftingBoxBlock.FULL_LEVEL - 1; i++) {
            world.getBlockState(pos).useItemOn(buds, world, player, InteractionHand.MAIN_HAND, hit);
        }
        context.assertValueEqual(context.getBlockState(BOX).getValue(SiftingBoxBlock.LEVEL),
                SiftingBoxBlock.FULL_LEVEL - 1,
                "a blue-ice jacket is rate 1.0, so every bud should have advanced the box");

        // ...and now the failure the design invites: the jacket melts mid-batch. One side to air is
        // what a torch leaves behind after vanilla's IceBlock melts.
        context.setBlock(BOX.north(), Blocks.AIR.defaultBlockState());
        InteractionResult melted = world.getBlockState(pos)
                .useItemOn(buds, world, player, InteractionHand.MAIN_HAND, hit);
        context.assertTrue(melted instanceof InteractionResult.TryEmptyHandInteraction,
                "a box whose jacket melted swallowed the click");
        context.assertValueEqual(context.getBlockState(BOX).getValue(SiftingBoxBlock.LEVEL),
                SiftingBoxBlock.FULL_LEVEL - 1,
                "the wash went on running with a hole in its jacket");

        context.setBlock(BOX.north(), Blocks.BLUE_ICE.defaultBlockState());
        world.getBlockState(pos).useItemOn(buds, world, player, InteractionHand.MAIN_HAND, hit);
        context.assertValueEqual(context.getBlockState(BOX).getValue(SiftingBoxBlock.LEVEL),
                SiftingBoxBlock.FULL_LEVEL,
                "the box did not resume once its jacket was repaired");

        context.runAtTickTime(READY_BY_TICK, () -> {
            rightClick(context, ItemStack.EMPTY);
            // The water is spent WITH the batch, not before it: a finished wash has to still
            // remember which process made it, and FILLED is how it does.
            context.assertTrue(!context.getBlockState(BOX).getValue(SiftingBoxBlock.FILLED),
                    "collecting the batch did not spend the box's water");

            context.runAfterDelay(2, () -> {
                // The washed powder, and never the dry one. Same box, same inputs, different product
                // -- which is the entire reason the two blocks became one.
                context.assertItemEntityCountIs(ModItems.BUBBLE_HASH, BOX, 3.0, SiftingBoxBlock.YIELD);
                context.assertItemEntityNotPresent(ModItems.KIEF, BOX, 3.0);
                context.succeed();
            });
        });
    }

    /**
     * The re-sift: <b>plant matter and powder do not share a screen, and a wash takes neither of
     * them but plant.</b>
     *
     * <p>Three things break silently here. If the {@code CONTENT} check is missing or inverted, a
     * player tops up a half-full plant screen with kief and gets a batch out of a load that was part
     * powder — <b>no error, and the wrong item</b>. If the wash accepted kief there would be a third
     * road to a product that already has two, and one of the three would inevitably be a trap. And
     * if a refusal returns {@code InteractionResult.PASS} rather than {@code super}, it <em>swallows</em>
     * the click — the box stops accepting anything and can never be emptied, which is precisely the
     * bug that shipped in this block once already and was found by a player.
     *
     * <p>So this asserts the returned {@code InteractionResult} as well as the level: a block that
     * silently ignores a click and one that correctly passes it on are indistinguishable from the
     * world state alone, and only one of them still lets {@code useWithoutItem} run.
     */
    public static void screenRefusesAMixedLoad(GameTestHelper context) {
        context.setBlock(BOX, ModBlocks.SIFTING_BOX.defaultBlockState());

        ServerLevel world = context.getLevel();
        BlockPos pos = context.absolutePos(BOX);
        Player player = context.makeMockPlayer(GameType.SURVIVAL);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);

        // Two buds in: the screen is now a PLANT screen, and stamped as one.
        ItemStack buds = new ItemStack(ModItems.INDICA_BUDS, 64);
        world.getBlockState(pos).useItemOn(buds, world, player, InteractionHand.MAIN_HAND, hit);
        world.getBlockState(pos).useItemOn(buds, world, player, InteractionHand.MAIN_HAND, hit);
        context.assertValueEqual(context.getBlockState(BOX).getValue(SiftingBoxBlock.LEVEL), 2,
                "two buds did not fill two levels");
        context.assertValueEqual(context.getBlockState(BOX).getValue(SiftingBoxBlock.CONTENT),
                SiftingBoxBlock.Content.PLANT, "the first bud did not stamp the screen as PLANT");

        ItemStack kief = new ItemStack(ModItems.KIEF, 64);
        InteractionResult refused = world.getBlockState(pos)
                .useItemOn(kief, world, player, InteractionHand.MAIN_HAND, hit);
        context.assertTrue(refused instanceof InteractionResult.TryEmptyHandInteraction,
                "a refused load must pass the click on, not swallow it with PASS");
        context.assertValueEqual(context.getBlockState(BOX).getValue(SiftingBoxBlock.LEVEL), 2,
                "kief went onto a plant screen");
        context.assertValueEqual(kief.getCount(), 64, "the screen charged for a load it refused");

        // ...and the same screen still takes plant matter, so the refusal cost nothing.
        world.getBlockState(pos).useItemOn(buds, world, player, InteractionHand.MAIN_HAND, hit);
        context.assertValueEqual(context.getBlockState(BOX).getValue(SiftingBoxBlock.LEVEL), 3,
                "the refusal left the screen unable to take its own content kind");

        // A fresh box takes kief happily, and stamps itself KIEF.
        context.setBlock(BOX, ModBlocks.SIFTING_BOX.defaultBlockState());
        for (int i = 0; i < 40 && context.getBlockState(BOX).getValue(SiftingBoxBlock.LEVEL) == 0; i++) {
            world.getBlockState(pos).useItemOn(kief, world, player, InteractionHand.MAIN_HAND, hit);
        }
        context.assertValueEqual(context.getBlockState(BOX).getValue(SiftingBoxBlock.CONTENT),
                SiftingBoxBlock.Content.KIEF,
                "kief did not stamp an empty screen as KIEF — the re-sift is unreachable");

        // A WASH TAKES PLANT MATTER AND NOTHING ELSE. Kief into a filled box must be refused, or
        // there is a third road to filtered hashish and one of the three becomes a trap.
        context.setBlock(BOX, ModBlocks.SIFTING_BOX.defaultBlockState()
                .setValue(SiftingBoxBlock.FILLED, true));
        for (Direction side : Direction.Plane.HORIZONTAL) {
            context.setBlock(BOX.relative(side), Blocks.BLUE_ICE.defaultBlockState());
        }
        ItemStack washKief = new ItemStack(ModItems.KIEF, 64);
        InteractionResult wet = world.getBlockState(pos)
                .useItemOn(washKief, world, player, InteractionHand.MAIN_HAND, hit);
        context.assertTrue(wet instanceof InteractionResult.TryEmptyHandInteraction,
                "a wash refusing kief swallowed the click");
        context.assertValueEqual(context.getBlockState(BOX).getValue(SiftingBoxBlock.LEVEL), 0,
                "the ice wash accepted kief — that is a third road to filtered hashish");
        context.assertValueEqual(washKief.getCount(), 64, "the wash charged for kief it refused");

        // The control: the same jacketed, filled box still takes buds, so the refusal above is
        // about the kief and not about the box being broken.
        world.getBlockState(pos).useItemOn(buds, world, player, InteractionHand.MAIN_HAND, hit);
        context.assertValueEqual(context.getBlockState(BOX).getValue(SiftingBoxBlock.LEVEL), 1,
                "the wash refused plant matter too, so the refusal above proves nothing");

        // ---- and the re-sift actually pays out FILTERED kief ----
        //
        // THIS ASSERTION IS THE POINT OF THE WHOLE RE-SIFT, and it was missing on the first pass:
        // three mutations were run against this test and the one that made the re-sift yield plain
        // kief PASSED, because nothing here had ever collected a KIEF batch. Everything above tests
        // what the screen REFUSES; without this, what it produces was covered by nothing.
        //
        // Set to READY_LEVEL directly rather than filled by hand, because KIEF_CHANCE is a rate:
        // filling it for real would be measuring the RNG, and what is under test is which branch
        // collect() takes.
        context.setBlock(BOX, ModBlocks.SIFTING_BOX.defaultBlockState()
                .setValue(SiftingBoxBlock.LEVEL, SiftingBoxBlock.READY_LEVEL)
                .setValue(SiftingBoxBlock.CONTENT, SiftingBoxBlock.Content.KIEF));
        rightClick(context, ItemStack.EMPTY);
        context.assertValueEqual(context.getBlockState(BOX).getValue(SiftingBoxBlock.LEVEL), 0,
                "collecting the re-sift did not empty the screen");
        // ...and the screen goes back to taking plant matter, so a powder batch does not strand it.
        context.assertValueEqual(context.getBlockState(BOX).getValue(SiftingBoxBlock.CONTENT),
                SiftingBoxBlock.Content.PLANT,
                "an emptied screen kept its KIEF content kind and now refuses buds");

        context.runAfterDelay(2, () -> {
            context.assertItemEntityCountIs(ModItems.FILTERED_KIEF, BOX, 3.0, SiftingBoxBlock.YIELD);
            // Not plain kief, which is the mutation that slipped through, and not bubble hash --
            // a dry screen cannot make that however fine its mesh, because "bubble" IS the water.
            context.assertItemEntityNotPresent(ModItems.KIEF, BOX, 3.0);
            context.assertItemEntityNotPresent(ModItems.BUBBLE_HASH, BOX, 3.0);
            context.succeed();
        });
    }

    /**
     * A water bucket the box will not take is turned down with an accepted result, so the click ends
     * there; a ready box still hands its batch to whoever holds one.
     *
     * <p>The pour itself happens on the client, which goes on to the bucket's own use after any
     * result that is not accepted — so the result is what this asserts. Two refusals: a box that
     * already holds water, and a dry screen part-way through a batch.
     */
    public static void aRefusedBucketIsNotPoured(GameTestHelper context) {
        context.setBlock(BOX, ModBlocks.SIFTING_BOX.defaultBlockState().setValue(SiftingBoxBlock.FILLED, true));
        InteractionResult full = rightClick(context, new ItemStack(Items.WATER_BUCKET));
        context.assertTrue(full.consumesAction(), "a box already holding water passed a water bucket on, so it pours: " + full);

        context.setBlock(BOX, ModBlocks.SIFTING_BOX.defaultBlockState().setValue(SiftingBoxBlock.LEVEL, 3));
        InteractionResult midBatch = rightClick(context, new ItemStack(Items.WATER_BUCKET));
        context.assertTrue(midBatch.consumesAction(), "a part-sifted dry screen passed a water bucket on, so it pours: " + midBatch);
        context.assertFalse(context.getBlockState(BOX).getValue(SiftingBoxBlock.FILLED),
                "a part-sifted dry screen took water");

        context.setBlock(BOX, ModBlocks.SIFTING_BOX.defaultBlockState().setValue(SiftingBoxBlock.LEVEL, SiftingBoxBlock.READY_LEVEL));
        rightClick(context, new ItemStack(Items.WATER_BUCKET));
        context.assertValueEqual(0, context.getBlockState(BOX).getValue(SiftingBoxBlock.LEVEL),
                "a ready box kept its batch from a player holding a water bucket");

        // And played out the way the client plays it, by a player looking down at the box: the water
        // a full box turned down is not then poured on top of it.
        context.setBlock(BOX, ModBlocks.SIFTING_BOX.defaultBlockState().setValue(SiftingBoxBlock.FILLED, true));
        BlockPos above = context.absolutePos(BOX).above();
        ServerPlayer player = Players.survival(context);
        player.snapTo(above.getX() + 0.5, above.getY(), above.getZ() + 0.5, 0, 90);
        Players.clickLikeAClient(context, player, BOX, new ItemStack(Items.WATER_BUCKET));
        context.assertFalse(context.getLevel().getFluidState(above).is(FluidTags.WATER),
                "a full box turned a water bucket down and the bucket poured on top of it");
        context.succeed();
    }

    /**
     * A box that becomes full by any road but a sift still settles. A piston push drops the tick the
     * last sift scheduled, and the box then sat full for ever; a command or a structure leaves it the
     * same way. Placed full here, which is the same road a pushed block takes back into the world.
     */
    public static void aFullBoxSettlesHoweverItArrived(GameTestHelper context) {
        context.setBlock(BOX, ModBlocks.SIFTING_BOX.defaultBlockState().setValue(SiftingBoxBlock.LEVEL, SiftingBoxBlock.FULL_LEVEL));
        context.runAtTickTime(READY_BY_TICK, () -> {
            context.assertValueEqual(SiftingBoxBlock.READY_LEVEL, context.getBlockState(BOX).getValue(SiftingBoxBlock.LEVEL),
                    "a box placed full never settled");
            context.succeed();
        });
    }

    /**
     * The mod's own {@code hempdustry:sifting} recipes are the four rates the box ran on before they were data,
     * and the box reads them: buds 1 level (and 1 a resin step), Beldía's resinous buds 2 (1 a step), fan leaf
     * 0.15, kief 0.25 on the kief screen, each over its {@code #hempdustry:siftable/*} tag; a stick, which no
     * recipe names, nothing. Two recipes naming one piece give it the one with the most levels, and with no
     * recipe at all the box takes nothing. The viewers' dry rows are these four, in this order, with today's
     * counts per batch (7, 4, 47, 28).
     */
    public static void theShippedSiftingRecipesAreTodays(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        record Want(String tag, SiftingBoxBlock.Content screen, float levels, Optional<Float> resinLevels, int perBatch) {
        }
        Map<String, Want> want = new LinkedHashMap<>();
        want.put("hempdustry:sifting/flower", new Want("#hempdustry:siftable/flower", SiftingBoxBlock.Content.PLANT, 1.0F, Optional.of(1.0F), 7));
        want.put("hempdustry:sifting/resinous", new Want("#hempdustry:siftable/resinous", SiftingBoxBlock.Content.PLANT, 2.0F, Optional.of(1.0F), 4));
        want.put("hempdustry:sifting/trim", new Want("#hempdustry:siftable/trim", SiftingBoxBlock.Content.PLANT, 0.15F, Optional.empty(), 47));
        want.put("hempdustry:sifting/kief", new Want("#hempdustry:siftable/kief", SiftingBoxBlock.Content.KIEF, 0.25F, Optional.empty(), 28));

        DynamicOps<JsonElement> json = world.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        Map<String, SiftingRecipe> shipped = new TreeMap<>();
        for (RecipeHolder<SiftingRecipe> holder : ModRecipes.allOfType(world, ModRecipes.SIFTING_TYPE)) {
            if (Sweep.ours(holder.id().identifier())) shipped.put(holder.id().identifier().toString(), holder.value());
        }
        context.assertValueEqual(shipped.keySet(), new TreeSet<>(want.keySet()), "the mod's sifting recipes");
        for (Map.Entry<String, Want> entry : want.entrySet()) {
            SiftingRecipe recipe = shipped.get(entry.getKey());
            Want expected = entry.getValue();
            String tag = Ingredient.CODEC.encodeStart(json, recipe.ingredient()).getOrThrow().getAsString();
            context.assertTrue(tag.equals(expected.tag()) && recipe.screen() == expected.screen()
                            && recipe.levels() == expected.levels() && recipe.resinLevels().equals(expected.resinLevels()),
                    entry.getKey() + " is " + tag + " " + recipe + ", not " + expected);
        }
        // The constants the recipe provider writes them from are the same numbers.
        context.assertTrue(SiftingBoxBlock.FLOWER_CHANCE == 1.0F && SiftingBoxBlock.RESINOUS_CHANCE == 2.0F
                        && SiftingBoxBlock.TRIM_CHANCE == 0.15F && SiftingBoxBlock.KIEF_CHANCE == 0.25F,
                "the recipe provider's rates moved from the ones the box shipped with");

        Map<ItemStack, Float> sieves = new LinkedHashMap<>();
        sieves.put(new ItemStack(ModItems.INDICA_BUDS), 1.0F);
        sieves.put(new ItemStack(ModItems.BELDIA_BUDS), 2.0F);
        sieves.put(new ItemStack(ModItems.HEMP_LEAF), 0.15F);
        sieves.put(new ItemStack(ModItems.KIEF), 0.25F);
        sieves.put(new ItemStack(Items.STICK), 0.0F);
        for (Map.Entry<ItemStack, Float> entry : sieves.entrySet()) {
            float chance = SiftingBoxBlock.sieveChance(entry.getKey(), world);
            context.assertTrue(chance == entry.getValue(), entry.getKey() + " sieves at " + chance + ", not " + entry.getValue());
        }

        ItemStack beldia = new ItemStack(ModItems.BELDIA_BUDS);
        RecipeHolder<SiftingRecipe> low = holder("a", new SiftingRecipe(Ingredient.of(ModItems.BELDIA_BUDS),
                SiftingBoxBlock.Content.PLANT, 1.0F, Optional.empty()));
        RecipeHolder<SiftingRecipe> high = holder("b", new SiftingRecipe(Ingredient.of(ModItems.BELDIA_BUDS),
                SiftingBoxBlock.Content.PLANT, 2.0F, Optional.empty()));
        net.minecraft.core.HolderLookup.Provider registries = world.registryAccess();
        context.assertValueEqual(SiftingRecipe.pick(List.of(low, high), beldia, registries), Optional.of(high),
                "a bud two recipes name took the one with fewer levels");
        context.assertValueEqual(SiftingRecipe.pick(List.of(), beldia, registries), Optional.empty(),
                "with no sifting recipe the box still sifts something");
        // A bud with a resin step ranks the recipes naming it by what it would sieve at by each: under the mod's one
        // level a step and a pack's flat 1.2, a rich bud (1.25) keeps the mod's, a very low one (0.5) and a bare one (1)
        // take the pack's.
        RecipeHolder<SiftingRecipe> stepped = holder("c", new SiftingRecipe(Ingredient.of(ModItems.INDICA_BUDS),
                SiftingBoxBlock.Content.PLANT, 1.0F, Optional.of(1.0F)));
        RecipeHolder<SiftingRecipe> flat = holder("d", new SiftingRecipe(Ingredient.of(ModItems.INDICA_BUDS),
                SiftingBoxBlock.Content.PLANT, 1.2F, Optional.empty()));
        context.assertValueEqual(SiftingRecipe.pick(List.of(stepped, flat),
                        Resins.stamp(new ItemStack(ModItems.INDICA_BUDS), ResinStep.RICH), registries), Optional.of(stepped),
                "a rich bud's recipe, of one reading its step and a flat one");
        context.assertValueEqual(SiftingRecipe.pick(List.of(stepped, flat),
                        Resins.stamp(new ItemStack(ModItems.INDICA_BUDS), ResinStep.VERY_LOW), registries), Optional.of(flat),
                "a very low bud's recipe, of one reading its step and a flat one");
        context.assertValueEqual(SiftingRecipe.pick(List.of(stepped, flat), new ItemStack(ModItems.INDICA_BUDS), registries),
                Optional.of(flat), "a bare bud's recipe, of one reading its step and a flat one");

        List<ViewerRecipes.Entry> rows = ViewerRecipes.sifting(world).stream()
                .filter(row -> Sweep.ours(row.id())).toList();
        context.assertValueEqual(rows.stream().map(row -> row.id().toString()).toList(), List.copyOf(want.keySet()),
                "the dry sifting rows");
        for (ViewerRecipes.Entry row : rows) {
            Object count = ((TranslatableContents) row.notes().getFirst().getContents()).getArgs()[0];
            context.assertValueEqual(count, want.get(row.id().toString()).perBatch(), row.id() + "'s pieces per batch");
            // A row whose recipe reads a bud's step tells the stepped buds' own counts, from its resin_levels: about
            // 4 to 14 at one level a step, the resinous row's very rich dry Beldía included, never its levels' 2 to 8.
            List<String> resin = row.notes().stream().map(Component::getContents)
                    .filter(contents -> contents instanceof TranslatableContents translatable
                            && translatable.getKey().equals("hempdustry.category.sifting.resin"))
                    .map(contents -> List.of(((TranslatableContents) contents).getArgs()).toString()).toList();
            context.assertValueEqual(resin, want.get(row.id().toString()).resinLevels().isPresent() ? List.of("[4, 14]") : List.of(),
                    row.id() + "'s stepped buds per batch");
        }
        context.succeed();
    }

    /**
     * A datapack's {@code hempdustry:sifting} recipe moves the box: the game tests' own pack puts wheat, which
     * no recipe of the mod names, on the plant screen at seven levels a piece, so one wheat fills a batch that
     * settles into nine kief, and both viewers' pages show it, dry and in each jacket.
     */
    public static void aDatapackSiftingRecipeMovesTheBox(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        // The pack's dead bush recipe moves the screen no levels, plain or stepped: the box refuses it and says why,
        // and the viewers give it no row.
        context.setBlock(BOX, ModBlocks.SIFTING_BOX.defaultBlockState());
        Players.Watched watched = Players.watched(context);
        watched.sent();
        Players.click(context, watched.player(), BOX, new ItemStack(Items.DEAD_BUSH, 2));
        context.assertValueEqual(context.getBlockState(BOX).getValue(SiftingBoxBlock.LEVEL), 0, "a dead bush at no levels moved the screen");
        context.assertTrue(watched.sent().stream().anyMatch(packet -> packet.toString().contains("hempdustry.sifting_box.too_little_resin")),
                "a piece at no levels was refused without a word");
        context.assertFalse(ViewerRecipes.sifting(world).stream().anyMatch(row -> row.id().getPath().endsWith("no_resin")),
                "a recipe at no levels has a row");

        context.assertValueEqual(SiftingBoxBlock.sieveChance(new ItemStack(Items.WHEAT), world), 7.0F,
                "the test pack's wheat recipe");
        context.setBlock(BOX, ModBlocks.SIFTING_BOX.defaultBlockState());
        ItemStack wheat = new ItemStack(Items.WHEAT, 3);
        rightClick(context, wheat);
        context.assertValueEqual(context.getBlockState(BOX).getValue(SiftingBoxBlock.LEVEL), SiftingBoxBlock.FULL_LEVEL,
                "one wheat at seven levels did not fill the screen");
        context.assertValueEqual(wheat.getCount(), 2, "the box did not take exactly one wheat");

        List<String> dry = ViewerRecipes.sifting(world).stream().map(row -> row.id().toString()).toList();
        List<String> wet = ViewerRecipes.iceOLator(world).stream().map(row -> row.id().toString()).toList();
        context.assertTrue(dry.contains("hempdustry-gametest:sifting/wheat"), "no dry row for the pack's recipe: " + dry);
        context.assertTrue(wet.containsAll(List.of("hempdustry-gametest:ice_o_lator/wheat/blue_ice",
                        "hempdustry-gametest:ice_o_lator/wheat/packed_ice", "hempdustry-gametest:ice_o_lator/wheat/ice")),
                "no wash rows for the pack's recipe: " + wet);
        context.assertFalse(wet.stream().anyMatch(id -> id.contains("/kief/")), "a kief recipe has wash rows: " + wet);

        context.runAtTickTime(READY_BY_TICK, () -> {
            rightClick(context, ItemStack.EMPTY);
            context.runAfterDelay(2, () -> {
                context.assertItemEntityCountIs(ModItems.KIEF, BOX, 3.0, SiftingBoxBlock.YIELD);
                context.succeed();
            });
        });
    }

    private static RecipeHolder<SiftingRecipe> holder(String name, SiftingRecipe recipe) {
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath("hempdustry-gametest", name)), recipe);
    }
}
