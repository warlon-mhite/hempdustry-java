package com.warlonmhite.hempdustry.datagen;

import com.warlonmhite.hempdustry.Hempdustry;
import com.warlonmhite.hempdustry.entity.ModEntities;
import com.warlonmhite.hempdustry.advancement.EdibleKicksInCriterion;
import com.warlonmhite.hempdustry.advancement.HarvestHempCriterion;
import com.warlonmhite.hempdustry.advancement.ModCriteria;
import com.warlonmhite.hempdustry.advancement.SmokeCriterion;
import com.warlonmhite.hempdustry.advancement.StashContentsPredicate;
import com.warlonmhite.hempdustry.block.ModBlocks;
import com.warlonmhite.hempdustry.block.custom.Defoliation;
import com.warlonmhite.hempdustry.block.custom.GrowLight;
import com.warlonmhite.hempdustry.block.custom.IndicaCropBlock;
import com.warlonmhite.hempdustry.component.ModComponents;
import com.warlonmhite.hempdustry.item.ModItems;
import com.warlonmhite.hempdustry.item.custom.Quality;
import com.warlonmhite.hempdustry.strain.ModStrains;
import com.warlonmhite.hempdustry.util.ModTags;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.predicates.BlockPredicate;
import net.minecraft.advancements.triggers.ConsumeItemTrigger;
import net.minecraft.advancements.predicates.DataComponentMatchers;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.advancements.triggers.ItemDurabilityTrigger;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.triggers.ItemUsedOnLocationTrigger;
import net.minecraft.advancements.predicates.LocationPredicate;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.triggers.PlayerInteractTrigger;
import net.minecraft.advancements.triggers.PlayerTrigger;
import net.minecraft.advancements.predicates.StatePropertiesPredicate;
import net.minecraft.advancements.triggers.TameAnimalTrigger;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.world.entity.EntityType;
import net.minecraft.resources.ResourceKey;
import com.warlonmhite.hempdustry.strain.Strain;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.advancements.predicates.FoodPredicate;
import net.minecraft.advancements.predicates.MobEffectsPredicate;
import net.minecraft.advancements.predicates.entity.PlayerPredicate;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemEntityPropertyCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ModAdvancementProvider extends FabricAdvancementProvider {
    // Every challenge pays experience on vanilla's scale, as every vanilla challenge but two does (26.3's
    // data/minecraft/advancement: 40 to 50 for a skill or timing feat or a small collection, Blowback 40,
    // Bullseye and A Complete Catalogue 50; 100 for a long collection, A Balanced Diet and Two by Two).
    /** Every strain's buds in a Stash Jar: a long collection, as A Balanced Diet. */
    private static final int APOTHECARY_XP = 100;
    /** Five resins from three chains: a small collection, as A Complete Catalogue. */
    private static final int AROUND_THE_WORLD_XP = 50;
    /** A flawless batch: a precise feat, as Bullseye. */
    private static final int PERFECT_BATCH_XP = 50;
    /** A hit at the right minute of the day: a timed feat, easy once known, as Blowback. */
    private static final int TIMED_HIT_XP = 40;

    private static AdvancementRewards.Builder challenge(int experience) {
        return AdvancementRewards.Builder.experience(experience);
    }


    /**
     * Background for every advancement in the tree: the hempcrete powder block, tiled the way
     * vanilla tiles its own block-like tab backgrounds. Kept in one constant so a change is one line.
     *
     * <p><b>This is an asset id, not a file path.</b> Since 1.21.9 the field is a
     * {@link ClientAsset.ResourceTexture}, which expands what it is given to
     * {@code textures/<path>.png} — so it wants {@code hempdustry:block/hempcrete_powder_block},
     * and the full path the old {@code Identifier} carried would resolve to
     * {@code textures/textures/block/….png.png}. That misses, and a missing advancement background
     * is drawn as the magenta-and-black missing texture rather than logged.
     */
    private static final ClientAsset.ResourceTexture BACKGROUND = new ClientAsset.ResourceTexture(
            Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "block/hempcrete_powder_block"));

    /**
     * The Strains tab's background: vanilla's watered farmland, where most of the strains are grown. Like
     * every tab background it is solid in every pixel; a texture with clear pixels shows holes.
     */
    private static final ClientAsset.ResourceTexture STRAINS_BACKGROUND = new ClientAsset.ResourceTexture(
            Identifier.withDefaultNamespace("block/farmland_moist"));

    /**
     * The Strains tab's rows, each a chain drawn left to right, grouped by where the plants grow: the
     * fields, the wild biomes, the Nether and the End. Vanilla lays siblings out as a column and a chain
     * as a row, so a row is the only way to keep twelve strains to a few lines. A chain orders nothing:
     * an advancement is granted on its own criteria whatever its parent, so every strain is still earned
     * by its own buds in any order. What the chain changes is when a node shows: one not earned yet
     * appears once its parent or the one before that is, so a row uncovers itself as it is walked, as the
     * Nether tab does. Each row starts with its most common strain. A new strain joins a row here; the
     * build refuses a bud strain no row holds.
     */
    private static final List<List<ResourceKey<Strain>>> STRAIN_ROWS = List.of(
            List.of(ModStrains.INDICA, ModStrains.SATIVA, ModStrains.BELDIA, ModStrains.RUDERALIS, ModStrains.DUD_WEED),
            List.of(ModStrains.CHERRY_WINE, ModStrains.WHITE_WIDOW, ModStrains.MAUI_WOWIE, ModStrains.ECHO_OG),
            List.of(ModStrains.WARPED_KUSH, ModStrains.CRIMSON, ModStrains.ENDER_HAZE));

    public ModAdvancementProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(output, registryLookup);
    }

    /**
     * The display block, which is otherwise eight positional arguments repeated twenty-odd times.
     * Title and description are always {@code advancements.hempdustry.<id>.title} / {@code .description},
     * so an advancement can never drift from its lang keys.
     *
     * <p>The three trailing flags are vanilla's {@code showToast} / {@code announceChat} /
     * {@code hidden}; only the last varies here.
     */
    /** Item predicates name their items through a lookup since 1.21.5; this is that lookup. */
    private static HolderGetter<Item> items(HolderLookup.Provider registries) {
        return registries.lookupOrThrow(Registries.ITEM);
    }

    /**
     * The ages at which shearing a hemp crop is a <b>trim</b> — the two defoliation windows, and
     * deliberately not the rub window above them.
     *
     * <p>{@code StatePredicate} can only express an exact match from a mod: its ranged matcher and
     * the {@code Condition} record it lives in are both package-private, so a 3–5 span has to be
     * three criteria OR-merged rather than one predicate. Cheap, and it reads plainly in the
     * generated JSON.
     *
     * <p>Both crops use {@code BlockStateProperties.AGE_7}, so {@link IndicaCropBlock#AGE} stands for the
     * sativa one too and there is one predicate rather than two.
     */
    private static List<Integer> trimAges() {
        List<Integer> ages = new ArrayList<>();
        for (int age = Defoliation.EARLY_MIN_AGE; age <= Defoliation.EARLY_MAX_AGE; age++) {
            ages.add(age);
        }
        for (int age = Defoliation.LATE_MIN_AGE; age <= Defoliation.LATE_MAX_AGE; age++) {
            ages.add(age);
        }
        return ages;
    }

    /** The tab's own root, the one advancement that carries the tab's background since 26.3. */
    private static DisplayInfo rootDisplay(ItemLike icon, String id, AdvancementType frame) {
        return rootDisplay(icon, id, frame, BACKGROUND);
    }

    private static DisplayInfo rootDisplay(ItemLike icon, String id, AdvancementType frame,
                                           ClientAsset.ResourceTexture background) {
        return display(icon, id, frame, false, Optional.of(background));
    }

    private static DisplayInfo display(ItemLike icon, String id, AdvancementType frame,
                                              boolean hidden) {
        // Since 26.3 a child that names a background fails to load: the tab's is the root's alone.
        return display(icon, id, frame, hidden, Optional.empty());
    }

    private static DisplayInfo display(ItemLike icon, String id, AdvancementType frame) {
        return display(icon, id, frame, false);
    }

    private static DisplayInfo display(ItemLike icon, String id, AdvancementType frame, boolean hidden,
                                       Optional<ClientAsset.ResourceTexture> background) {
        return new DisplayInfo(new ItemStackTemplate(icon.asItem()),
                Component.translatable("advancements.hempdustry." + id + ".title"),
                Component.translatable("advancements.hempdustry." + id + ".description"),
                background, frame, true, true, hidden);
    }

    @Override
    public void generateAdvancement(HolderLookup.Provider registryLookup, Consumer<AdvancementHolder> consumer) {

        // Entry point for the whole mod, so it must fire on *any* strain's seed, not just Purple
        // Kush's — hence the #hempdustry:hemp_seeds tag rather than a list of items. (Note
        // TriggerInstance.hasItems(ItemLike...) is an AND: one predicate per item, all required —
        // which is what `chill_set` below wants, and the opposite of what this one does.)
        // A future strain joins the tag in ModItemTagProvider and is picked up here for free.
        AdvancementHolder rootAdvancement = Advancement.Builder.advancement()
                .display(rootDisplay(ModItems.INDICA_SEEDS, "hempdustry", AdvancementType.TASK))
                .addCriterion("has_hemp_seeds", InventoryChangeTrigger.TriggerInstance.hasItems(
                        ItemPredicate.Builder.item().of(items(registryLookup), ModTags.Items.HEMP_SEEDS)))
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "hempdustry"));


        // ---------------------------------------------------------------------
        // The Strains tab
        // ---------------------------------------------------------------------

        // A tab of its own, as vanilla gives a collection one (Husbandry): twelve strains under High
        // Hopes made that tab a column three dozen rows tall. Its root is any strain's buds, through the
        // tag, so a datapack strain adding its buds there opens it too, and the tab first shows with the
        // first buds. Schwag is not in the tag: a bud gone wrong, it keeps its node in High Hopes.
        AdvancementHolder strainGauge = Advancement.Builder.advancement()
                .display(rootDisplay(ModItems.SATIVA_BUDS, "strain_gauge", AdvancementType.TASK, STRAINS_BACKGROUND))
                .addCriterion("has_buds", InventoryChangeTrigger.TriggerInstance.hasItems(
                        ItemPredicate.Builder.item().of(items(registryLookup), ModTags.Items.BUDS)))
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "strain_gauge"));

        // One node per strain, the rows above. Obtaining the buds is the criterion, so a wild plant counts
        // as much as a grown one. Purple Kush, Lemon Haze and Beldía came first and hung under the root of
        // High Hopes; moved here, they keep their ids and criterion names, which is all a save keeps of
        // them. Every newer strain follows the first two, <strain>_strain with has_<buds item>; Beldía's
        // was Kif Country from the start. Dud Weed is hidden: a 1-in-64 sport of Industrial Hemp is a
        // find, not a goal.
        List<ResourceKey<Strain>> placed = STRAIN_ROWS.stream().flatMap(List::stream).toList();
        if (!placed.containsAll(budStrains(registryLookup)) || placed.size() != budStrains(registryLookup).size()) {
            throw new IllegalStateException("Every strain that grows buds needs exactly one place in STRAIN_ROWS: rows "
                    + placed + ", strains " + budStrains(registryLookup));
        }
        for (List<ResourceKey<Strain>> row : STRAIN_ROWS) {
            AdvancementHolder before = strainGauge;
            for (ResourceKey<Strain> key : row) {
                Item buds = Strain.registry(registryLookup).getOrThrow(key).value().buds();
                String id = key == ModStrains.BELDIA ? "kif_country" : ModStrains.id(key) + "_strain";
                String budsId = BuiltInRegistries.ITEM.getKey(buds).getPath();
                before = Advancement.Builder.advancement()
                        .display(display(buds, id, AdvancementType.TASK, key == ModStrains.DUD_WEED))
                        .addCriterion("has_" + budsId, InventoryChangeTrigger.TriggerInstance.hasItems(buds))
                        .parent(before)
                        .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, id));
            }
        }

        // The fourth row: the jar, then the collection in it.
        AdvancementHolder goodStash = Advancement.Builder.advancement()
                .display(display(ModBlocks.STASH_JAR, "good_stash", AdvancementType.TASK))
                .addCriterion("has_stash_jar", InventoryChangeTrigger.TriggerInstance.hasItems(
                        ItemPredicate.Builder.item().of(items(registryLookup), ModBlocks.STASH_JARS.toArray(ItemLike[]::new))))
                .parent(strainGauge)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "good_stash"));

        // Vanilla's A Balanced Diet, for the jar: one criterion per strain, each "a Stash Jar in the
        // inventory holding this strain's buds", so each is earned when it happens and kept, any jar of any
        // tier counts, and the tooltip counts them off. A strain added later adds a criterion and reopens
        // it, as a new biome reopens Adventuring Time. One criterion asking for every strain in one jar at
        // once would show no progress at all.
        Advancement.Builder apothecary = Advancement.Builder.advancement()
                .display(display(ModBlocks.GILDED_STASH_JAR, "apothecary", AdvancementType.CHALLENGE))
                .rewards(challenge(APOTHECARY_XP))
                .parent(goodStash);
        for (ResourceKey<Strain> key : budStrains(registryLookup)) {
            Item buds = Strain.registry(registryLookup).getOrThrow(key).value().buds();
            apothecary.addCriterion("stashed_" + BuiltInRegistries.ITEM.getKey(buds).getPath(),
                    InventoryChangeTrigger.TriggerInstance.hasItems(ItemPredicate.Builder.item()
                            .of(items(registryLookup), ModBlocks.STASH_JARS.toArray(ItemLike[]::new))
                            .withComponents(DataComponentMatchers.Builder.components()
                                    .partial(ModCriteria.STASH_CONTENTS, StashContentsPredicate.holding(
                                            ItemPredicate.Builder.item().of(items(registryLookup), buds).build()))
                                    .build())));
        }
        apothecary.save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "apothecary"));

        // The strains' sour sibling, beside them: nobody grows schwag, so it has no node of its own
        // in the cultivation branch. Vanilla's poisonous potato earns its keep the same way, as a
        // line in A Balanced Diet. Obtaining it is the whole criterion -- from a harvest or a wreck.
        AdvancementHolder schwag = Advancement.Builder.advancement()
                .display(display(ModItems.SCHWAG, "schwag", AdvancementType.TASK))
                .addCriterion("has_schwag", InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.SCHWAG))
                .parent(rootAdvancement)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "schwag"));

        // ---------------------------------------------------------------------
        // Cultivation
        // ---------------------------------------------------------------------

        // Shearing a growing plant needs NO custom criterion. Vanilla fires
        // minecraft:item_used_on_block from ServerPlayerGameMode#useItemOn the moment
        // BlockState#useItemOn returns an accepted result — which, for a hemp crop held against
        // shears, happens exactly where Defoliation#tryCut succeeds. The item filter is not optional
        // though: bonemeal reaches the same trigger down the ItemStack#useOnBlock path, so without
        // it this would fire on fertilising too.
        //
        // IT IS ALSO NOT ENOUGH ON ITS OWN ANY MORE. tryCut succeeds in THREE windows since
        // 2026-09-09, and the third is the charas rub at ages 6-7 -- which is not a trim, takes no
        // leaf, and must not grant a trimming advancement. So the criterion is split per trim age
        // and OR-merged: age is the only discriminator that works, because it is the one property
        // synced onto BOTH halves of a two-block plant (see IndicaCropBlock#isShapeSettled), and
        // the criterion tests whichever block the player actually clicked. The trim flags are
        // canonical on the lower half only, so a predicate on those would leak the moment somebody
        // sheared the top of a ripe plant.
        //
        // Built from Defoliation's own window constants rather than the literals 3..5, so retuning a
        // window moves the advancement with it. Two ranges rather than one 3..5 span, so it stays
        // right if the windows ever stop being contiguous.
        //
        // Parented to the root rather than to a strain, for the same reason first_contact is: an
        // advancement has exactly one parent, and trimming is not a Purple Kush thing.
        Advancement.Builder trimSeasonBuilder = Advancement.Builder.advancement()
                .display(display(Items.SHEARS, "trim_season", AdvancementType.TASK))
                .parent(rootAdvancement);
        List<Integer> trimAges = trimAges();
        for (int age : trimAges) {
            // The first window keeps 2.0.x's name, sheared_hemp_crop. Progress is saved per criterion
            // name and the game drops a name the advancement no longer has, so renaming it un-earned
            // Trim Season for every player who had it; with the criteria OR-merged, the old name
            // alone keeps it earned. advancement_criteria_keep_their_names holds every 2.0.x name.
            String criterion = age == trimAges.getFirst() ? "sheared_hemp_crop" : "sheared_hemp_crop_age_" + age;
            trimSeasonBuilder.addCriterion(criterion,
                    ItemUsedOnLocationTrigger.TriggerInstance.itemUsedOnBlock(
                            LocationPredicate.Builder.location().setBlock(BlockPredicate.Builder.block()
                                    .of(registryLookup.lookupOrThrow(Registries.BLOCK), ModTags.Blocks.HEMP_CROPS)
                                    .setProperties(StatePropertiesPredicate.Builder.properties()
                                            .hasProperty(IndicaCropBlock.AGE, age))),
                            ItemPredicate.Builder.item().of(items(registryLookup), ConventionalItemTags.SHEAR_TOOLS)));
        }
        AdvancementHolder trimSeason = trimSeasonBuilder
                // ANY of the trim ages, not all of them. Without this an advancement ANDs its
                // criteria and "Trim Season" would silently demand a cut in every window.
                .requirements(AdvancementRequirements.Strategy.OR)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "trim_season"));

        // TASK, not GOAL. Vanilla spends GOAL six times in the whole game, always on a large
        // deliberate investment (a full beacon, a cured villager); the closest analogues to "do the
        // fiddly thing correctly" — safely_harvest_honey, lightning_rod_with_villager_no_fire — are
        // both TASK. The mod's one GOAL goes to the Decarboxylator below, which really is beacon-tier.
        Advancement.Builder.advancement()
                .display(display(ModItems.HEMP_LEAF, "perfect_cut", AdvancementType.TASK))
                .addCriterion("harvested_fully_trimmed", HarvestHempCriterion.Conditions.fullyTrimmed())
                .parent(trimSeason)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "perfect_cut"));

        // Charas only comes off a ripe plant met with shears, and at 1 in 4 a player who tried it
        // once and got nothing has no reason to try again. The description is what teaches it.
        Advancement.Builder.advancement()
                .display(display(ModItems.CHARAS, "sticky_fingers", AdvancementType.TASK))
                .addCriterion("has_charas", InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.CHARAS))
                .parent(trimSeason)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "sticky_fingers"));

        // The furnace road into the Infuser: a leaf and a furnace, no bricks at all, which is why it
        // hangs here, where leaves first pile up. Obtaining the item is the criterion — the
        // Vaporizer's spent bowl grants it too — exactly as vanilla's "Acquire Hardware" says
        // "Smelt an Iron Ingot" and fires on any iron ingot.
        Advancement.Builder.advancement()
                .display(display(ModItems.SCORCHED_HEMP, "well_done", AdvancementType.TASK))
                .addCriterion("has_scorched_hemp", InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.SCORCHED_HEMP))
                .parent(trimSeason)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "well_done"));

        // Two criteria, ANDed (which is what an advancement does with several criteria by default),
        // because neither says the whole thing on its own. minecraft:tame_animal carries no item —
        // taming a parrot with *wheat* seeds would otherwise grant a hemp advancement — and
        // minecraft:player_interacted_with_entity carries no outcome, only that the interaction was
        // accepted. Together they mean "you have tamed a parrot, and you have fed one hemp seeds",
        // which is the claim the title makes.
        Advancement.Builder.advancement()
                .display(display(Items.FEATHER, "parrot_tamer", AdvancementType.TASK))
                .addCriterion("tamed_a_parrot", TameAnimalTrigger.TriggerInstance.tamedAnimal(
                        EntityPredicate.Builder.entity().of(registryLookup.lookupOrThrow(Registries.ENTITY_TYPE), EntityTypes.PARROT)))
                .addCriterion("fed_a_parrot_hemp_seeds", PlayerInteractTrigger.TriggerInstance.itemUsedOnEntity(
                        ItemPredicate.Builder.item().of(items(registryLookup), ModTags.Items.HEMP_SEEDS),
                        Optional.of(EntityPredicate.wrap(
                                EntityPredicate.Builder.entity().of(registryLookup.lookupOrThrow(Registries.ENTITY_TYPE), EntityTypes.PARROT)))))
                .parent(rootAdvancement)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "parrot_tamer"));

        // The seed's other life: food with no THC in it. Off the root because a seed is all it takes,
        // and keyed on a tag so a new seed food needs no edit here. Hulled hemp seed is sold as
        // "hemp hearts", which is the joke and the fact at once.
        Advancement.Builder.advancement()
                .display(display(ModItems.TOASTED_HEMP_SEEDS, "hemp_hearts", AdvancementType.TASK))
                .addCriterion("ate_a_hemp_seed_food", ConsumeItemTrigger.TriggerInstance.usedItem(
                        ItemPredicate.Builder.item().of(items(registryLookup), ModTags.Items.HEMP_SEED_FOODS)))
                .parent(rootAdvancement)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "hemp_hearts"));

        // ---------------------------------------------------------------------
        // Smoking
        // ---------------------------------------------------------------------

        // Granted the first time the player takes a hit, regardless of device *or* strain — the
        // SmokeCriterion is fired from Smoking.takeHit, which every smokeable funnels through,
        // rather than being an inventory check.
        //
        // An advancement has exactly one parent, so "a common child of every strain" is not a thing
        // the tree can express. It therefore hangs off the **root**, as a sibling of the strain
        // nodes rather than a child of one of them: parenting it to Purple Kush read as "you must
        // smoke Purple Kush", which is wrong and stays wrong for every strain added later.
        AdvancementHolder firstContact = Advancement.Builder.advancement()
                .display(display(ModItems.SPLIFF, "first_contact", AdvancementType.TASK))
                .addCriterion("took_a_hit", SmokeCriterion.Conditions.any())
                .parent(rootAdvancement)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "first_contact"));

        // A joke, so hidden: CBD fixes nothing. Smoke Cherry Wine, or feel a CBD-dominant edible kick
        // in, while greened out -- and the Nausea stays. "Greened out" is the player still carrying
        // Nausea, asked through vanilla's own player predicate. Under First Contact, since the
        // obvious way in is a hit; either criterion grants it.
        LootItemCondition nauseous = LootItemEntityPropertyCondition.hasProperties(LootContext.EntityTarget.THIS,
                EntityPredicate.Builder.entity().effects(MobEffectsPredicate.Builder.effects().and(MobEffects.NAUSEA))).build();
        Advancement.Builder.advancement()
                .display(display(ModItems.CHERRY_WINE_BUDS, "it_doesnt_work_like_that", AdvancementType.TASK, true))
                .addCriterion("smoked_cherry_wine_greened_out", ModCriteria.SMOKE.createCriterion(new SmokeCriterion.Conditions(
                        Optional.of(Holder.direct(nauseous)), Optional.empty(), Optional.empty(),
                        Optional.of(ModStrains.CHERRY_WINE))))
                .addCriterion("cbd_edible_kicked_in_greened_out", EdibleKicksInCriterion.Conditions.cbdDominant(nauseous))
                .requirements(AdvancementRequirements.Strategy.OR)
                .parent(firstContact)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "it_doesnt_work_like_that"));

        // A joke too, so hidden: smoke the plant with nothing in it, and nothing happens. A damp squib
        // is a firework that fails to go off, as a dud is; the French "pétard mouillé" is the same idiom,
        // and a pétard is a joint. Beside the other joke under First Contact. Only a load that comes to
        // no THC at all: Dud Weed rolled with hashish goes off, and is no damp squib.
        Advancement.Builder.advancement()
                .display(display(ModItems.DUD_WEED_BUDS, "damp_squib", AdvancementType.TASK, true))
                .addCriterion("smoked_dud_weed", SmokeCriterion.Conditions.of(ModStrains.DUD_WEED, 0))
                .parent(firstContact)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "damp_squib"));

        // Hidden: Deep Purple's riff, done properly -- smoke Maui Wowie on the water, riding a hemp boat or a
        // hemp chest boat. The ocean strain, the sailors' hemp under you, and the smoke over the water. The
        // song's title stays English in every language. Under First Contact, as every smoke joke is. One
        // criterion per boat, either one granting it.
        Advancement.Builder.advancement()
                .display(display(ModItems.MAUI_WOWIE_BUDS, "smoke_on_the_water", AdvancementType.TASK, true))
                .addCriterion("smoked_maui_wowie_on_a_hemp_boat", smokedAboard(registryLookup, ModEntities.HEMP_BOAT, ModStrains.MAUI_WOWIE))
                .addCriterion("smoked_maui_wowie_on_a_hemp_chest_boat", smokedAboard(registryLookup, ModEntities.HEMP_CHEST_BOAT, ModStrains.MAUI_WOWIE))
                .requirements(AdvancementRequirements.Strategy.OR)
                .parent(firstContact)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "smoke_on_the_water"));

        // Hidden: vanilla's Sneak 100 is a sculk sensor or a Warden letting a vibration go because you were
        // sneaking; this is the same, because you were Hushed. Echo OG is a smoke, so it hangs under First
        // Contact. It never fires vanilla's own: Sneak 100 is about sneaking. A Hushed player who sneaks earns
        // vanilla's for what sneaking already muffles (a step, say), which is let go before Hush is asked, and
        // this one for what only Hush muffles (eating, say).
        Advancement.Builder.advancement()
                .display(display(ModItems.ECHO_OG_BUDS, "sneak_420", AdvancementType.TASK, true))
                .addCriterion("hush_avoided_vibration", ModCriteria.HUSH_AVOIDED_VIBRATION.createCriterion(
                        new PlayerTrigger.TriggerInstance(Optional.empty())))
                .parent(firstContact)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "sneak_420"));

        // Hidden easter-egg: take a hit while the in-game clock reads ~4:20 PM. Vanilla day = 24000
        // ticks, tick 0 = 6:00 AM, 1000 ticks/hour → 4:20 PM = 10333. Window ±210 ticks (10123–10543,
        // 420 ticks wide ≈ 21 real seconds) as a nod to the number. Time gate lives in the criterion
        // conditions; hidden = last display flag is true so it stays secret until earned.
        Advancement.Builder.advancement()
                .display(display(Items.CLOCK, "blaze_it", AdvancementType.CHALLENGE, true))
                .addCriterion("smoked_at_420", SmokeCriterion.Conditions.during(10123L, 10543L))
                .rewards(challenge(TIMED_HIT_XP))
                .parent(firstContact)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "blaze_it"));

        // The other end of the same joke, and it needs no bed-tracking to work: ServerLevel sets the
        // time on wake to (t + 24000) - (t + 24000) % 24000, i.e. exactly tick 0. So the first 420
        // ticks of a day genuinely *are* the 420 ticks after getting up. It also catches anyone who
        // stayed up through the night and lit one at sunrise, which is if anything more on-theme.
        Advancement.Builder.advancement()
                .display(display(Items.BED.red(), "wake_and_bake", AdvancementType.CHALLENGE, true))
                .addCriterion("smoked_at_dawn", SmokeCriterion.Conditions.during(0L, 420L))
                .rewards(challenge(TIMED_HIT_XP))
                .parent(firstContact)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "wake_and_bake"));

        // The munchies: eat while smoking's Hunger is on you and the food bar is at 6 or under, the
        // point where FoodData#hasEnoughFood stops letting you sprint. consume_item fires before the
        // food lands (Consumable#onConsume), so the bar it reads is the empty one. Any food counts,
        // which is what the munchies are; drinks and potions do not, having no food component.
        Advancement.Builder.advancement()
                .display(display(Items.COOKIE, "the_munchies", AdvancementType.TASK))
                .addCriterion("ate_with_the_munchies", CriteriaTriggers.CONSUME_ITEM.createCriterion(
                        new ConsumeItemTrigger.TriggerInstance(
                                Optional.of(EntityPredicate.wrap(EntityPredicate.Builder.entity()
                                        .effects(MobEffectsPredicate.Builder.effects().and(MobEffects.HUNGER))
                                        .player(PlayerPredicate.Builder.player()
                                                .setFood(FoodPredicate.Builder.food()
                                                        .withLevel(MinMaxBounds.Ints.atMost(6)).build())
                                                .build()))),
                                Optional.of(ItemPredicate.Builder.item()
                                        .withComponents(DataComponentMatchers.Builder.components()
                                                .any(DataComponents.FOOD).build())
                                        .build()))))
                .parent(firstContact)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "the_munchies"));

        // The device ladder. An ItemPredicate rather than a device field on the criterion — the
        // stack handed to the trigger is still the packed one, and since packing is a component on
        // the same item now, matching the item is all this needs. Pipe before bong because that is
        // the cost order (planks vs. glass), the same way story/iron_tools gates upgrade_tools.
        AdvancementHolder pipeDream = Advancement.Builder.advancement()
                .display(display(ModItems.WOODEN_PIPE, "pipe_dream", AdvancementType.TASK))
                .addCriterion("smoked_a_pipe", SmokeCriterion.Conditions.with(registryLookup, ModItems.WOODEN_PIPE))
                .parent(firstContact)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "pipe_dream"));

        Advancement.Builder.advancement()
                .display(display(ModItems.BONG, "bong_voyage", AdvancementType.TASK))
                .addCriterion("smoked_a_bong", SmokeCriterion.Conditions.with(registryLookup,
                        ModItems.bongs().toArray(Item[]::new)))
                .parent(pipeDream)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "bong_voyage"));

        // Durability is measured in hits, so "ran it into the ground" is literally readable off the
        // vanilla criterion: ItemStack#damage passes the *new damage* value, and the conditions test
        // maxDamage - damage, i.e. hits remaining. {max: 0} is therefore the hit that breaks it.
        //
        // This is deliberately not an anvil-repair advancement: nothing in vanilla observes an
        // anvil (ITEM_DURABILITY_CHANGED is triggered from ItemStack#damage and nowhere else), so
        // that would have cost a second mixin. This is the moment that teaches you devices wear
        // out, which is what sends you to the anvil anyway.
        Advancement.Builder.advancement()
                .display(display(Items.ANVIL, "burnout", AdvancementType.TASK))
                .addCriterion("smoked_a_device_to_death", ItemDurabilityTrigger.TriggerInstance.changedDurability(
                        Optional.of(ItemPredicate.Builder.item()
                                .of(items(registryLookup), java.util.stream.Stream.concat(
                                        java.util.stream.Stream.of(ModItems.WOODEN_PIPE),
                                        ModItems.bongs().stream()).toArray(Item[]::new)).build()),
                        MinMaxBounds.Ints.atMost(0)))
                .parent(pipeDream)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "burnout"));

        Advancement.Builder.advancement()
                .display(display(ModItems.VAPORIZER, "vapor_trail", AdvancementType.TASK))
                .addCriterion("smoked_a_vaporizer", SmokeCriterion.Conditions.with(registryLookup, ModItems.VAPORIZER))
                .parent(firstContact)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "vapor_trail"));

        // A joke, so hidden, and under the schwag node rather than First Contact: smoking it needs
        // it first. Keyed on the strain, not the device, so any smokeable counts.
        Advancement.Builder.advancement()
                .display(display(Items.EMERALD, "you_get_what_you_pay_for", AdvancementType.TASK, true))
                .addCriterion("smoked_schwag", SmokeCriterion.Conditions.of(ModStrains.SCHWAG))
                .parent(schwag)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "you_get_what_you_pay_for"));

        // ---------------------------------------------------------------------
        // Industry
        // ---------------------------------------------------------------------

        AdvancementHolder hemprepreneurs = Advancement.Builder.advancement()
                .display(display(ModItems.HEMP_STEM, "hemprepreneurs", AdvancementType.TASK))
                .addCriterion("has_hemp_stem", InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.HEMP_STEM))
                .parent(rootAdvancement)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "hemprepreneurs"));

        AdvancementHolder hempBuilder = Advancement.Builder.advancement()
                .display(display(ModItems.HEMP_BRICK, "hemp_builder", AdvancementType.TASK))
                .addCriterion("has_hemp_brick", InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.HEMP_BRICK))
                .parent(hemprepreneurs)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "hemp_builder"));

        // ---------------------------------------------------------------------
        // Growing indoors
        // ---------------------------------------------------------------------

        // Under Hemp Builder because the pot is hemp bricks round dirt: a node hangs under what the
        // player must already have done, so everything indoors follows from the bricks.
        AdvancementHolder pothead = Advancement.Builder.advancement()
                .display(display(ModBlocks.GROW_POT, "pothead", AdvancementType.TASK))
                .addCriterion("placed_a_grow_pot", ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(registryLookup.lookupOrThrow(Registries.BLOCK), ModBlocks.GROW_POT))
                .parent(hempBuilder)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "pothead"));

        // Siblings, because a tray needs a pot and no lamp. Both read the plant, never the room: the
        // light is the record the plant kept (a lamp moved over at the end changes nothing), and the
        // bed is the block the plant stood on when it was broken.
        BlockPredicate.Builder hydroTray = BlockPredicate.Builder.block()
                .of(registryLookup.lookupOrThrow(Registries.BLOCK), ModBlocks.HYDRO_TRAY);
        Advancement.Builder.advancement()
                .display(display(ModBlocks.HYDRO_TRAY, "sea_of_green", AdvancementType.TASK))
                .addCriterion("harvested_from_a_tray", HarvestHempCriterion.Conditions.grownIn(hydroTray))
                .parent(pothead)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "sea_of_green"));

        AdvancementHolder midnightSun = Advancement.Builder.advancement()
                .display(display(ModBlocks.GROW_LAMP, "midnight_sun", AdvancementType.TASK))
                .addCriterion("harvested_under_a_grow_lamp", HarvestHempCriterion.Conditions.grownUnder(GrowLight.GROW_LAMP))
                .parent(pothead)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "midnight_sun"));

        // The second GOAL. Everything the indoor set offers, on one plant: trimmed in both windows,
        // under a Grow Lamp it never lost (losing it would have turned the record STRESSED), in a
        // Hydro Tray. The lamp is what makes it an investment — it is a redstone lamp, so glowstone,
        // so a trip to the Nether — which is why it sits under Midnight Sun and not under the tray.
        Advancement.Builder.advancement()
                .display(display(ModItems.INDICA_BUDS, "top_shelf", AdvancementType.GOAL))
                .addCriterion("harvested_top_shelf", HarvestHempCriterion.Conditions.create(
                        MinMaxBounds.Ints.exactly(2), Optional.of(GrowLight.GROW_LAMP), Optional.of(hydroTray.build())))
                .parent(midnightSun)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "top_shelf"));

        // Hidden: White Widow is the one plant a light at night stresses, so a Grow Lamp left on over
        // it ruins it. A harvest still on its Grow Lamp record means the lamp went dark every night --
        // a daylight detector on it, or a player at the lever -- which is the whole trick. The plant must
        // also have stood through a dark night while flowering: bone meal under a lamp that never went out
        // ripens it in one day, with no night to keep dark. Only in the Overworld: the Nether and the End
        // have no night, so a lamp left on there would earn it too.
        Advancement.Builder.advancement()
                .display(display(ModItems.WHITE_WIDOW_BUDS, "lights_out", AdvancementType.TASK, true))
                .addCriterion("harvested_white_widow_under_a_grow_lamp",
                        HarvestHempCriterion.Conditions.strainGrownUnderThroughANight(ModStrains.WHITE_WIDOW,
                                GrowLight.GROW_LAMP,
                                Optional.of(EntityPredicate.wrap(EntityPredicate.Builder.entity()
                                        .located(LocationPredicate.Builder.inDimension(Level.OVERWORLD))))))
                .parent(midnightSun)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "lights_out"));

        // A GOAL, and it earns it the way vanilla's create_full_beacon does: the Decarboxylator is 7
        // hemp bricks *blocks* — 504 hemp stem, roughly 252 indica or 126 sativa mature plants. The
        // tree's only other GOAL is Top Shelf, below.
        AdvancementHolder activationEnergy = Advancement.Builder.advancement()
                .display(display(ModBlocks.DECARBOXYLATOR, "activation_energy", AdvancementType.GOAL))
                .addCriterion("has_decarboxylator", InventoryChangeTrigger.TriggerInstance.hasItems(ModBlocks.DECARBOXYLATOR))
                .parent(hempBuilder)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "activation_energy"));

        // Bhang skips the butter: decarboxylated hemp goes straight into milk, so it hangs off the
        // oven rather than off the Infuser branch.
        Advancement.Builder.advancement()
                .display(display(ModItems.BHANG_BUCKET, "got_bhang", AdvancementType.TASK))
                .addCriterion("drank_bhang", ConsumeItemTrigger.TriggerInstance.usedItem(items(registryLookup), ModItems.BHANG_BUCKET))
                .parent(activationEnergy)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "got_bhang"));

        // Deliberately the only step between owning the machine and this node. "You decarboxylated
        // something" is an unavoidable consequence of building the oven, not a discovery of its own,
        // so it gets no node; the cauldron rinse is a separate thing the player has to work out.
        AdvancementHolder rinseCycle = Advancement.Builder.advancement()
                .display(display(ModItems.WASHED_DECARBOXYLATED_HEMP, "rinse_cycle", AdvancementType.TASK))
                .addCriterion("has_washed_hemp", InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.WASHED_DECARBOXYLATED_HEMP))
                .parent(activationEnergy)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "rinse_cycle"));

        AdvancementHolder cannabutter = Advancement.Builder.advancement()
                .display(display(ModItems.CANNABUTTER, "butter_late_than_never", AdvancementType.TASK))
                .addCriterion("has_cannabutter", InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.CANNABUTTER))
                .parent(rinseCycle)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "butter_late_than_never"));

        // The payoff node for the whole four-step chain. Keyed on the #hempdustry:edibles tag so a
        // future edible needs no edit here — and note Space Cake is legitimately absent from that
        // tag, since a slice is eaten by using the *block* and never fires consume_item.
        Advancement.Builder.advancement()
                .display(display(ModItems.SPACE_COOKIE, "give_it_an_hour", AdvancementType.TASK))
                .addCriterion("ate_an_edible", ConsumeItemTrigger.TriggerInstance.usedItem(
                        ItemPredicate.Builder.item().of(items(registryLookup), ModTags.Items.EDIBLES)))
                .parent(cannabutter)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "give_it_an_hour"));

        // Perfect is the one grade gated on *both* dials maxed — every item washed and a full
        // simmer — so it is a genuine mastery feat rather than a milestone, which is what CHALLENGE
        // is for. Matched with a component predicate: DataComponentPredicate is exact-value matching, so
        // listing only `quality` leaves `strength` free, and a weak-but-flawless batch still counts.
        Advancement.Builder.advancement()
                .display(display(ModBlocks.INFUSER, "perfect_batch", AdvancementType.CHALLENGE, true))
                .addCriterion("has_perfect_cannabutter", InventoryChangeTrigger.TriggerInstance.hasItems(
                        ItemPredicate.Builder.item()
                                .of(items(registryLookup), ModItems.CANNABUTTER)
                                .withComponents(new DataComponentMatchers(
                                        DataComponentExactPredicate.expect(ModComponents.QUALITY, Quality.PERFECT),
                                        Map.of()))))
                .rewards(challenge(PERFECT_BATCH_XP))
                .parent(cannabutter)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "perfect_batch"));

        // The Club des Hachichins met at the Hôtel Pimodan in 1840s Paris — Gautier, Baudelaire,
        // Dumas — and what they ate was dawamesk, the Algerian hash jam. Cannabutter is its base
        // here, so this sits beside the other cannabutter foods.
        Advancement.Builder.advancement()
                .display(display(ModItems.DAWAMESK, "club_des_hashischins", AdvancementType.TASK))
                .addCriterion("ate_dawamesk", ConsumeItemTrigger.TriggerInstance.usedItem(items(registryLookup), ModItems.DAWAMESK))
                .parent(cannabutter)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "club_des_hashischins"));

        // ---------------------------------------------------------------------
        // Textiles
        // ---------------------------------------------------------------------

        AdvancementHolder greenThreads = Advancement.Builder.advancement()
                .display(display(ModItems.HEMP_FIBER, "green_threads", AdvancementType.TASK))
                .addCriterion("has_hemp_fiber", InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.HEMP_FIBER))
                .parent(hemprepreneurs)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "green_threads"));

        Advancement.Builder.advancement()
                .display(display(ModItems.HEMP_BEANIE, "chill_set", AdvancementType.TASK))
                .addCriterion("full_hemp_armor", InventoryChangeTrigger.TriggerInstance.hasItems(
                        ModItems.HEMP_BEANIE,
                        ModItems.HEMP_SHIRT,
                        ModItems.HEMP_HAREM_PANTS,
                        ModItems.FLIP_FLOPS
                ))
                .parent(greenThreads)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "chill_set"));

        // ---------------------------------------------------------------------
        // Extraction
        // ---------------------------------------------------------------------

        // Under Green Threads because the Sifting Box's screen is hemp canvas. Every node from here
        // is "obtain the product": each is made in exactly one way that matters, and the item is
        // what the player can see.
        AdvancementHolder itsNotPollen = Advancement.Builder.advancement()
                .display(display(ModItems.KIEF, "its_not_pollen", AdvancementType.TASK))
                .addCriterion("has_kief", InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.KIEF))
                .parent(greenThreads)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "its_not_pollen"));

        AdvancementHolder hotOffThePress = Advancement.Builder.advancement()
                .display(display(ModBlocks.HASHISH_BAR, "hot_off_the_press", AdvancementType.TASK))
                .addCriterion("has_hashish_bar", InventoryChangeTrigger.TriggerInstance.hasItems(ModBlocks.HASHISH_BAR))
                .parent(itsNotPollen)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "hot_off_the_press"));

        // Keyed on the cut itself, as its description says, and not on holding hashish: the press
        // makes hashish before there is any bar, so holding it fired on the first pressing, ahead of
        // Hot Off the Press, its parent.
        AdvancementHolder cloudNine = Advancement.Builder.advancement()
                .display(display(ModItems.HASHISH, "cloud_nine", AdvancementType.TASK))
                .addCriterion("cut_a_hashish_bar", ItemUsedOnLocationTrigger.TriggerInstance.itemUsedOnBlock(
                        LocationPredicate.Builder.location().setBlock(BlockPredicate.Builder.block()
                                .of(registryLookup.lookupOrThrow(Registries.BLOCK), ModBlocks.HASHISH_BAR)),
                        ItemPredicate.Builder.item().of(items(registryLookup), ModTags.Items.HASH_CUTTERS)))
                .parent(hotOffThePress)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "cloud_nine"));

        Advancement.Builder.advancement()
                .display(display(ModItems.MOON_ROCK, "one_small_step", AdvancementType.TASK))
                .addCriterion("has_moon_rock", InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.MOON_ROCK))
                .parent(cloudNine)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "one_small_step"));

        AdvancementHolder doubleZero = Advancement.Builder.advancement()
                .display(display(ModItems.FILTERED_HASHISH, "double_zero", AdvancementType.TASK))
                .addCriterion("has_filtered_hashish", InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.FILTERED_HASHISH))
                .parent(hotOffThePress)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "double_zero"));

        // Under Double Zero, not under the ice wash: rosin is pressed from filtered hashish, which
        // the dry road reaches too.
        Advancement.Builder.advancement()
                .display(display(ModItems.ROSIN, "under_pressure", AdvancementType.TASK))
                .addCriterion("has_rosin", InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.ROSIN))
                .parent(doubleZero)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "under_pressure"));

        // A TASK, not a GOAL: plain ice from a snowy biome runs the wash, so it is a build but not an
        // investment. Packed and blue ice only make it better.
        Advancement.Builder.advancement()
                .display(display(ModItems.BUBBLE_HASH, "see_you_ice_o_later", AdvancementType.TASK))
                .addCriterion("has_bubble_hash", InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.BUBBLE_HASH))
                .parent(itsNotPollen)
                .save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "see_you_ice_o_later"));

        // One criterion per resin, ANDed by default, so each may be obtained at any time.
        // TriggerInstance.hasItems(a, b, c…) would demand all five in the inventory at once instead.
        Advancement.Builder aroundTheWorld = Advancement.Builder.advancement()
                .display(display(Items.COMPASS, "around_the_world_in_80_grams", AdvancementType.CHALLENGE))
                .rewards(challenge(AROUND_THE_WORLD_XP))
                .parent(itsNotPollen);
        for (Item resin : List.of(ModItems.CHARAS, ModItems.HASHISH, ModItems.FILTERED_HASHISH,
                ModItems.BUBBLE_HASH, ModItems.ROSIN)) {
            aroundTheWorld.addCriterion("has_" + BuiltInRegistries.ITEM.getKey(resin).getPath(),
                    InventoryChangeTrigger.TriggerInstance.hasItems(resin));
        }
        aroundTheWorld.save(consumer, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "around_the_world_in_80_grams"));
    }

    /** The built-in strains that grow buds on a plant of their own: a node each, and a criterion each in Apothecary. */
    private static List<ResourceKey<Strain>> budStrains(HolderLookup.Provider registryLookup) {
        return ModStrains.BUILT_IN.stream()
                .filter(key -> Strain.registry(registryLookup).getOrThrow(key).value().wildFlower().isPresent())
                .toList();
    }

    /** A hit of {@code strain} taken while riding a {@code vehicle}. */
    private static Criterion<SmokeCriterion.Conditions> smokedAboard(HolderLookup.Provider registryLookup,
                                                                     EntityType<?> vehicle, ResourceKey<Strain> strain) {
        LootItemCondition aboard = LootItemEntityPropertyCondition.hasProperties(LootContext.EntityTarget.THIS,
                EntityPredicate.Builder.entity().vehicle(EntityPredicate.Builder.entity()
                        .of(registryLookup.lookupOrThrow(Registries.ENTITY_TYPE), vehicle))).build();
        return ModCriteria.SMOKE.createCriterion(new SmokeCriterion.Conditions(
                Optional.of(Holder.direct(aboard)), Optional.empty(), Optional.empty(), Optional.of(strain)));
    }
}
