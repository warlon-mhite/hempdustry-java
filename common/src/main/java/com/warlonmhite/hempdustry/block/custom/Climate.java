package com.warlonmhite.hempdustry.block.custom;

import com.warlonmhite.hempdustry.balance.GrowClimate;
import com.warlonmhite.hempdustry.block.entity.custom.DehumidifierBlockEntity;
import com.warlonmhite.hempdustry.block.entity.custom.PlantRecordBlockEntity;
import com.warlonmhite.hempdustry.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

/**
 * The climate a plant grows in, as powdery mildew reads it: its <b>air</b> and its <b>humidity</b>. Every
 * piece of it is something the player built or chose, and can see — the roof, the glass, the water, the
 * biome — so a mildewed room is a room whose cause can be found and fixed.
 *
 * <ul>
 *   <li><b>Air</b>: {@link Air#OPEN} under the sky; {@link Air#STIRRED} under a roof the sun still reaches
 *       (sky light at the plant's full-grown top: glass), the sun warming and moving the air by day;
 *       {@link Air#STILL} under a roof that blocks the sun, a sealed room. What a roof is stays
 *       {@link Mildew#roofed}: a Grow Lamp is none. A running {@link GrowFanBlock Grow Fan} blowing on it moves
 *       a plant one step towards the open; two fans do no more than one.</li>
 *   <li><b>Humidity</b>: {@link Humidity#NORMAL}, one drier in a biome with almost no rain (a desert, the
 *       badlands, a savanna, the Nether), one wetter in a warm, rainy one (a jungle, a swamp, the mushroom
 *       fields), and one wetter again in a wet room: the plant's own bed a Hydro Tray, or, under a roof,
 *       standing water within reach. Under a roof, a running {@link DehumidifierBlock Dehumidifier} in reach
 *       takes two steps off. Never past {@link Humidity#DRY} or {@link Humidity#SOAKED}.</li>
 *   <li><b>A plant in water</b> never catches it, nor is it reached: free water washes the spores off
 *       and bursts them, which is why the fungus is a dry-leaf, humid-air one.</li>
 *   <li><b>A trimmed plant</b>, in either window, is a little less at risk: a thinner canopy.</li>
 * </ul>
 *
 * <p>The numbers are data ({@link GrowClimate}); the steps and what moves a plant between them are here.
 */
public final class Climate {
    private Climate() {
    }

    /** How the air moves around a plant, from the open sky to a sealed room. */
    public enum Air implements StringRepresentable {
        OPEN("open"),
        STIRRED("stirred"),
        STILL("still");

        private final String name;

        Air(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        /** One step towards the open: a fan's work. */
        public Air better() {
            return this == STILL ? STIRRED : OPEN;
        }

        /** This air's factor in {@code climate}. */
        public float factor(GrowClimate climate) {
            return switch (this) {
                case OPEN -> climate.air().open();
                case STIRRED -> climate.air().stirred();
                case STILL -> climate.air().still();
            };
        }
    }

    /** How damp the air around a plant is, in four steps. */
    public enum Humidity implements StringRepresentable {
        DRY("dry"),
        NORMAL("normal"),
        DAMP("damp"),
        SOAKED("soaked");

        private final String name;

        Humidity(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        /** The step {@code steps} away from normal, held between dry and soaked. */
        public static Humidity fromNormal(int steps) {
            return values()[Math.clamp(steps + 1, 0, values().length - 1)];
        }

        /** This humidity's factor in {@code climate}. */
        public float factor(GrowClimate climate) {
            return switch (this) {
                case DRY -> climate.humidity().dry();
                case NORMAL -> climate.humidity().normal();
                case DAMP -> climate.humidity().damp();
                case SOAKED -> climate.humidity().soaked();
            };
        }
    }

    /**
     * What the climate of the plant whose root is at {@code root}, standing as {@code plant}, does to its odds of
     * mildew: its air times its humidity, times the trim factor if it was trimmed in a window. Zero for a plant
     * in water, which never catches it.
     */
    public static float factor(Level world, BlockPos root, BlockState plant) {
        if (inWater(world, root)) {
            return 0.0F;
        }
        GrowClimate climate = GrowClimate.of(world.registryAccess());
        boolean roofed = Mildew.roofed(world, root);
        float factor = air(world, root, heightOf(plant), roofed).factor(climate)
                * humidity(world, root, roofed, onTray(world, root)).factor(climate);
        return trimmed(plant) ? factor * climate.trimmed() : factor;
    }

    /** The air of the plant whose root is at {@code root}, {@code height} blocks tall once grown. */
    public static Air air(Level world, BlockPos root, int height, boolean roofed) {
        if (!roofed) {
            return Air.OPEN;
        }
        Air air = world.canSeeSky(root.above(Math.max(0, height - 1))) ? Air.STIRRED : Air.STILL;
        return ventilated(world, root) ? air.better() : air;
    }

    /**
     * Whether a running Grow Fan blows on the plant whose root is at {@code root}: one facing the plant, from 1 to
     * the climate's {@code ahead} blocks behind it along the way it blows, at most {@code side} blocks off its line,
     * standing from {@code above} blocks under the root to {@code below} blocks over it. A fan beside a plant or
     * behind it blows past it. Walls do not stop the wind, as they stop no other reach of a grow room's gear.
     */
    public static boolean ventilated(Level world, BlockPos root) {
        GrowClimate.Fan fan = GrowClimate.of(world.registryAccess()).fan();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (Direction blows : Direction.Plane.HORIZONTAL) {
            Direction across = blows.getClockWise();
            for (int ahead = 1; ahead <= fan.ahead(); ahead++) {
                for (int side = -fan.side(); side <= fan.side(); side++) {
                    // A fan blowing this way that reaches the root stands this far back against its wind.
                    int x = root.getX() - blows.getStepX() * ahead + across.getStepX() * side;
                    int z = root.getZ() - blows.getStepZ() * ahead + across.getStepZ() * side;
                    for (int dy = -fan.above(); dy <= fan.below(); dy++) {
                        BlockState state = world.getBlockState(at.set(x, root.getY() + dy, z));
                        if (GrowFanBlock.running(state) && GrowFanBlock.blows(state) == blows) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * What a plant's growth step from {@code fromAge} tells its climate: its record keeps whether it grew in a fan's
     * wind, and a Dehumidifier drying it, under a roof, takes the step's water into its tank. Called from the crops'
     * own step, before the mildew roll.
     */
    public static void afterStep(Level world, BlockPos root, int fromAge) {
        if (world.isClientSide()) {
            return;
        }
        PlantRecordBlockEntity.recordVentilation(world, root, fromAge, ventilated(world, root));
        if (!inWater(world, root) && Mildew.roofed(world, root)) {
            BlockPos drier = drier(world, root, GrowClimate.of(world.registryAccess()));
            if (drier != null && world.getBlockEntity(drier) instanceof DehumidifierBlockEntity tank) {
                tank.collect(world);
            }
        }
    }

    /** A device a reach looks for: a block, read where it stands. */
    @FunctionalInterface
    interface Device {
        boolean at(BlockGetter world, BlockPos pos, BlockState state);
    }

    /**
     * The first block {@code device} accepts where {@code reach} reaches the plant rooted at {@code root}: the layers
     * from the lowest up, each row by row.
     */
    @Nullable
    static BlockPos find(Level world, BlockPos root, GrowClimate.Reach reach, Device device) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dy = -reach.above(); dy <= reach.below(); dy++) {
            for (int dx = -reach.reach(); dx <= reach.reach(); dx++) {
                for (int dz = -reach.reach(); dz <= reach.reach(); dz++) {
                    at.setWithOffset(root, dx, dy, dz);
                    if (device.at(world, at, world.getBlockState(at))) {
                        return at.immutable();
                    }
                }
            }
        }
        return null;
    }

    /**
     * The humidity at {@code pos}: the biome's step, one more for a wet room — a Hydro Tray as the bed
     * ({@code onTray}), or under a roof ({@code roofed}) standing water within {@link GrowClimate#wetReach} — and,
     * under a roof, the steps a running Dehumidifier in reach takes off.
     */
    public static Humidity humidity(Level world, BlockPos pos, boolean roofed, boolean onTray) {
        GrowClimate climate = GrowClimate.of(world.registryAccess());
        int steps = biomeSteps(world, pos, climate);
        if (onTray || roofed && waterNear(world, pos, climate.wetReach())) {
            steps++;
        }
        if (roofed && drier(world, pos, climate) != null) {
            steps -= climate.dehumidifier().dries();
        }
        return Humidity.fromNormal(steps);
    }

    /** The running Dehumidifier whose reach takes in {@code pos}, if there is one; the first found. */
    @Nullable
    public static BlockPos drier(Level world, BlockPos pos, GrowClimate climate) {
        return find(world, pos, climate.dehumidifier().area(), DehumidifierBlock::working);
    }

    /**
     * The biome's own step at {@code pos}: −1 with almost no rain, +1 where it is both rainy and warm, 0 elsewhere.
     * Read at the biome cell itself, not vanilla's jittered {@code getBiome}, so a plant at a border reads one
     * biome every time.
     */
    public static int biomeSteps(Level world, BlockPos pos, GrowClimate climate) {
        Biome biome = world.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()),
                QuartPos.fromBlock(pos.getZ())).value();
        Biome.ClimateSettings settings = Services.PLATFORM.climateOf(biome);
        if (settings.downfall() < climate.biome().dryBelow()) {
            return -1;
        }
        return settings.downfall() >= climate.biome().wetFrom() && settings.temperature() >= climate.biome().warmFrom()
                ? 1 : 0;
    }

    /**
     * Whether water stands within {@code reach} of {@code pos}, at its level or the one under it: a pool beside
     * the plants, an irrigation channel in the floor, a waterlogged block. The box is small on purpose — a room
     * is wet from what is in it, not from the sea outside.
     */
    public static boolean waterNear(Level world, BlockPos pos, int reach) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dy = -1; dy <= 0; dy++) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if ((dx != 0 || dz != 0 || dy != 0)
                            && world.getFluidState(at.setWithOffset(pos, dx, dy, dz)).is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Whether the plant whose root is at {@code root} stands in water: submerged or waterlogged. */
    public static boolean inWater(Level world, BlockPos root) {
        return world.getFluidState(root).is(FluidTags.WATER);
    }

    /**
     * Whether the bed under the root at {@code root} is a Hydro Tray with water in it. A dry tray is a dry bed,
     * and no wetter than dry farmland.
     */
    public static boolean onTray(Level world, BlockPos root) {
        BlockState bed = world.getBlockState(root.below());
        return bed.getBlock() instanceof HydroTrayBlock && bed.getValue(HydroTrayBlock.LEVEL) > 0;
    }

    /** Whether {@code plant} was trimmed in either window. */
    public static boolean trimmed(BlockState plant) {
        return plant.hasProperty(Defoliation.TRIMMED_EARLY) && plant.getValue(Defoliation.TRIMMED_EARLY)
                || plant.hasProperty(Defoliation.TRIMMED_LATE) && plant.getValue(Defoliation.TRIMMED_LATE);
    }

    /** How tall a plant of {@code plant}'s kind stands once grown: a sativa three blocks, every other host two. */
    public static int heightOf(BlockState plant) {
        return plant.getBlock() instanceof SativaCropBlock ? 3 : 2;
    }

    /**
     * Whether something over {@code pos}, within {@code limit} blocks, is a roof: the same blocks
     * {@link Mildew#roofed} reads off the heightmap (a plant's own segments are none of them), walked instead.
     * A client keeps no heightmap of those blocks, so its cues ask this.
     */
    public static boolean roofedByWalk(Level world, BlockPos pos, int limit) {
        BlockPos.MutableBlockPos at = pos.mutable();
        for (int i = 1; i <= limit; i++) {
            if (world.getBlockState(at.move(0, 1, 0)).is(BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP_NO_LEAVES)) {
                return true;
            }
        }
        return false;
    }
}
