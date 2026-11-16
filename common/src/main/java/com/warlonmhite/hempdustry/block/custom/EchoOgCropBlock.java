package com.warlonmhite.hempdustry.block.custom;

import com.warlonmhite.hempdustry.block.entity.custom.PlantRecordBlockEntity;
import com.warlonmhite.hempdustry.item.ModItems;
import com.warlonmhite.hempdustry.mixin.ExperienceOrbAccessor;
import com.warlonmhite.hempdustry.util.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Echo OG, the Ancient City's plant. Purple Kush's two-tall body playing by sculk's rules — the rest
 * (halves, headroom, whole-plant harvest, defoliation) is shared, traps included.
 *
 * <ul>
 *   <li><b>Sculk, and nothing else</b> ({@code #hempdustry:echo_og_soil}). No farmland, Grow Pot or Hydro
 *       Tray. It needs no light to survive.</li>
 *   <li><b>It shuns light.</b> Its own growth takes a step only where the light at its top, read as
 *       vanilla crops read it, is {@link #MAX_LIGHT} or less — a sculk catalyst's own glow, so a plant
 *       beside one still grows — and never under open sky. A step that does fall in brighter light
 *       stresses it ({@link PlantStress#shyStep}). No lamp counts for it.</li>
 *   <li><b>Slow by itself</b>: 1 in 20 per random tick, Warped Kush's pace away from the Nether.</li>
 *   <li><b>Fed by experience</b>, {@link #EXPERIENCE_PER_STEP} a growth step — vanilla's own sculk growth
 *       cost — through two doors: a sculk catalyst's charge reaching the sculk under it
 *       ({@code SculkBlockMixin}), spent where vanilla would have grown a sensor that the plant blocks
 *       anyway, and experience orbs that touch it, eaten as a player picks them up. Bone meal is refused,
 *       and not used up.</li>
 *   <li><b>It gives a little back</b>: a ripe harvest drops 1–3 experience, as mined sculk drops 1, always
 *       far below the seventy or so a plant ate.</li>
 *   <li><b>A faint glow when ripe</b>, light 1, a sculk sensor's — set on the block's properties.</li>
 * </ul>
 *
 * <p>The wild plant is this block, generated ripe on the sculk floors of Ancient Cities.
 */
public class EchoOgCropBlock extends IndicaCropBlock {
    /** The brightest light it grows in: a sculk catalyst's own (6). Mushrooms' rule, at a darker number. */
    public static final int MAX_LIGHT = 6;
    /** Experience a growth step costs: vanilla's {@code SculkSpreader} growth spawn cost. */
    public static final int EXPERIENCE_PER_STEP = 10;
    /** A ripe harvest's experience, as mined sculk's. */
    public static final UniformInt HARVEST_EXPERIENCE = UniformInt.of(1, 3);
    /** {@code floor(resistance / 1) + 1}: 1 in 20, Warped Kush's pace away from the Nether. */
    private static final float RESISTANCE = 19.0F;

    public EchoOgCropBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected boolean mayPlaceOn(BlockState floor, BlockGetter world, BlockPos pos) {
        return floor.is(ModTags.Blocks.ECHO_OG_SOIL);
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return ModItems.ECHO_OG_SEEDS;
    }

    @Override
    protected boolean needsLight() {
        return false;
    }

    @Override
    protected boolean lightLetsItGrow(LevelReader world, BlockPos pos) {
        return lightAtTop(world, pos) <= MAX_LIGHT;
    }

    @Override
    protected boolean shunsLight() {
        return true;
    }

    @Override
    public float growthResistance(LevelReader world, BlockPos pos) {
        return RESISTANCE;
    }

    /** Sculk is not farmland: a flat 1, so the resistance alone sets the odds and nothing crowds. */
    @Override
    public float fieldMoisture(LevelReader world, BlockPos pos) {
        return 1.0F;
    }

    /** Experience is its bone meal: bone meal is refused, on either half, and so never used up. */
    @Override
    public boolean isValidBonemealTarget(LevelReader world, BlockPos pos, BlockState state) {
        return false;
    }

    /**
     * The light over the plant whose root is at {@code root}, read as vanilla reads a crop's — sky light as
     * at noon, and block light — at its topmost block: the root of a young plant, its tip once it has one.
     */
    public static int lightAtTop(LevelReader world, BlockPos root) {
        BlockState plant = world.getBlockState(root);
        BlockPos top = root;
        while (plant.getBlock() instanceof IndicaCropBlock && world.getBlockState(top.above()).is(plant.getBlock())) {
            top = top.above();
        }
        return world.getRawBrightness(top, 0);
    }

    // ----- fed by experience -----

    /**
     * An experience orb touching either half is eaten, one orb's worth a tick as a player takes them, when the
     * whole orb fits the steps the plant has left (with what it already keeps): a ripe plant, one its headroom
     * holds, or one an orb would overfill leaves it for the player, so no experience is lost in the plant, as
     * Mending hands on what it cannot use. Every {@link #EXPERIENCE_PER_STEP} is a growth step; the rest waits on
     * the plant's record.
     */
    @Override
    protected void entityInside(BlockState state, Level world, BlockPos pos, Entity entity,
                                InsideBlockEffectApplier effects, boolean precise) {
        super.entityInside(state, world, pos, entity, effects, precise);
        if (!(world instanceof ServerLevel server) || !(entity instanceof ExperienceOrb orb) || orb.isRemoved()) {
            return;
        }
        BlockPos rootPos = TallHemp.rootOf(this, world, pos, state);
        if (rootPos == null || !(world.getBlockEntity(rootPos) instanceof PlantRecordBlockEntity record)) {
            return;
        }
        int value = orb.getValue();
        int room = this.stepsLeft(world, rootPos, world.getBlockState(rootPos)) * EXPERIENCE_PER_STEP - record.storedExperience();
        if (value > room) {
            return;
        }
        ExperienceOrbAccessor eaten = (ExperienceOrbAccessor) orb;
        eaten.hempdustry$setCount(eaten.hempdustry$getCount() - 1);
        if (eaten.hempdustry$getCount() <= 0) {
            orb.discard();
        }
        int steps = record.feed(value, EXPERIENCE_PER_STEP);
        boolean grew = false;
        for (int i = 0; i < steps; i++) {
            grew |= this.growOneStep(server, rootPos, server.getRandom());
        }
        fed(server, rootPos, grew);
    }

    /**
     * Whether a sculk catalyst's charge on the sculk at {@code sculk} would feed a plant standing on it: the
     * root of one that still has a step to take.
     */
    public boolean eatsChargeOn(LevelReader world, BlockPos sculk) {
        BlockPos rootPos = sculk.above();
        return this.canGrowFurther(world, rootPos, world.getBlockState(rootPos));
    }

    /** One growth step from a catalyst's charge on the sculk under the root at {@code rootPos}. */
    public void eatCharge(ServerLevel world, BlockPos rootPos, RandomSource random) {
        fed(world, rootPos, this.growOneStep(world, rootPos, random));
    }

    /** The sign that the plant fed: sculk's charge popping at its foot, and, if it grew, sculk's charge sound. */
    private static void fed(ServerLevel world, BlockPos rootPos, boolean grew) {
        world.sendParticles(ParticleTypes.SCULK_CHARGE_POP, rootPos.getX() + 0.5, rootPos.getY() + 0.3,
                rootPos.getZ() + 0.5, grew ? 6 : 2, 0.3, 0.2, 0.3, 0.0);
        if (grew) {
            world.playSound(null, rootPos, SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.BLOCKS, 1.0F,
                    0.9F + world.getRandom().nextFloat() * 0.2F);
        }
    }

    // ----- the harvest's experience -----

    /**
     * A ripe plant drops {@link #HARVEST_EXPERIENCE} when broken, as sculk does: once a plant, on its root,
     * whichever half was broken (the tip's break hands the root's drops through the root), and none for
     * Silk Touch, which vanilla's experience drop refuses.
     */
    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel world, BlockPos pos, ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, world, pos, tool, dropExperience);
        if (dropExperience && this.isRoot(state) && this.isMaxAge(state)) {
            this.tryDropExperience(world, pos, tool, HARVEST_EXPERIENCE);
        }
    }

    /**
     * The same experience as NeoForge asks for it. NeoForge drops a block's experience with its drops, from
     * {@code IBlockExtension#getExpDrop}, and none through {@link #tryDropExperience} (its own sculk overrides
     * this method instead); a public method of this signature is that override there, and is never called
     * on Fabric. Enchantments (Silk Touch's zero) are applied after it, as vanilla applies them.
     */
    public int getExpDrop(BlockState state, LevelAccessor level, BlockPos pos, @Nullable BlockEntity blockEntity,
                          @Nullable Entity breaker, ItemStack tool) {
        return this.isRoot(state) && this.isMaxAge(state) ? HARVEST_EXPERIENCE.sample(level.getRandom()) : 0;
    }
}
