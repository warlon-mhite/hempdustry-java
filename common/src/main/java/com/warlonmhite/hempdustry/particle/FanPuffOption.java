package com.warlonmhite.hempdustry.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * {@code hempdustry:fan_puff}: a faint puff of air off a running Grow Fan's grille, drifting down the lane the fan
 * blows on. It leaves the grille {@code blows}-ward and runs straight out of the fan's own block ({@code clear}
 * blocks), spreads {@code across} blocks to the side over the next block ({@link #SPREAD}, the lane's first; positive
 * is clockwise of {@code blows}, seen from above), then goes straight on and is gone {@code reach} blocks ahead of
 * where it started, at the lane's far edge. Spawned on the client by the fan itself ({@code GrowFanBlock#animateTick});
 * the path is here, in common code, so the lane it draws can be held to the lane the wind reaches.
 */
public record FanPuffOption(Direction blows, float clear, float across, float reach) implements ParticleOptions {
    /** How far a puff goes, once clear of the fan's block, to spread to its side: the lane's first block. */
    public static final double SPREAD = 1.0;

    private static final Codec<Direction> HORIZONTAL = Direction.CODEC.validate(direction ->
            direction.getAxis().isHorizontal()
                    ? DataResult.success(direction)
                    : DataResult.error(() -> "a fan blows level, not " + direction.getSerializedName()));

    public static final MapCodec<FanPuffOption> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            HORIZONTAL.fieldOf("blows").forGetter(FanPuffOption::blows),
            Codec.floatRange(0.0F, 1.0F).fieldOf("clear").forGetter(FanPuffOption::clear),
            Codec.floatRange(-16.0F, 16.0F).fieldOf("across").forGetter(FanPuffOption::across),
            Codec.floatRange(0.0F, 32.0F).fieldOf("reach").forGetter(FanPuffOption::reach)
    ).apply(instance, FanPuffOption::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FanPuffOption> STREAM_CODEC = StreamCodec.composite(
            Direction.STREAM_CODEC, FanPuffOption::blows,
            ByteBufCodecs.FLOAT, FanPuffOption::clear,
            ByteBufCodecs.FLOAT, FanPuffOption::across,
            ByteBufCodecs.FLOAT, FanPuffOption::reach,
            FanPuffOption::new);

    /**
     * Where the puff is, from where it started, once it has gone {@code travelled} blocks ahead: straight out of the
     * fan's block, then out to its side fast, easing into a straight line, as air leaving a fan widens and runs on.
     */
    public Vec3 offset(double travelled) {
        double ahead = Math.clamp(travelled, 0.0, this.reach);
        double spread = Math.clamp((ahead - this.clear) / SPREAD, 0.0, 1.0);
        double side = this.across * spread * (2.0 - spread);
        // Clockwise of the way it blows, seen from above: east of north, south of east. Written out rather than
        // asked of getClockWise(), which throws on a vertical direction a stream could still carry.
        return new Vec3(this.blows.getStepX() * ahead - this.blows.getStepZ() * side, 0.0,
                this.blows.getStepZ() * ahead + this.blows.getStepX() * side);
    }

    @Override
    public ParticleType<?> getType() {
        return ModParticles.FAN_PUFF;
    }
}
