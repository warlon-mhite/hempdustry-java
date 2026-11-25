package com.warlonmhite.hempdustry.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.warlonmhite.hempdustry.Hempdustry;
import com.warlonmhite.hempdustry.block.ModBlocks;
import com.warlonmhite.hempdustry.block.custom.stash.StashNose;
import com.warlonmhite.hempdustry.block.entity.custom.StashJarBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * A Stash Jar's lid, drawn apart from the jar so it can lift when a hand opens the jar, the way a chest's
 * lid is drawn by its renderer. At rest it sits exactly where the jar model used to bake it.
 *
 * <p>Opened, it pops up, tips toward the player who opened it, hangs while the smell gets out and drops back
 * with a little bounce. How far and how long follows the jar's nose: an epic jar's lid flies highest and hangs
 * longest, a soft one barely lifts, a rotten one rattles as if the jar did not want to be opened. Putting buds
 * in lifts it briefly.
 *
 * <p>The lids are block models ({@code block/stash_jar_lid}, {@code block/diamond_stash_jar_lid},
 * {@code block/netherite_stash_jar_lid}) drawn through the item pipeline, as the Hemp Press draws its moving
 * parts: a client item definition is the one place vanilla looks a model up by id alone.
 */
public class StashJarLidRenderer implements BlockEntityRenderer<StashJarBlockEntity, StashJarLidRenderer.State> {
    private static final ItemStackTemplate PLAIN = lid("stash_jar_lid");
    private static final ItemStackTemplate DIAMOND = lid("diamond_stash_jar_lid");
    private static final ItemStackTemplate NETHERITE = lid("netherite_stash_jar_lid");
    /** The lid's centre, from the middle of the block: what it turns about. */
    private static final float PIVOT_Y = 11.75F / 16.0F - 0.5F;

    private final ItemModelResolver itemModels;
    /**
     * One stack per lid, made in the world it is drawn in and made again in the next: the frame used to make one per
     * jar. Never made before a world binds the items' components, so not in a field initialiser.
     */
    private final Map<ItemStackTemplate, ItemStack> stacks = new IdentityHashMap<>();
    /** The world {@link #stacks} were made in, held weakly so a world left is not kept for them. */
    private WeakReference<Level> stacksFor = new WeakReference<>(null);

    public StashJarLidRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModels = context.itemModelResolver();
    }

    private static ItemStackTemplate lid(String model) {
        return new ItemStackTemplate(Items.STICK, DataComponentPatch.builder()
                .set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, model))
                .build());
    }

    private static ItemStackTemplate lidOf(Block jar) {
        if (jar == ModBlocks.NETHERITE_STASH_JAR) {
            return NETHERITE;
        }
        return jar == ModBlocks.DIAMOND_STASH_JAR ? DIAMOND : PLAIN;
    }

    public static class State extends BlockEntityRenderState {
        final ItemStackRenderState lid = new ItemStackRenderState();
        /** Blocks the lid is lifted by. */
        float lift;
        /** Radians it is tipped by, its near edge up. */
        float tip;
        /** Radians it is rocked by, side to side (a rotten jar's rattle). */
        float rock;
        Direction toward = Direction.SOUTH;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(StashJarBlockEntity jar, State state, float partialTick, Vec3 cameraPos,
                                   @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(jar, state, partialTick, cameraPos, crumblingOverlay);
        itemModels.updateForTopItem(state.lid, stackOf(jar.getLevel(), lidOf(jar.getBlockState().getBlock())),
                ItemDisplayContext.NONE, jar.getLevel(), null, 0);
        int event = jar.lidMotion();
        Motion motion = Motion.of(event & 7);
        state.toward = Direction.from2DDataValue(event >> 3);
        float t = jar.lidTicks(partialTick);
        float up = motion.up(t);
        state.lift = up * motion.rise / 16.0F;
        state.tip = up * motion.tip * Mth.DEG_TO_RAD;
        state.rock = motion.rattles && t >= 0 && t < motion.length()
                ? Mth.sin(t * 2.8F) * 7.0F * Mth.DEG_TO_RAD * Math.min(1.0F, up * 2.0F) : 0.0F;
    }

    private ItemStack stackOf(@Nullable Level level, ItemStackTemplate lid) {
        if (level != stacksFor.get()) {
            stacks.clear();
            stacksFor = new WeakReference<>(level);
        }
        return stacks.computeIfAbsent(lid, ItemStackTemplate::create);
    }

    @Override
    public void submit(State state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState camera) {
        matrices.pushPose();
        // The item pipeline draws a model centred on the origin, so stand it in the middle of the block.
        matrices.translate(0.5F, 0.5F + state.lift, 0.5F);
        if (state.tip != 0.0F || state.rock != 0.0F) {
            // Turning about the axis level with the lid and square to the opener lifts the edge on their side.
            float nx = state.toward.getStepX();
            float nz = state.toward.getStepZ();
            Quaternionf turn = new Quaternionf().rotateAxis(state.tip, -nz, 0.0F, nx).rotateAxis(state.rock, nx, 0.0F, nz);
            matrices.rotateAround(turn, 0.0F, PIVOT_Y, 0.0F);
        }
        state.lid.submit(matrices, queue, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        matrices.popPose();
    }

    @Override
    public int getViewDistance() {
        // The jar's body is a block model, drawn as far as the chunks are; a lid drawn only to the 64 blocks a block
        // entity renderer takes by default (BlockEntityRenderer#getViewDistance) left lidless jars past it. A chest
        // vanishes whole at 64: its renderer draws all of it.
        Minecraft client = Minecraft.getInstance();
        return Math.max(64, client.options.getEffectiveRenderDistance() * 16);
    }

    /**
     * How a lid moves for one opening: rises {@code rise} pixels and tips {@code tip} degrees over 3 ticks,
     * hangs {@code hang} ticks, drops back over 3 and bounces once over 4.
     */
    record Motion(float rise, float tip, int hang, boolean rattles) {
        private static final int RISE = 3;
        private static final int DROP = 3;
        private static final int BOUNCE = 4;

        static Motion of(int motion) {
            if (motion == StashJarBlockEntity.INSERTED) {
                return new Motion(1.6F, 8.0F, 1, false);
            }
            StashNose nose = StashNose.values()[Math.floorMod(motion, StashNose.values().length)];
            return switch (nose) {
                case EPIC -> new Motion(4.5F, 24.0F, 9, false);
                case STRONG -> new Motion(3.4F, 18.0F, 6, false);
                case DECENT -> new Motion(2.8F, 14.0F, 4, false);
                case SOFT -> new Motion(2.2F, 10.0F, 3, false);
                case STALE -> new Motion(1.6F, 6.0F, 2, false);
                case ROTTEN -> new Motion(1.8F, 5.0F, 8, true);
            };
        }

        int length() {
            return RISE + hang + DROP + BOUNCE;
        }

        /** 0 at rest, 1 fully up, at {@code t} ticks since the jar was opened. */
        float up(float t) {
            if (t < 0.0F || t >= length()) {
                return 0.0F;
            }
            if (t < RISE) {
                float x = t / RISE;
                return 1.0F - (1.0F - x) * (1.0F - x);
            }
            if (t < RISE + hang) {
                // A breath while it hangs, back to 1 when it drops.
                return 1.0F + 0.06F * Mth.sin((t - RISE) / hang * Mth.TWO_PI);
            }
            if (t < RISE + hang + DROP) {
                float x = (t - RISE - hang) / DROP;
                return 1.0F - x * x;
            }
            float x = (t - RISE - hang - DROP) / BOUNCE;
            return 0.16F * Mth.sin(x * Mth.PI) * (1.0F - x);
        }
    }
}
