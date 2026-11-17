package com.warlonmhite.hempdustry.test;

import com.warlonmhite.hempdustry.Hempdustry;
import com.warlonmhite.hempdustry.block.ModBlocks;
import com.warlonmhite.hempdustry.block.custom.IndicaCropBlock;
import com.warlonmhite.hempdustry.block.entity.custom.PlantRecordBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.feature.BlockColumnFeature;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A wild plant of several blocks, generated with its chunk, loads without a word in the log, and its root
 * keeps its record.
 *
 * <p>A live world never shows this: placing a feature in a loaded level, as the other wild-plant tests do,
 * goes through {@code LevelChunk}, which asks the block for its block entity at once. Real chunk generation
 * writes into a {@link ProtoChunk} through a {@link WorldGenRegion} instead, and there vanilla only leaves a
 * placeholder ({@code "DUMMY"}) for every block that can hold a block entity, to be made when the chunk
 * goes live. A plant keeps its record on its root alone, so a placeholder on any other of its blocks finds
 * no block entity to make and vanilla warns, once per block, when the chunk loads.
 *
 * <p>Two writes on one block (overlapping features) must not leave a root's placeholder under another block of a
 * plant either: vanilla clears an older one only under a block that says it keeps none.
 *
 * <p>So this test generates the real thing, as far as a test can: a fresh nether proto-chunk, the mod's own
 * configured {@code block_column} features (each wild patch's plant) placed into it through vanilla's {@link WorldGenRegion}, then the
 * chunk made live the way vanilla makes it ({@link LevelChunk} from the proto-chunk, every placeholder
 * promoted), with the log read the while. The only stand-in is the chunk holder that hands the region its
 * one chunk; the chunk is never added to the world.
 */
public final class WildPlantChunkGameTest {
    private static final String WARNING = "Tried to load a block entity";

    private WildPlantChunkGameTest() {
    }

    public static void aWildPlantLoadsWithoutAWarning(GameTestHelper context) {
        ServerLevel nether = context.getLevel().getServer().getLevel(Level.NETHER);
        context.assertTrue(nether != null, "this server has no Nether, so this proves nothing");
        List<String> warnings = new ArrayList<>();
        List<String> checked = new ArrayList<>();
        var appender = new AbstractAppender("hempdustry-wild-plant-chunk", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                String line = event.getMessage().getFormattedMessage();
                if (line.contains(WARNING) && line.contains(Hempdustry.MOD_ID + ":")) {
                    warnings.add(line);
                }
            }
        };
        appender.start();
        Logger root = (Logger) LogManager.getRootLogger();
        root.addAppender(appender);
        try {
            int chunk = 0;
            for (var feature : nether.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).listElements().toList()) {
                if (!feature.key().identifier().getNamespace().equals(Hempdustry.MOD_ID)
                        || !(feature.value().config() instanceof RandomPatchConfiguration patch)) {
                    continue;
                }
                ConfiguredFeature<?, ?> plant = patch.feature().value().feature().value();
                if (!(plant.feature() instanceof BlockColumnFeature)) {
                    continue;
                }
                String name = feature.key().identifier().getPath();
                checked.add(name);
                generateAndLoad(context, nether, plant, name, new ChunkPos(20000 + 2 * chunk++, 20000));
            }
            overwriteARoot(context, nether, new ChunkPos(20000 + 2 * chunk, 20000));
        } finally {
            root.removeAppender(appender);
            appender.stop();
        }
        context.assertTrue(checked.containsAll(List.of("warped_kush", "crimson", "ender_haze", "beldia")),
                "the wild plants of several blocks are not all patches of block columns any more, so this test misses some: "
                        + checked);
        context.assertTrue(warnings.isEmpty(), "a wild plant generated with its chunk warns when the chunk loads ("
                + warnings.size() + " lines), first: " + (warnings.isEmpty() ? "" : warnings.get(0)));
        context.succeed();
    }

    /** Places {@code feature} in a fresh proto-chunk at {@code at} as chunk generation does, then makes it live. */
    private static void generateAndLoad(GameTestHelper context, ServerLevel level,
            ConfiguredFeature<?, ?> feature, String name, ChunkPos at) {
        ProtoChunk proto = protoChunk(level, at);
        WorldGenRegion region = regionOf(level, proto);
        BlockPos origin = at.getMiddleBlockPosition(100);
        context.assertTrue(feature.place(region, level.getChunkSource().getGenerator(), region.getRandom(), origin),
                "the wild " + name + " feature placed nothing in an empty chunk");

        List<BlockPos> roots = new ArrayList<>();
        List<BlockPos> plant = new ArrayList<>();
        for (int dy = -8; dy <= 8; dy++) {
            BlockPos pos = origin.above(dy);
            BlockState state = proto.getBlockState(pos);
            if (!state.isAir()) {
                plant.add(pos);
                if (state.getBlock() instanceof EntityBlock block && block.newBlockEntity(pos, state) != null) {
                    roots.add(pos);
                }
            }
        }
        context.assertTrue(plant.size() > 1, "the wild " + name + " is one block tall, so this proves nothing");
        context.assertTrue(roots.size() == 1, "the wild " + name + " has " + roots.size() + " blocks that keep a record");

        LevelChunk live = new LevelChunk(level, proto, null);
        for (BlockPos pos : plant) {
            BlockEntity entity = live.getBlockEntity(pos);
            boolean isRoot = pos.equals(roots.get(0));
            context.assertTrue(isRoot == entity instanceof PlantRecordBlockEntity,
                    "the wild " + name + "'s " + (isRoot ? "root has no record" : "block at " + pos + " has " + entity)
                            + " once its chunk is live");
        }
    }

    /**
     * Two generation writes on one block, as overlapping features make them: a plant's root, then another block
     * of a plant. The root's placeholder goes with it, as vanilla takes one away under a block that keeps none.
     */
    private static void overwriteARoot(GameTestHelper context, ServerLevel level, ChunkPos at) {
        ProtoChunk proto = protoChunk(level, at);
        WorldGenRegion region = regionOf(level, proto);
        BlockPos pos = at.getMiddleBlockPosition(100);
        BlockState root = ModBlocks.INDICA_CROP.defaultBlockState();
        region.setBlock(pos, root, Block.UPDATE_ALL);
        context.assertTrue(proto.getBlockEntityNbt(pos) != null, "test setup: a plant's root left no placeholder in its proto-chunk");
        region.setBlock(pos, root.setValue(IndicaCropBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        context.assertTrue(proto.getBlockEntityNbt(pos) == null,
                "a plant's tip written over its root in generation kept the root's placeholder");
        new LevelChunk(level, proto, null);
    }

    /** A fresh proto-chunk at {@code at}, one step short of its features, as chunk generation hands it over. */
    static ProtoChunk protoChunk(ServerLevel level, ChunkPos at) {
        ProtoChunk proto = new ProtoChunk(at, UpgradeData.EMPTY, level, level.palettedContainerFactory(), null);
        proto.setPersistedStatus(ChunkStatus.FEATURES.getParent());
        return proto;
    }

    /**
     * Vanilla's {@link WorldGenRegion} for the features step of {@code proto} alone: what a feature and its
     * placement see while that chunk generates. Its neighbours are out of reach.
     */
    static WorldGenRegion regionOf(ServerLevel level, ProtoChunk proto) {
        ChunkPos at = proto.getPos();
        GenerationChunkHolder holder = new GenerationChunkHolder(at) {
            @Override
            public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) {
                return proto;
            }

            @Override
            protected void addSaveDependency(CompletableFuture<?> sync) {
            }

            @Override
            public int getTicketLevel() {
                return 0;
            }

            @Override
            public int getQueueLevel() {
                return 0;
            }
        };
        return new WorldGenRegion(level, StaticCache2D.create(at.x, at.z, 0, (x, z) -> holder),
                ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES), proto);
    }
}
