package com.warlonmhite.hempdustry.block.entity.custom;

import com.warlonmhite.hempdustry.balance.Genetics;
import com.warlonmhite.hempdustry.strain.internal.Rolls;
import com.warlonmhite.hempdustry.strain.internal.Strains;
import com.warlonmhite.hempdustry.strain.internal.Tiers;
import com.warlonmhite.hempdustry.block.custom.GrowLuck;
import com.warlonmhite.hempdustry.block.custom.HydroTrayBlock;
import com.warlonmhite.hempdustry.block.custom.PlantStress;
import com.warlonmhite.hempdustry.block.custom.IndustrialHempCropBlock;
import com.warlonmhite.hempdustry.block.entity.ModBlockEntities;
import com.warlonmhite.hempdustry.strain.BudTier;
import com.warlonmhite.hempdustry.strain.GrowQuality;
import com.warlonmhite.hempdustry.strain.Potency;
import com.warlonmhite.hempdustry.strain.PotencyBand;
import com.warlonmhite.hempdustry.strain.QualityGene;
import com.warlonmhite.hempdustry.strain.ResinSheet;
import com.warlonmhite.hempdustry.strain.ResinStep;
import com.warlonmhite.hempdustry.strain.ResinTexture;
import com.warlonmhite.hempdustry.strain.ResistanceGenes;
import com.warlonmhite.hempdustry.strain.Resistances;
import com.warlonmhite.hempdustry.strain.Strain;
import com.mojang.serialization.Codec;
import java.lang.ref.WeakReference;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * What one plant carries from planting to harvest beyond its block state: its potency band, its quality
 * gene, its four resistances, its resin and whether it has grown under open sky. Sits on the plant's root only (the
 * block that carries its age), on every crop that grows buds and on Industrial Hemp.
 *
 * <h2>Why a block entity, and not more state</h2>
 *
 * A plant's own rolls are too many values to be block-state properties once all of them exist, and a
 * block entity is the one place vanilla's loot reads per-block data from: every break, by a player,
 * an explosion or a harvester, hands the block entity to the loot table, which is how the band
 * reaches the buds. Nothing is drawn: the band never changes how the plant looks.
 *
 * <h2>Rolled at planting</h2>
 *
 * The band and the gene are drawn from the strain's odds, each on its own, when the record joins its
 * world ({@link #setLevel}): the moment a seed is planted, a plant another mod placed, or a world
 * generated ripe goes live with its chunk. A record loaded with its band and gene keeps them; one loaded
 * without, from a plant sown before records existed, rolls what it lacks then. A plant set back to age 0
 * in place, by a harvester that replants, is a new plant: its crop {@link #replant replants} the record,
 * which rolls again.
 *
 * <p>Industrial Hemp grows no bud: its record rolls its resistances and its resin, from its own sheets
 * ({@link IndustrialHempCropBlock#RESISTANCES}, {@link IndustrialHempCropBlock#RESIN}, since it is no strain),
 * and no band and no gene; its band reads mid. Nothing in its life reads them yet (it is never stressed, never
 * catches mildew and is never rubbed); they are what it will pass on.
 *
 * <h2>Resistances</h2>
 *
 * Four steps, one per phenomenon ({@link ResistanceGenes}), each rolled on its own from the strain's odds
 * ({@link Resistances}) and hidden. Every cause of stress and of mildew asks them ({@link #resistancesAt});
 * a plant with no record is neutral on all four.
 *
 * <p>The draw is on a random of its own: never the loot table's, so a harvest rolls exactly the
 * numbers it always did (a loot entry taking a variable number of draws shifts every pool after it),
 * and never the world's, since a record can join a chunk on a world-generation thread.
 *
 * <h2>Resin</h2>
 *
 * One step ({@link ResinStep}), rolled like the others from the strain's odds ({@link ResinSheet}). The harvest
 * hands it to the buds ({@code hempdustry:set_resin}) and the rub reads it off the living plant
 * ({@link #resinAt}). A plant with no record rubs and gives buds as before resin.
 *
 * <h2>Open sky</h2>
 *
 * A plant grown in the sun is luckier at its harvest than one grown under glowstone. Whether it was is
 * read like every light ({@link #recordSky}): at each growth step, sky light 15 at its full-grown top
 * ({@code canSeeSky}, which passes glass and stops at leaves and roofs), the record keeping the weaker of
 * before and now. Sky light is the same by night, so the hour plays no part. Losing the sun never
 * stresses a plant. A plant that keeps no light record never steps it, and a wild plant generated ripe
 * never took a step: both read shade.
 *
 * <h2>Experience</h2>
 *
 * A plant fed by experience (Echo OG) keeps what it has eaten short of a growth step ({@link #feed}): ten
 * points a step, so an orb worth three waits for the rest. It goes with the plant: a replanting or a harvest
 * takes it. Nothing else keeps any.
 *
 * <h2>The harvest's tier</h2>
 *
 * {@link #harvestTier} rolls the {@link BudTier} every bud of one harvest carries, Fortune's extras
 * included: once per harvest, on a random of its own (so the loot table's draws are exactly what they
 * were), from the plant's grow luck ({@link GrowLuck}). Asked again by another stack of the same harvest
 * (the same loot context), it answers the same tier.
 */
public class PlantRecordBlockEntity extends BlockEntity {
    private static final String POTENCY_KEY = "potency_band";
    private static final String QUALITY_KEY = "quality_gene";
    private static final String OPEN_SKY_KEY = "open_sky";
    private static final String WORKING_TRAY_KEY = "working_tray";
    private static final String SAW_NIGHT_KEY = "saw_night";
    private static final String VENTILATED_KEY = "ventilated";
    private static final String RESISTANCES_KEY = "resistances";
    private static final String RESIN_KEY = "resin";
    private static final String EXPERIENCE_KEY = "experience";

    private @Nullable PotencyBand band;
    private @Nullable QualityGene gene;
    private @Nullable Boolean openSky;
    private @Nullable Boolean workingTray;
    private boolean sawNight;
    private @Nullable Boolean ventilated;
    private @Nullable ResistanceGenes resistances;
    private @Nullable ResinStep resin;
    private int experience;
    private WeakReference<Object> harvest = new WeakReference<>(null);
    private @Nullable BudTier harvestTier;

    public PlantRecordBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLANT_RECORD, pos, state);
    }

    /** The band, if this plant has rolled it yet. */
    public Optional<PotencyBand> rolledBand() {
        return Optional.ofNullable(band);
    }

    /**
     * This plant's band, rolled now on {@code random} from its strain's odds if it has none yet (a
     * record that never joined a world). A plant no strain names is mid.
     */
    public PotencyBand band() {
        if (!growsBuds(getBlockState())) {
            return PotencyBand.MID;
        }
        if (band == null) {
            band = Rolls.roll(potencyOf(level, getBlockState()), RandomSource.create());
            setChanged();
        }
        return band;
    }

    /** Sets the band outright: what a test or a command does. */
    public void setBand(@Nullable PotencyBand band) {
        this.band = band;
        setChanged();
    }

    /**
     * Rolls on joining a server world, unless the save it was loaded from carried a band. A client's
     * copy rolls nothing: the band lives on the server.
     */
    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level.isClientSide()) {
            return;
        }
        boolean buds = growsBuds(getBlockState());
        if (buds && (band == null || gene == null) || resistances == null || resin == null) {
            if (buds && band == null) {
                band = Rolls.roll(potencyOf(level, getBlockState()), RandomSource.create());
            }
            if (buds && gene == null) {
                gene = Rolls.roll(qualityOf(level, getBlockState()), RandomSource.create());
            }
            if (resistances == null) {
                resistances = Rolls.roll(resistancesOf(level, getBlockState()), RandomSource.create());
            }
            if (resin == null) {
                resin = Rolls.roll(resinOf(level, getBlockState()), RandomSource.create());
            }
            setChanged();
        }
    }

    /** The plant at this root is a new plant: everything is rolled again. */
    public void replant() {
        boolean buds = growsBuds(getBlockState());
        band = buds ? Rolls.roll(potencyOf(level, getBlockState()), RandomSource.create()) : null;
        gene = buds ? Rolls.roll(qualityOf(level, getBlockState()), RandomSource.create()) : null;
        resistances = Rolls.roll(resistancesOf(level, getBlockState()), RandomSource.create());
        resin = Rolls.roll(resinOf(level, getBlockState()), RandomSource.create());
        openSky = null;
        workingTray = null;
        sawNight = false;
        ventilated = null;
        experience = 0;
        harvestTier = null;
        harvest = new WeakReference<>(null);
        setChanged();
    }

    /**
     * Feeds the plant {@code points} of experience and answers how many growth steps of {@code cost} each they
     * buy with what it already kept, keeping the rest for the next.
     */
    public int feed(int points, int cost) {
        experience += Math.max(0, points);
        int steps = experience / cost;
        experience %= cost;
        setChanged();
        return steps;
    }

    /** The experience this plant keeps short of a growth step. */
    public int storedExperience() {
        return experience;
    }

    /** The gene, if this plant has rolled it yet. */
    public Optional<QualityGene> rolledGene() {
        return Optional.ofNullable(gene);
    }

    /** This plant's quality gene, rolled now from its strain's odds if it has none yet; average on Industrial Hemp. */
    public QualityGene gene() {
        if (!growsBuds(getBlockState())) {
            return QualityGene.AVERAGE;
        }
        if (gene == null) {
            gene = Rolls.roll(qualityOf(level, getBlockState()), RandomSource.create());
            setChanged();
        }
        return gene;
    }

    /** Sets the gene outright: what a test or a command does. */
    public void setGene(@Nullable QualityGene gene) {
        this.gene = gene;
        setChanged();
    }

    /** The resistances, if this plant has rolled them yet. */
    public Optional<ResistanceGenes> rolledResistances() {
        return Optional.ofNullable(resistances);
    }

    /** This plant's four resistances, rolled now from its strain's odds if it has none yet. */
    public ResistanceGenes resistances() {
        if (resistances == null) {
            resistances = Rolls.roll(resistancesOf(level, getBlockState()), RandomSource.create());
            setChanged();
        }
        return resistances;
    }

    /** Sets the resistances outright: what a test or a command does. */
    public void setResistances(@Nullable ResistanceGenes resistances) {
        this.resistances = resistances;
        setChanged();
    }

    /** The resin step, if this plant has rolled it yet. */
    public Optional<ResinStep> rolledResin() {
        return Optional.ofNullable(resin);
    }

    /** This plant's resin step, rolled now from its strain's odds if it has none yet. */
    public ResinStep resin() {
        if (resin == null) {
            resin = Rolls.roll(resinOf(level, getBlockState()), RandomSource.create());
            setChanged();
        }
        return resin;
    }

    /** Sets the resin step outright: what a test or a command does. */
    public void setResin(@Nullable ResinStep resin) {
        this.resin = resin;
        setChanged();
    }

    /** Whether this plant has grown under open sky at every step so far; false before its first step. */
    public boolean openSky() {
        return Boolean.TRUE.equals(openSky);
    }

    /** The open-sky record as kept: empty before the plant's first growth step. */
    public Optional<Boolean> skyRecord() {
        return Optional.ofNullable(openSky);
    }

    /** Sets the open-sky record outright: what a test does. */
    public void setOpenSky(@Nullable Boolean openSky) {
        this.openSky = openSky;
        setChanged();
    }

    /** Whether this plant has grown on a working Hydro Tray at every step so far; false before its first step. */
    public boolean workingTray() {
        return Boolean.TRUE.equals(workingTray);
    }

    /** Sets the working-tray record outright: what a test does. */
    public void setWorkingTray(@Nullable Boolean workingTray) {
        this.workingTray = workingTray;
        setChanged();
    }

    /**
     * One growth step of the plant whose root is at {@code root}, taken from {@code fromAge}: its working-tray record
     * becomes whether a Hydro Tray at work ({@link HydroTrayBlock#isWorking}) is under the root now on its first
     * step, and the weaker of before and now after, as the open sky is kept. So a tray that ran dry, went unfed or
     * lost its pump on any step gives that harvest nothing, and one set under the plant on its last step neither.
     * Read before the ripening step drinks the tray. A root with no record is left alone.
     */
    public static void recordTray(Level level, BlockPos root, int fromAge) {
        if (level.isClientSide() || !(level.getBlockEntity(root) instanceof PlantRecordBlockEntity record)) {
            return;
        }
        boolean now = HydroTrayBlock.isWorking(level.getBlockState(root.below()));
        boolean next = fromAge == 0 || record.workingTray == null ? now : record.workingTray && now;
        if (record.workingTray == null || record.workingTray != next) {
            record.workingTray = next;
            record.setChanged();
        }
    }

    /**
     * One growth step of the plant whose root is at {@code root}, taken from {@code fromAge}, standing
     * {@code height} blocks tall once grown: its open-sky record becomes the sky now at its full-grown top
     * on its first step, and the weaker of before and now after. Called from the one step every growth
     * route takes ({@code PlantStress#afterStep}); a root with no record (a plant that keeps none) is left
     * alone.
     *
     * <p>A plant that holds water (Maui Wowie) reads its sky through the water above its top ({@link
     * #seesSkyThroughWater}): under water no block sees sky light 15, so {@code canSeeSky} would call every plant
     * of the sea roofed.
     */
    public static void recordSky(Level level, BlockPos root, int fromAge, int height) {
        if (level.isClientSide() || !(level.getBlockEntity(root) instanceof PlantRecordBlockEntity record)) {
            return;
        }
        BlockPos top = root.above(Math.max(0, height - 1));
        boolean now = level.getFluidState(root).isEmpty() ? level.canSeeSky(top) : seesSkyThroughWater(level, top);
        boolean next = fromAge == 0 || record.openSky == null ? now : record.openSky && now;
        if (record.openSky == null || record.openSky != next) {
            record.openSky = next;
            record.setChanged();
        }
    }

    /** Whether this plant has grown in a running Grow Fan's wind at every step so far; false before its first step. */
    public boolean ventilated() {
        return Boolean.TRUE.equals(ventilated);
    }

    /** Sets the ventilation record outright: what a test does. */
    public void setVentilated(@Nullable Boolean ventilated) {
        this.ventilated = ventilated;
        setChanged();
    }

    /**
     * One growth step of the plant whose root is at {@code root}, taken from {@code fromAge}, {@code now} in a running
     * fan's wind or not: its ventilation record becomes {@code now} on its first step, and the weaker of before and now
     * after, as the open sky is kept. So the stalk a fan thickens is one it blew on all its life; a fan switched on for
     * the last step gives nothing. A root with no record is left alone.
     */
    public static void recordVentilation(Level level, BlockPos root, int fromAge, boolean now) {
        if (level.isClientSide() || !(level.getBlockEntity(root) instanceof PlantRecordBlockEntity record)) {
            return;
        }
        boolean next = fromAge == 0 || record.ventilated == null ? now : record.ventilated && now;
        if (record.ventilated == null || record.ventilated != next) {
            record.ventilated = next;
            record.setChanged();
        }
    }

    /**
     * Whether this plant, once flowering and before it ripened, stood through a dark night: a random tick it
     * took at night with no light leaking onto it ({@link #recordNight}). A plant brought to ripe in one day,
     * by bone meal under a lamp that never went out, never did.
     */
    public boolean sawNight() {
        return sawNight;
    }

    /** Sets the night record outright: what a test does. */
    public void setSawNight(boolean sawNight) {
        this.sawNight = sawNight;
        setChanged();
    }

    /**
     * One random tick of the growing plant whose root is at {@code root}, {@code age} old and standing {@code height}
     * blocks tall once grown: it now has seen a dark night if it reads night where it stands
     * ({@link PlantStress#hours}), is flowering ({@link PlantStress#TOUCHY_AGE} on) and not yet ripe, and no light
     * leaks onto it ({@link PlantStress#leaks}). Kept once seen, until the plant is replanted. A root with no
     * record, or a dimension with no night, never sees one.
     */
    public static void recordNight(Level level, BlockPos root, int age, int maxAge, int height) {
        if (level.isClientSide() || age < PlantStress.TOUCHY_AGE || age >= maxAge
                || !(level.getBlockEntity(root) instanceof PlantRecordBlockEntity record) || record.sawNight) {
            return;
        }
        if (PlantStress.hours(level, root) == PlantStress.Hours.NIGHT && !PlantStress.leaks(level, root, height)) {
            record.sawNight = true;
            record.setChanged();
        }
    }

    /**
     * Whether the sky reaches {@code top} through the water over it: the first block above that holds no fluid
     * sees the sky, and nothing on the way up dims the light but the water itself. That is vanilla's {@code
     * canSeeSkyFromBelowWater} (a drowned's spawn) at any height: vanilla walks down from sea level, and at or
     * above sea level asks plain {@code canSeeSky}, which no block inside water passes, so a tank or a mountain
     * lake would read roofed.
     */
    public static boolean seesSkyThroughWater(Level level, BlockPos top) {
        BlockPos.MutableBlockPos pos = top.mutable();
        while (pos.getY() < level.getMaxY()) {
            pos.move(Direction.UP);
            BlockState state = level.getBlockState(pos);
            if (state.getFluidState().isEmpty()) {
                return level.canSeeSky(pos);
            }
            if (state.getLightDampening() > 0 && !state.liquid()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The tier of this plant's harvest now: Mids for a stressed or mildewed plant, else one roll from its
     * grow luck with {@code harvester}'s luck in it ({@link GrowLuck}). {@code root} is the plant's root as
     * it stood when it was broken. {@code harvestKey} names the harvest — the loot context rolling it — and
     * every ask with the same key answers the same tier, so every bud of a harvest shares it however many
     * stacks the loot hands out.
     */
    public BudTier harvestTier(Object harvestKey, Level level, BlockState root, @Nullable Entity harvester) {
        if (harvestTier == null || harvest.get() != harvestKey) {
            harvestTier = GrowLuck.givesMids(root)
                    ? BudTier.MIDS
                    : Tiers.roll(Genetics.of(level.registryAccess()), GrowLuck.of(this, root, level, harvester),
                            RandomSource.create());
            harvest = new WeakReference<>(harvestKey);
        }
        return harvestTier;
    }

    /** The quality sheet of the strain {@code state}'s block is a plant of, the default with none. */
    public static GrowQuality qualityOf(@Nullable Level level, BlockState state) {
        if (level == null) {
            return GrowQuality.DEFAULT;
        }
        return Strains.ofPlant(state.getBlock(), level.registryAccess())
                .map(Holder::value)
                .map(Strain::traits).map(Strain.Traits::quality)
                .orElse(GrowQuality.DEFAULT);
    }

    /**
     * The resistance sheet of the plant {@code state} is: Industrial Hemp's own, else its strain's, else the
     * spread around neutral, as a strain file naming none.
     */
    public static Resistances resistancesOf(@Nullable Level level, BlockState state) {
        if (state.getBlock() instanceof IndustrialHempCropBlock) {
            return IndustrialHempCropBlock.RESISTANCES;
        }
        if (level == null) {
            return Resistances.DEFAULT;
        }
        return Strains.ofPlant(state.getBlock(), level.registryAccess())
                .map(Holder::value)
                .map(Strain::traits).map(Strain.Traits::resistances)
                .orElse(Resistances.DEFAULT);
    }

    /**
     * The resin sheet of the plant {@code state} is: Industrial Hemp's own, else its strain's, always average with
     * none.
     */
    public static ResinSheet resinOf(@Nullable Level level, BlockState state) {
        if (state.getBlock() instanceof IndustrialHempCropBlock) {
            return IndustrialHempCropBlock.RESIN;
        }
        if (level == null) {
            return ResinSheet.DEFAULT;
        }
        return Strains.ofPlant(state.getBlock(), level.registryAccess())
                .map(Holder::value)
                .map(Strain::traits).map(Strain.Traits::resin)
                .orElse(ResinSheet.DEFAULT);
    }

    /** The resin texture of the plant {@code state} is, even with none to ask. */
    public static ResinTexture textureOf(@Nullable Level level, BlockState state) {
        return resinOf(level, state).texture();
    }

    /**
     * The resin step of the plant whose root is at {@code rootPos}; empty where there is no record to ask, or on a
     * client, where none is rolled.
     */
    public static Optional<ResinStep> resinAt(Level level, BlockPos rootPos) {
        return !level.isClientSide() && level.getBlockEntity(rootPos) instanceof PlantRecordBlockEntity record
                ? Optional.of(record.resin())
                : Optional.empty();
    }

    /** Whether the plant {@code state} is grows buds, and so rolls a band and a gene: all but Industrial Hemp. */
    private static boolean growsBuds(BlockState state) {
        return !(state.getBlock() instanceof IndustrialHempCropBlock);
    }

    /**
     * The resistances of the plant whose root is at {@code rootPos}; neutral on all four where there is no
     * record to ask, or on a client, where none is rolled.
     */
    public static ResistanceGenes resistancesAt(Level level, BlockPos rootPos) {
        return !level.isClientSide() && level.getBlockEntity(rootPos) instanceof PlantRecordBlockEntity record
                ? record.resistances()
                : ResistanceGenes.NEUTRAL;
    }

    /** The potency sheet of the strain {@code state}'s block is a plant of, the default with none. */
    public static Potency potencyOf(@Nullable Level level, BlockState state) {
        if (level == null) {
            return Potency.DEFAULT;
        }
        return Strains.ofPlant(state.getBlock(), level.registryAccess())
                .map(Holder::value)
                .map(Strain::traits).map(Strain.Traits::potency)
                .orElse(Potency.DEFAULT);
    }

    /** The band of the plant whose root is at {@code rootPos}; mid where there is no record to ask. */
    public static PotencyBand bandAt(Level level, BlockPos rootPos) {
        return level.getBlockEntity(rootPos) instanceof PlantRecordBlockEntity record
                ? record.band()
                : PotencyBand.MID;
    }

    /** Rolls the record at {@code rootPos} again, if there is one: a new plant stands there. */
    public static void replantAt(Level level, BlockPos rootPos) {
        if (level.getBlockEntity(rootPos) instanceof PlantRecordBlockEntity record) {
            record.replant();
        }
    }

    @Override
    protected void loadAdditional(ValueInput view) {
        super.loadAdditional(view);
        band = view.read(POTENCY_KEY, PotencyBand.CODEC).orElse(null);
        gene = view.read(QUALITY_KEY, QualityGene.CODEC).orElse(null);
        openSky = view.read(OPEN_SKY_KEY, Codec.BOOL).orElse(null);
        workingTray = view.read(WORKING_TRAY_KEY, Codec.BOOL).orElse(null);
        sawNight = view.read(SAW_NIGHT_KEY, Codec.BOOL).orElse(false);
        ventilated = view.read(VENTILATED_KEY, Codec.BOOL).orElse(null);
        resistances = view.read(RESISTANCES_KEY, ResistanceGenes.CODEC).orElse(null);
        resin = view.read(RESIN_KEY, ResinStep.CODEC).orElse(null);
        experience = view.getIntOr(EXPERIENCE_KEY, 0);
    }

    @Override
    protected void saveAdditional(ValueOutput view) {
        super.saveAdditional(view);
        if (band != null) {
            view.store(POTENCY_KEY, PotencyBand.CODEC, band);
        }
        if (gene != null) {
            view.store(QUALITY_KEY, QualityGene.CODEC, gene);
        }
        if (openSky != null) {
            view.store(OPEN_SKY_KEY, Codec.BOOL, openSky);
        }
        if (workingTray != null) {
            view.store(WORKING_TRAY_KEY, Codec.BOOL, workingTray);
        }
        if (sawNight) {
            view.store(SAW_NIGHT_KEY, Codec.BOOL, true);
        }
        if (ventilated != null) {
            view.store(VENTILATED_KEY, Codec.BOOL, ventilated);
        }
        if (resistances != null) {
            view.store(RESISTANCES_KEY, ResistanceGenes.CODEC, resistances);
        }
        if (resin != null) {
            view.store(RESIN_KEY, ResinStep.CODEC, resin);
        }
        if (experience > 0) {
            view.putInt(EXPERIENCE_KEY, experience);
        }
    }
}
