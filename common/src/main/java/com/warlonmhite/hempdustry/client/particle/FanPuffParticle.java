package com.warlonmhite.hempdustry.client.particle;

import com.warlonmhite.hempdustry.particle.FanPuffOption;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * One puff of a running Grow Fan's air ({@link FanPuffOption}): it leaves the grille small, widens out to its
 * side of the lane over the first block, runs straight on at a fan's breeze and thins away over the lane's last
 * block, gone at its far edge. Faint on purpose: it shows where the wind goes, it is not smoke.
 *
 * <p>It follows its path exactly ({@link FanPuffOption#offset}) instead of being pushed and slowed, so where it
 * dies is where the lane ends. No collision of its own: where a wall stops the wind is decided when the puff is made
 * ({@code GrowFanBlock#puff}, by the same rule as the wind's reach), and its path already ends there.
 */
public class FanPuffParticle extends SingleQuadParticle {
    private static final float MAX_ALPHA = 0.55f;
    private static final int FADE_IN_TICKS = 4;
    /** The puff thins away over this much of the end of its path: the lane's last block. */
    private static final double FADE_OUT_BLOCKS = 1.0;
    /** How much wider a puff is at the lane's end than at the grille. */
    private static final float SPREAD = 1.0f;

    private final SpriteSet sprites;
    private final FanPuffOption path;
    private final Vec3 start;
    /** Blocks a tick: about three metres a second, a box fan's breeze. */
    private final double speed;

    FanPuffParticle(ClientLevel world, double x, double y, double z, FanPuffOption path, SpriteSet sprites) {
        super(world, x, y, z, sprites.first());
        this.sprites = sprites;
        this.path = path;
        this.start = new Vec3(x, y, z);
        this.hasPhysics = false;
        this.gravity = 0.0f;
        this.friction = 1.0f;
        this.speed = 0.13 + this.random.nextDouble() * 0.04;
        this.lifetime = Math.max(1, Mth.ceil(path.reach() / this.speed));
        this.quadSize = 0.16f + this.random.nextFloat() * 0.04f;
        this.alpha = 0.0f;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        // Aim this tick's move at the next point of the path; the base tick moves there and ages the puff.
        Vec3 next = this.start.add(this.path.offset((this.age + 1) * this.speed));
        this.xd = next.x - this.x;
        this.yd = next.y - this.y;
        this.zd = next.z - this.z;
        super.tick();
        if (!this.removed) {
            this.setSpriteFromAge(this.sprites);
            float fadeIn = (float) this.age / FADE_IN_TICKS;
            float fadeOut = (float) ((this.path.reach() - this.age * this.speed) / FADE_OUT_BLOCKS);
            this.alpha = MAX_ALPHA * Mth.clamp(Math.min(fadeIn, fadeOut), 0.0f, 1.0f);
        }
    }

    @Override
    public float getQuadSize(float tickDelta) {
        return this.quadSize * (1.0f + SPREAD * Math.min(1.0f, (this.age + tickDelta) / this.lifetime));
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<FanPuffOption> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(FanPuffOption option, ClientLevel world, double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ, RandomSource random) {
            return new FanPuffParticle(world, x, y, z, option, this.sprites);
        }
    }
}
