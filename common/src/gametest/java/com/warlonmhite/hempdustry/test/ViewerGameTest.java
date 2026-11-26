package com.warlonmhite.hempdustry.test;

import com.google.gson.JsonObject;
import com.warlonmhite.hempdustry.balance.GrowClimate;
import com.warlonmhite.hempdustry.block.ModBlocks;
import com.warlonmhite.hempdustry.compat.ViewerRecipes;
import com.warlonmhite.hempdustry.component.ModComponents;
import com.warlonmhite.hempdustry.item.ModItems;
import com.warlonmhite.hempdustry.item.custom.SmokeContents;
import com.warlonmhite.hempdustry.recipe.PackingRecipe;
import com.warlonmhite.hempdustry.strain.Strain;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * What a recipe viewer shows as packing is what the crafting grid does.
 *
 * <p>The packing rows are drawn from stacks the viewer model builds, not from the recipe, so
 * nothing else ties the two together: a moon rock drawn without its load, or a glass the bong slot
 * offers that the recipe would refuse, is a page that lies with every gate green. This packs every
 * row through the real {@link PackingRecipe}, once for each stack every slot offers.
 */
public final class ViewerGameTest {

    /**
     * Every recipe read by a serializer of ours reaches a client. Since 1.21.2 the server keeps
     * recipes to itself unless asked, and Fabric asks by serializer: a recipe viewer there opts in
     * vanilla's, so one of ours left out is a recipe that crafts and shows on no page (the edibles,
     * bhang and hemp milk were). NeoForge asks by type. A server's own list holds every recipe either
     * way, so the loader is asked what it sends.
     */
    public static void everyRecipeOfOursReachesAClient(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Map<String, String> missing = new TreeMap<>();
        int seen = 0;
        for (RecipeHolder<?> holder : world.recipeAccess().getRecipes()) {
            Identifier serializer = BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer());
            if (serializer == null || !Sweep.ours(serializer)) {
                continue;
            }
            seen++;
            if (!TestPlatform.INSTANCE.reachesAClient(holder)) {
                missing.put(holder.id().identifier().toString(), serializer + " is not sent to the client");
            }
        }
        Sweep.atLeast(context, "recipes read by a serializer of ours", seen, 150);
        Sweep.report(context, "every recipe of ours reaches a client", missing, Map.of());
        context.succeed();
    }

    public static void everyPackingRowIsARealCraft(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        PackingRecipe packing = PackingRecipe.INSTANCE;
        List<ViewerRecipes.Packing> rows = ViewerRecipes.packing(world.registryAccess());
        context.assertFalse(rows.isEmpty(), "the viewers are shown no packing rows at all");

        int moonRockRows = 0;
        for (ViewerRecipes.Packing row : rows) {
            String id = row.id().toString();
            for (int slot = 0; slot < row.inputs().size(); slot++) {
                for (ItemStack offered : row.inputs().get(slot)) {
                    List<ItemStack> grid = new ArrayList<>();
                    for (int i = 0; i < 9; i++) {
                        grid.add(i >= row.inputs().size() ? ItemStack.EMPTY
                                : i == slot ? offered.copy() : row.inputs().get(i).getFirst().copy());
                    }
                    CraftingInput input = CraftingInput.of(3, 3, grid);
                    context.assertTrue(packing.matches(input, world),
                            id + " is refused by the real recipe with " + offered.getHoverName().getString());
                    ItemStack packed = packing.assemble(input, world.registryAccess());
                    context.assertValueEqual(packed.get(ModComponents.SMOKE_CONTENTS),
                            row.output().get(ModComponents.SMOKE_CONTENTS),
                            id + " shows a different load from the one it crafts");
                    context.assertValueEqual(packed.get(ModComponents.CHARGES), row.output().get(ModComponents.CHARGES),
                            id + " shows a different bowl from the one it crafts");
                    if (slot == 0) {
                        context.assertTrue(packed.is(offered.getItem()),
                                id + " packs " + offered.getHoverName().getString() + " into another item");
                    }
                }
            }
            if (row.inputs().stream().anyMatch(stacks -> stacks.getFirst().is(ModItems.MOON_ROCK))) {
                moonRockRows++;
            }
        }

        // One row per plant strain per coat: the coat is half of what a moon rock carries.
        long plants = Strain.all(world.registryAccess()).stream()
                .filter(strain -> strain.value().wildFlower().isPresent()).count();
        context.assertValueEqual(moonRockRows, (int) plants * 3, "moon rock rows, one per plant and coat");
        context.assertTrue(rows.stream().anyMatch(row -> row.inputs().getFirst().size() == ModItems.bongs().size()),
                "no bong row offers every glass");
        context.assertFalse(rows.getFirst().output().getOrDefault(ModComponents.SMOKE_CONTENTS, SmokeContents.EMPTY)
                        .entries().getFirst().strain().value().wildFlower().isEmpty(),
                "the packing rows open on a strain that never grew on a plant");
        context.succeed();
    }

    /**
     * Every viewer page's notes fit the room its category has for them. Both viewers size a category once, for
     * every page in it, so a note past the room is drawn over the next page: the resin line on the sifting,
     * wash and rubbing pages did that until their room was counted here.
     */
    public static void everyViewerNoteFitsItsPage(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Map<Identifier, List<ViewerRecipes.Entry>> pages = Map.of(
                ViewerRecipes.DECARBOXYLATING, ViewerRecipes.decarboxylating(world),
                ViewerRecipes.INFUSING, ViewerRecipes.infusing(world),
                ViewerRecipes.PRESSING, ViewerRecipes.pressing(world),
                ViewerRecipes.CAULDRON, ViewerRecipes.cauldron(),
                ViewerRecipes.SIFTING, ViewerRecipes.sifting(world),
                ViewerRecipes.ICE_O_LATOR, ViewerRecipes.iceOLator(world),
                ViewerRecipes.WORLD, ViewerRecipes.world(world));
        List<String> over = new ArrayList<>();
        int seen = 0;
        for (Map.Entry<Identifier, List<ViewerRecipes.Entry>> page : pages.entrySet()) {
            int room = ViewerRecipes.noteLines(page.getKey());
            for (ViewerRecipes.Entry entry : page.getValue()) {
                seen++;
                if (entry.notes().size() > room) {
                    over.add(entry.id() + ": " + entry.notes().size() + " lines, room for " + room);
                }
            }
        }
        context.assertTrue(seen >= 20, "only " + seen + " viewer rows to measure");
        context.assertTrue(over.isEmpty(), "viewer notes past their page's room: " + over);
        context.succeed();
    }

    /**
     * The Grow Fan's and the Dehumidifier's information pages take their reach from the grow climate: at the shipped
     * climate every English page reads as it was written with the numbers in it ("9 blocks ahead, 5 wide, down to 3
     * below", "9×9 and 2 blocks up and down"), and at a climate a pack moved (a fan 12 ahead, side 3, 4 below; a
     * Dehumidifier of reach 5, 1 below and 3 above) the pages say 12, 7 and 4, and 11×11 and 3. Every locale carrying
     * the two pages is formatted with the moved numbers and must show each one, with no placeholder left over.
     */
    public static void theClimatePagesReadTheClimate(GameTestHelper context) {
        GrowClimate shipped = GrowClimate.of(context.getLevel().registryAccess());
        Map<String, JsonObject> locales = new TreeMap<>();
        for (Map.Entry<String, java.nio.file.Path> file : Sweep.modFiles().entrySet()) {
            if (file.getKey().startsWith("assets/hempdustry/lang/")) {
                locales.put(file.getKey().substring(file.getKey().lastIndexOf('/') + 1).replace(".json", ""),
                        Sweep.json(file.getValue()).getAsJsonObject());
            }
        }
        JsonObject english = locales.get("en_us");
        Map<Item, Component> pages = ViewerRecipes.info(context.getLevel().registryAccess());
        String fan = page(english, pages.get(ModBlocks.GROW_FAN.asItem()));
        String drier = page(english, pages.get(ModBlocks.DEHUMIDIFIER.asItem()));
        context.assertTrue(fan.contains("in front of it: 9 blocks ahead, 5 wide, down to 3 below."),
                "the Grow Fan's page at the shipped climate reads: " + fan);
        context.assertTrue(drier.contains("around it, 9×9 and 2 blocks up and down:"),
                "the Dehumidifier's page at the shipped climate reads: " + drier);

        GrowClimate moved = new GrowClimate(shipped.onsetOneIn(), shipped.spreadOneIn(), shipped.air(), shipped.humidity(),
                shipped.biome(), shipped.wetReach(), shipped.trimmed(), new GrowClimate.Fan(12, 3, 4, 1),
                new GrowClimate.Drier(5, 1, 3, shipped.dehumidifier().dries(), shipped.dehumidifier().stepsPerLevel()),
                shipped.uvcLamp(), shipped.stress(), shipped.boneMealFailsOneIn());
        Map<Item, Component> movedPages = ViewerRecipes.info(moved);
        Map<String, String> findings = new TreeMap<>();
        int checked = 0;
        for (Map.Entry<String, JsonObject> locale : locales.entrySet()) {
            for (Map.Entry<Item, List<String>> want : Map.of(ModBlocks.GROW_FAN.asItem(), List.of("12", "7", "4"),
                    ModBlocks.DEHUMIDIFIER.asItem(), List.of("11×11", "3")).entrySet()) {
                Component page = movedPages.get(want.getKey());
                String key = ((TranslatableContents) page.getContents()).getKey();
                if (!locale.getValue().has(key)) continue;
                checked++;
                String text = page(locale.getValue(), page);
                if (text.contains("%") || !want.getValue().stream().allMatch(text::contains)) {
                    findings.put(locale.getKey() + " " + key, text);
                }
            }
        }
        Sweep.atLeast(context, "climate pages in the locales", checked, 16);
        Sweep.report(context, "Every climate page shows the moved climate's numbers", findings, Map.of());
        context.succeed();
    }

    /** {@code page} as {@code lang} formats it, the way the game fills a translation's arguments. */
    private static String page(JsonObject lang, Component page) {
        TranslatableContents contents = (TranslatableContents) page.getContents();
        return String.format(Locale.ROOT, lang.get(contents.getKey()).getAsString(), contents.getArgs());
    }
}
