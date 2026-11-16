package com.warlonmhite.hempdustry.world;

import com.mojang.serialization.MapCodec;
import com.warlonmhite.hempdustry.Hempdustry;
import com.warlonmhite.hempdustry.util.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

public class ModPlacementModifiers {

    public static final PlacementModifierType<InBeldiaBiomePlacementModifier> IN_BELDIA_BIOME =
            Registry.register(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "in_beldia_biome"),
                    () -> InBeldiaBiomePlacementModifier.CODEC);

    public static final PlacementModifierType<InStructurePlacementFilter> IN_STRUCTURE =
            Registry.register(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "in_structure"),
                    () -> InStructurePlacementFilter.CODEC);

    public static void registerPlacementModifiers() {
        Hempdustry.LOGGER.info("Registering Placement Modifiers for " + Hempdustry.MOD_ID);
    }

    /**
     * Each wild Beldía, not just its patch, stands in {@code #hempdustry:beldia_gen}.
     *
     * <p>{@code BiomeFilter} only tests the patch's <em>centre</em>, and a patch spreads four
     * blocks, so without this a plant could land across a desert's border — on the beach beside it,
     * which is the one place Beldía is kept off. A desert that runs straight into the sea is a desert,
     * and grows it on the shore (Warlon Mhite's call, 2026-09-17).
     *
     * <p>The biome is read from the exact 4×4×4 cell, <b>not through {@code getBiome}</b>, which applies
     * vanilla's zoom jitter and can answer for a neighbouring cell — a border is exactly where that
     * lies.
     */
    public static final class InBeldiaBiomePlacementModifier extends PlacementFilter {
        public static final InBeldiaBiomePlacementModifier INSTANCE = new InBeldiaBiomePlacementModifier();
        public static final MapCodec<InBeldiaBiomePlacementModifier> CODEC = MapCodec.unit(() -> INSTANCE);

        private InBeldiaBiomePlacementModifier() {
        }

        @Override
        protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
            return context.getLevel().getNoiseBiome(QuartPos.fromBlock(pos.getX()),
                    QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ())).is(ModTags.Biomes.BELDIA_GEN);
        }

        @Override
        public PlacementModifierType<?> type() {
            return IN_BELDIA_BIOME;
        }
    }

    /**
     * Places only inside a piece of a structure in {@code structures}: wild Echo OG grows in Ancient Cities,
     * on their floors, and not on the sculk spreading round them.
     *
     * <p>A structure's pieces are placed in its own decoration step, before the vegetation's, so by the time
     * this runs the city stands. The question goes to vanilla's own {@code StructureManager}, bound to the
     * generating region as the chunk's own decoration does it ({@code forWorldGenRegion}), which reads the
     * structure references of the chunk the point is in: a piece's box, never the whole city's outline.
     *
     * <p><b>And only inside the chunk being decorated.</b> A structure's pieces are placed chunk by chunk, each
     * in its own chunk's decoration: a point in a neighbour not decorated yet is inside a piece that does not
     * stand there yet, and when it does, its blocks (air among them) are written over whatever a feature put
     * there first. A wild plant scattered across the border was overwritten so, and left its block-entity
     * placeholder behind on the air (a warning at the chunk's load).
     */
    public static final class InStructurePlacementFilter extends PlacementFilter {
        public static final MapCodec<InStructurePlacementFilter> CODEC = TagKey.hashedCodec(Registries.STRUCTURE)
                .fieldOf("structures").xmap(InStructurePlacementFilter::new, InStructurePlacementFilter::structures);

        private final TagKey<Structure> structures;

        private InStructurePlacementFilter(TagKey<Structure> structures) {
            this.structures = structures;
        }

        public static InStructurePlacementFilter of(TagKey<Structure> structures) {
            return new InStructurePlacementFilter(structures);
        }

        public TagKey<Structure> structures() {
            return structures;
        }

        @Override
        public boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
            WorldGenLevel level = context.getLevel();
            if (level instanceof WorldGenRegion region && !region.getCenter().equals(new ChunkPos(pos))) {
                return false;
            }
            StructureManager manager = structureManager(level);
            return manager != null && manager.getStructureWithPieceAt(pos, structures).isValid();
        }

        private static StructureManager structureManager(WorldGenLevel level) {
            if (level instanceof WorldGenRegion region) {
                return region.getLevel().structureManager().forWorldGenRegion(region);
            }
            return level instanceof ServerLevel server ? server.structureManager() : null;
        }

        @Override
        public PlacementModifierType<?> type() {
            return IN_STRUCTURE;
        }
    }
}
