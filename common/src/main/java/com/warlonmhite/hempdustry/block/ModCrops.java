package com.warlonmhite.hempdustry.block;

import com.warlonmhite.hempdustry.item.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Every hemp crop of the mod, each once, with what the rest of the mod needs to know about it: its
 * seeds and buds, its wild plant, the ground it takes, whether it burns, how it is tinted, where
 * its seeds are found and when it grows in a year with seasons. The block entity of the plant record, the composter, fire, the client's tints,
 * the tags and the item models read this list; a new crop is one entry here, and each of those gets
 * it the day it is added.
 *
 * <p>The blocks and items themselves stay registered where they always were ({@link ModBlocks},
 * {@link ModItems}): their ids are a save's, and a field of either is part of what addons compile
 * against. What makes one strain unlike another (its body, its home's rules, its loot) stays in its
 * own classes; only the facts every list shares are here.
 */
public final class ModCrops {
    private ModCrops() {
    }

    /** How a crop is coloured in the world. */
    public enum Tint {
        /** The biome's grass colour, then a shade deeper when grown under a lamp. */
        BIOME_AND_LIGHT,
        /** Drawn in its own colours, so only the lamp's shade: a biome's grass colour would stain them. */
        LIGHT_ONLY,
        /** The biome's grass colour and nothing else: the plant keeps no light record to read. */
        BIOME_ONLY,
        /** Drawn exactly as it is. */
        NONE
    }

    /** Where a crop's seeds are found as loot. */
    public enum Home {
        /** Everywhere seeds are: the tall grass, the shared chests, the villages. */
        SHARED,
        /** The cherry grove's: in the shared chests and the villages, never in the grass. */
        GROVE,
        /** The desert's temples and houses. */
        DESERT,
        /** The Nether's chests, its treasure and its bartering. */
        NETHER,
        /** Only ever dug up, by a brush or a sniffer. */
        BURIED,
        /** The hoglin stables. */
        STABLE,
        /** The End's cities. */
        END,
        /** The pale garden's mansions and camps. */
        PALE,
        /** A sport of another crop's harvest, and a shipwreck's stores. */
        SPORT,
        /** The Ancient City's ice box. */
        CITY,
        /** The warm ocean's ruins. */
        OCEAN;

        /**
         * Found in one place only, so kept out of the tall grass and the shared chests that every other
         * strain shares; leaving all of them out keeps the others' share of those rolls what it was.
         */
        public boolean regionBound() {
            return this != SHARED && this != GROVE;
        }
    }

    /**
     * When a crop grows in a year that has seasons: Serene Seasons' crop tags, which the tag provider writes from
     * this. Hemp is an annual, sown after the last frost and dead by winter, so a crop rests in winter, as the
     * vanilla crops beside it do, unless a reason named here says it does not.
     */
    public enum Seasons {
        /** Spring, summer and autumn; it rests in winter. */
        GROWING_SEASON,
        /** All year: it flowers by its age, not by the length of the day, and is the hemp of the cold steppes. */
        DAY_NEUTRAL,
        /** All year: it grows in the Nether or the End, which have no seasons for it to follow. */
        SEASONLESS_HOME;

        /** In the year-round crops, rather than in the three seasons of the growing year. */
        public boolean yearRound() {
            return this != GROWING_SEASON;
        }
    }

    /**
     * A crop's wild plant. {@code holdable}: an enderman may carry it (one small block on land; never a
     * two-block plant, whose other half would pop off, nor one holding its own water).
     */
    public record Wild(Block flower, Optional<Block> potted, boolean holdable) {
    }

    /**
     * One crop. {@code farmland}: it stands on farmland, so it keeps the farmland wet and a farmer
     * villager plants it. {@code bees}: a bee carrying pollen hurries it ({@code #minecraft:crops}).
     * {@code burns}: fire takes it, and its wild plant. {@code seasons}: when it grows in a year with seasons.
     */
    public record Crop(Block crop, Item seeds, Optional<Item> buds, Optional<Wild> wild, boolean farmland,
                       boolean bees, boolean burns, Tint tint, Home home, Seasons seasons) {
    }

    private static Wild wild(Block flower, Block potted, boolean holdable) {
        return new Wild(flower, Optional.of(potted), holdable);
    }

    /** Every crop, in the order the tags list them. */
    public static final List<Crop> ALL = List.of(
            new Crop(ModBlocks.INDICA_CROP, ModItems.INDICA_SEEDS, Optional.of(ModItems.INDICA_BUDS),
                    Optional.of(wild(ModBlocks.INDICA_FLOWER, ModBlocks.POTTED_INDICA_FLOWER, true)),
                    true, true, true, Tint.BIOME_AND_LIGHT, Home.SHARED, Seasons.GROWING_SEASON),
            new Crop(ModBlocks.SATIVA_CROP, ModItems.SATIVA_SEEDS, Optional.of(ModItems.SATIVA_BUDS),
                    Optional.of(wild(ModBlocks.SATIVA_FLOWER, ModBlocks.POTTED_SATIVA_FLOWER, false)),
                    true, true, true, Tint.BIOME_AND_LIGHT, Home.SHARED, Seasons.GROWING_SEASON),
            // Never on farmland (sand only), but a bee hurries it all the same.
            new Crop(ModBlocks.BELDIA_CROP, ModItems.BELDIA_SEEDS, Optional.of(ModItems.BELDIA_BUDS), Optional.empty(),
                    false, true, true, Tint.LIGHT_ONLY, Home.DESERT, Seasons.GROWING_SEASON),
            // No buds; its wild kin is Ditchweed, two blocks tall.
            new Crop(ModBlocks.INDUSTRIAL_HEMP_CROP, ModItems.INDUSTRIAL_HEMP_SEEDS, Optional.empty(),
                    Optional.of(wild(ModBlocks.DITCHWEED, ModBlocks.POTTED_DITCHWEED, false)),
                    true, true, true, Tint.BIOME_AND_LIGHT, Home.SHARED, Seasons.GROWING_SEASON),
            new Crop(ModBlocks.WARPED_KUSH_CROP, ModItems.WARPED_KUSH_SEEDS, Optional.of(ModItems.WARPED_KUSH_BUDS),
                    Optional.empty(), false, false, false, Tint.NONE, Home.NETHER, Seasons.SEASONLESS_HOME),
            // Nothing hurries Ruderalis, not even a bee.
            new Crop(ModBlocks.RUDERALIS_CROP, ModItems.RUDERALIS_SEEDS, Optional.of(ModItems.RUDERALIS_BUDS),
                    Optional.empty(), false, false, true, Tint.BIOME_ONLY, Home.BURIED, Seasons.DAY_NEUTRAL),
            new Crop(ModBlocks.CRIMSON_CROP, ModItems.CRIMSON_SEEDS, Optional.of(ModItems.CRIMSON_BUDS),
                    Optional.empty(), false, false, false, Tint.NONE, Home.STABLE, Seasons.SEASONLESS_HOME),
            new Crop(ModBlocks.ENDER_HAZE_CROP, ModItems.ENDER_HAZE_SEEDS, Optional.of(ModItems.ENDER_HAZE_BUDS),
                    Optional.empty(), false, false, false, Tint.NONE, Home.END, Seasons.SEASONLESS_HOME),
            new Crop(ModBlocks.CHERRY_WINE_CROP, ModItems.CHERRY_WINE_SEEDS, Optional.of(ModItems.CHERRY_WINE_BUDS),
                    Optional.of(wild(ModBlocks.CHERRY_WINE_FLOWER, ModBlocks.POTTED_CHERRY_WINE_FLOWER, true)),
                    true, true, true, Tint.BIOME_AND_LIGHT, Home.GROVE, Seasons.GROWING_SEASON),
            new Crop(ModBlocks.WHITE_WIDOW_CROP, ModItems.WHITE_WIDOW_SEEDS, Optional.of(ModItems.WHITE_WIDOW_BUDS),
                    Optional.of(wild(ModBlocks.WHITE_WIDOW_FLOWER, ModBlocks.POTTED_WHITE_WIDOW_FLOWER, true)),
                    true, true, true, Tint.LIGHT_ONLY, Home.PALE, Seasons.GROWING_SEASON),
            new Crop(ModBlocks.DUD_WEED_CROP, ModItems.DUD_WEED_SEEDS, Optional.of(ModItems.DUD_WEED_BUDS),
                    Optional.empty(), true, true, true, Tint.BIOME_AND_LIGHT, Home.SPORT, Seasons.GROWING_SEASON),
            // It grows on experience: no bee hurries it.
            new Crop(ModBlocks.ECHO_OG_CROP, ModItems.ECHO_OG_SEEDS, Optional.of(ModItems.ECHO_OG_BUDS),
                    Optional.empty(), false, false, true, Tint.NONE, Home.CITY, Seasons.GROWING_SEASON),
            // Under the sea: no fire, no bee, no pot; its wild plant holds its own water.
            new Crop(ModBlocks.MAUI_WOWIE_CROP, ModItems.MAUI_WOWIE_SEEDS, Optional.of(ModItems.MAUI_WOWIE_BUDS),
                    Optional.of(new Wild(ModBlocks.MAUI_WOWIE_FLOWER, Optional.empty(), false)),
                    false, false, false, Tint.LIGHT_ONLY, Home.OCEAN, Seasons.GROWING_SEASON));

    /** Every crop block, in {@link #ALL}'s order. */
    public static Block[] blocks() {
        return ALL.stream().map(Crop::crop).toArray(Block[]::new);
    }

    /** Every wild plant, in {@link #ALL}'s order; each names its potted twin, if it has one. */
    public static Stream<Wild> wilds() {
        return ALL.stream().flatMap(crop -> crop.wild().stream());
    }

    /** The crop {@code block} is, if it is one of ours. */
    public static Optional<Crop> of(Block block) {
        return ALL.stream().filter(crop -> crop.crop() == block).findFirst();
    }

    /**
     * Where the crop {@code seeds} plant has its seeds found; {@link Home#SHARED} for seeds of a crop not
     * of ours (a datapack strain's seeds plant one of these blocks, or another mod's).
     */
    public static Home home(Item seeds) {
        return seeds instanceof BlockItem item ? of(item.getBlock()).map(Crop::home).orElse(Home.SHARED) : Home.SHARED;
    }
}
