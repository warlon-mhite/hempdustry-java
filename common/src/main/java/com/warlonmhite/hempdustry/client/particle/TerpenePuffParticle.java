package com.warlonmhite.hempdustry.client.particle;

import com.warlonmhite.hempdustry.block.custom.stash.StashNose;
import com.warlonmhite.hempdustry.block.custom.stash.TerpenePuff;
import com.warlonmhite.hempdustry.particle.TerpenePuffOption;
import com.warlonmhite.hempdustry.strain.Terpene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The puff a Stash Jar lets off when an empty hand opens it: an emitter the server sends once, which
 * plays the whole puff over a second or two on each client that sees it, so everyone nearby watches the
 * same jar breathe out. What it plays is the jar's {@link StashNose}:
 *
 * <ul>
 *   <li><b>Soft</b>: one faint wisp off the lid.</li>
 *   <li><b>Decent</b>: three wisps that curl round each other.</li>
 *   <li><b>Strong</b>: six wisps in a slow spiral, higher, a few trichome glints among them.</li>
 *   <li><b>Epic</b>: the lid pops (a ring of breath bursts out sideways), a double helix of aroma climbs
 *       a block over the lid, widening, and lingers, trichomes glint up through it, and the smell hangs as a wide bloom at the
 *       top. The colour deepens with the nose, so the best jar shows its terpene the strongest.</li>
 *   <li><b>Stale</b>: one faint wisp of stale hay that barely rises.</li>
 *   <li><b>Rotten</b> (a stale jar holding schwag): murky clumps burp over the rim and sink, heavy, down
 *       the glass and spread over the floor, and a few flies come buzzing round the lid.</li>
 * </ul>
 *
 * <p>The sprites are one set (particles/terpene_puff.json) read in ranges: the mod's aroma wisp, vanilla's
 * glitter, vanilla's smoke puff from its largest to its smallest (this game has no noxious gas, whose clumps the
 * murk would take), vanilla's smallest dot. Every particle skips the world collision query
 * ({@code hasPhysics} off) and moves by arithmetic alone, like the ripe plant's wisp; the most a puff
 * makes is the Epic one's 43. The emitter goes through vanilla's particle setting like any particle the
 * server sends: none on Minimal, two puffs in three on Decreased.
 */
public class TerpenePuffParticle extends NoRenderParticle {
    // Where each part of particles/terpene_puff.json starts, and how long it is.
    private static final int AROMA = 0;
    private static final int AROMA_FRAMES = 4;
    private static final int GLITTER = 4;
    private static final int GLITTER_FRAMES = 8;
    private static final int GAS = 12;
    private static final int GAS_FRAMES = 8;
    /** The smoke puff shrinks to a dot in its last frames: a reek that lingers keeps to the larger ones. */
    private static final int GAS_CLUMPED = 5;
    private static final int DOT = 20;
    private static final int SPRITES = 21;

    /** The murk a rotten jar breathes out: stale hay gone olive and brown. */
    private static final int[] MURK = {TerpenePuff.STALE_HAY, 0x8C8A3E, 0x7A6A35, 0x6F7432};
    private static final int FLY = 0x17150F;

    private final SpriteSet sprites;
    private final StashNose nose;
    private final int colour;
    /** The block the jar stands in: its floor is where a rotten puff settles. */
    private final double floorY;

    TerpenePuffParticle(ClientLevel level, double x, double y, double z, TerpenePuffOption option, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.nose = option.nose();
        this.colour = option.colour() & 0xFFFFFF;
        this.floorY = Math.floor(y) + 0.03;
        this.lifetime = switch (nose) {
            case EPIC -> 42;
            case STRONG -> 16;
            case DECENT -> 10;
            case ROTTEN -> 14;
            case SOFT, STALE -> 1;
        };
    }

    @Override
    public void tick() {
        switch (nose) {
            case SOFT -> soft();
            case DECENT -> decent();
            case STRONG -> strong();
            case EPIC -> epic();
            case STALE -> stale();
            case ROTTEN -> rotten();
        }
        if (++age >= lifetime) {
            remove();
        }
    }

    /** The colour of the puff at this nose: the smell's colour, mixed deeper the better the jar. */
    private int tint(float share) {
        return Terpene.mix(0xFFFFFF, colour, share);
    }

    private void soft() {
        if (age == 0) {
            wisp(0.0, 0.0, tint(0.35F)).rise(0.009).life(45, 15).size(0.16F).alpha(0.55F).add();
        }
    }

    private void decent() {
        if (age % 4 == 0 && age <= 8) {
            float angle = age / 4 * Mth.TWO_PI / 3;
            wisp(0.0, 0.0, tint(0.55F)).orbit(angle, 0.08, 0.003, 0.09F).rise(0.011).life(55, 15).size(0.18F)
                    .alpha(0.7F).add();
        }
    }

    private void strong() {
        if (age % 2 == 0 && age <= 10) {
            float angle = age / 2 * Mth.TWO_PI / 6;
            wisp(0.0, 0.0, tint(0.75F)).orbit(angle, 0.1, 0.005, 0.1F).rise(0.013).life(65, 15).size(0.2F)
                    .spread(0.8F).alpha(0.8F).add();
        }
        if (age == 6 || age == 10 || age == 14) {
            glint(0.22, tint(0.4F));
        }
    }

    private void epic() {
        if (age == 0) {
            // The lid pops: a ring of breath bursting out sideways, gone in under a second.
            for (int i = 0; i < 12; i++) {
                float angle = i * Mth.TWO_PI / 12 + random.nextFloat() * 0.2F;
                wisp(0.0, 0.0, tint(0.7F)).velocity(Mth.cos(angle) * 0.09, 0.006, Mth.sin(angle) * 0.09)
                        .friction(0.86F).life(18, 6).size(0.14F).spread(1.5F).alpha(0.75F).fadeIn(1).add();
            }
        }
        if (age % 2 == 0 && age <= 18) {
            // The double helix: two strands half a turn apart, each wisp climbing as it circles.
            int i = age / 2;
            float angle = (i % 2) * Mth.PI + i * 0.35F;
            wisp(0.0, 0.0, tint(1.0F)).orbit(angle, 0.12, 0.006, 0.12F).rise(0.012).life(75, 20).size(0.24F)
                    .spread(1.3F).alpha(0.9F).add();
        }
        if (age >= 3 && age <= 40 && age % 2 == 1) {
            glint(0.38, tint(0.45F));
        }
        if (age == 22) {
            // The smell hangs in the air above the jar, wide and slow.
            for (int i = 0; i < 2; i++) {
                double side = (random.nextDouble() - 0.5) * 0.3;
                wisp(side, 0.95 + random.nextDouble() * 0.2, tint(0.9F)).rise(0.002).life(70, 10).size(0.5F)
                        .spread(1.2F).alpha(0.45F).fadeIn(12).add();
            }
        }
    }

    private void stale() {
        if (age == 0) {
            wisp(0.0, 0.0, TerpenePuff.STALE_HAY).rise(0.005).life(28, 8).size(0.13F).alpha(0.4F).add();
        }
    }

    private void rotten() {
        if (age % 2 == 0 && age <= 12) {
            // Heavy clumps burp over the rim and sink down the glass, then spread over the floor.
            float angle = random.nextFloat() * Mth.TWO_PI;
            boolean burp = age == 2;
            new Wisp(this, x + Mth.cos(angle) * 0.16, y - 0.02, z + Mth.sin(angle) * 0.16,
                    MURK[random.nextInt(MURK.length)], GAS, GAS_CLUMPED)
                    .velocity(Mth.cos(angle) * 0.012, burp ? 0.03 : -0.004, Mth.sin(angle) * 0.012)
                    .gravity(0.0018F).friction(0.95F).floor(floorY).life(80, 25).size(0.3F).spread(0.9F)
                    .alpha(0.8F).add();
        }
        if (age == 0 || age == 5 || age == 11) {
            // A fly, drawn to it: loops round the lid, then leaves.
            new Wisp(this, x, y + 0.15, z, FLY, DOT, 1)
                    .buzz(random.nextFloat() * Mth.TWO_PI, 0.22 + random.nextDouble() * 0.12,
                            (random.nextBoolean() ? 1 : -1) * (0.28F + random.nextFloat() * 0.2F))
                    .life(85, 30).size(0.08F).spread(0.0F).alpha(1.0F).fadeIn(2).add();
        }
    }

    private Wisp wisp(double dx, double dy, int rgb) {
        return new Wisp(this, x + dx, y + dy, z, rgb, AROMA, AROMA_FRAMES);
    }

    /** A trichome glint: a small bright sparkle drifting up somewhere over the lid, twinkling. */
    private void glint(double radius, int rgb) {
        double r = Math.sqrt(random.nextDouble()) * radius;
        float angle = random.nextFloat() * Mth.TWO_PI;
        new Wisp(this, x + Mth.cos(angle) * r, y + 0.1 + random.nextDouble() * 0.9, z + Mth.sin(angle) * r,
                rgb, GLITTER, GLITTER_FRAMES)
                .rise(0.008).life(24, 12).size(0.1F + random.nextFloat() * 0.04F).spread(0.0F).alpha(1.0F)
                .fadeIn(2).glow().twinkle().add();
    }

    /** Sprites {@code from} to {@code from + frames} of the puff's set. */
    TextureAtlasSprite sprite(int from, int frames, int age, int lifetime) {
        int frame = frames <= 1 ? 0 : Math.min(frames - 1, age * frames / Math.max(1, lifetime));
        // The set's own lookup by age: frame i of n - 1 is sprite i exactly.
        return sprites.get(from + frame, SPRITES - 1);
    }

    /**
     * One particle of a puff. Every motion is a formula of its age, plus a constant velocity with
     * friction and gravity, so nothing needs the world: no collision query, and a sinking clump stops on
     * the jar's floor by a number, not a block lookup.
     */
    static final class Wisp extends SingleQuadParticle {
        private static final float MAX_FADE_OUT = 0.35F;

        private final TerpenePuffParticle puff;
        private final int firstSprite;
        private final int frames;
        private final double originX;
        private final double originZ;
        private final float swayPhase;
        private float maxAlpha = 0.8F;
        private int fadeIn = 6;
        private float spread = 0.6F;
        private double rise;
        // An orbit round the jar's axis (0 radius: none), or a fly's buzz round it.
        private float angle;
        private double radius;
        private double radiusGrowth;
        private float angularSpeed;
        private boolean buzz;
        private double floor = Double.NEGATIVE_INFINITY;
        private boolean glow;
        private boolean twinkle;

        Wisp(TerpenePuffParticle puff, double x, double y, double z, int rgb, int firstSprite, int frames) {
            super(puff.level, x, y, z, puff.sprite(firstSprite, frames, 0, 1));
            this.puff = puff;
            this.firstSprite = firstSprite;
            this.frames = frames;
            this.originX = puff.x;
            this.originZ = puff.z;
            this.hasPhysics = false;
            this.gravity = 0.0F;
            this.friction = 1.0F;
            this.xd = 0.0;
            this.yd = 0.0;
            this.zd = 0.0;
            this.swayPhase = random.nextFloat() * Mth.TWO_PI;
            this.alpha = 0.0F;
            setColor(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F);
        }

        Wisp velocity(double xd, double yd, double zd) {
            this.xd = xd;
            this.yd = yd;
            this.zd = zd;
            return this;
        }

        Wisp rise(double perTick) {
            this.rise = perTick * (0.85 + random.nextDouble() * 0.3);
            return this;
        }

        Wisp orbit(float angle, double radius, double growth, float speed) {
            this.angle = angle;
            this.radius = radius;
            this.radiusGrowth = growth;
            this.angularSpeed = speed;
            return this;
        }

        Wisp buzz(float angle, double radius, float speed) {
            orbit(angle, radius, 0.0, speed);
            this.buzz = true;
            return this;
        }

        Wisp friction(float friction) {
            this.friction = friction;
            return this;
        }

        Wisp gravity(float gravity) {
            this.gravity = gravity;
            return this;
        }

        Wisp floor(double y) {
            this.floor = y;
            return this;
        }

        Wisp life(int ticks, int jitter) {
            this.lifetime = ticks + random.nextInt(jitter + 1);
            return this;
        }

        Wisp size(float size) {
            this.quadSize = size * (0.9F + random.nextFloat() * 0.2F);
            return this;
        }

        Wisp spread(float spread) {
            this.spread = spread;
            return this;
        }

        Wisp alpha(float alpha) {
            this.maxAlpha = alpha;
            return this;
        }

        Wisp fadeIn(int ticks) {
            this.fadeIn = ticks;
            return this;
        }

        Wisp glow() {
            this.glow = true;
            return this;
        }

        Wisp twinkle() {
            this.twinkle = true;
            return this;
        }

        void add() {
            Minecraft.getInstance().particleEngine.add(this);
        }

        @Override
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            if (age++ >= lifetime) {
                remove();
                return;
            }
            if (radius > 0.0) {
                angle += angularSpeed;
                radius += radiusGrowth;
                double r = radius;
                double bob = 0.0;
                if (buzz) {
                    // A fly's loop is never round: the radius breathes, it dips and it jinks.
                    r *= 1.0 + 0.35 * Mth.sin(age * 0.9F + swayPhase);
                    bob = 0.02 * Mth.sin(age * 1.3F + swayPhase) + (random.nextDouble() - 0.5) * 0.03;
                    if (age > lifetime * 3 / 4) {
                        bob += 0.04; // and off it goes
                        radius += 0.02;
                    }
                }
                x = originX + Mth.cos(angle) * r + (buzz ? (random.nextDouble() - 0.5) * 0.02 : 0.0);
                z = originZ + Mth.sin(angle) * r + (buzz ? (random.nextDouble() - 0.5) * 0.02 : 0.0);
                y += rise + bob;
            } else {
                float sway = swayPhase + age * 0.15F;
                yd -= gravity;
                x += xd + 0.003 * Mth.sin(sway);
                y += yd + rise;
                z += zd + 0.003 * Mth.cos(sway);
                xd *= friction;
                yd *= friction;
                zd *= friction;
                if (y < floor) {
                    // Settled: it creeps outward along the floor instead of falling through.
                    y = floor;
                    yd = 0.0;
                    xd *= 1.08;
                    zd *= 1.08;
                }
            }
            setSprite(puff.sprite(firstSprite, frames, age, lifetime));
            float in = fadeIn <= 0 ? 1.0F : (float) age / fadeIn;
            float out = (lifetime - age) / (MAX_FADE_OUT * lifetime);
            float a = maxAlpha * Mth.clamp(Math.min(in, out), 0.0F, 1.0F);
            if (twinkle) {
                a *= 0.55F + 0.45F * Mth.sin(age * 1.7F + swayPhase);
            }
            alpha = Math.max(0.0F, a);
        }

        @Override
        public float getQuadSize(float partialTick) {
            return quadSize * (1.0F + spread * (age + partialTick) / Math.max(1, lifetime));
        }

        @Override
        protected int getLightColor(float partialTick) {
            return glow ? LightTexture.FULL_BRIGHT : super.getLightColor(partialTick);
        }

        @Override
        protected Layer getLayer() {
            return Layer.TRANSLUCENT;
        }
    }

    public static class Provider implements ParticleProvider<TerpenePuffOption> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(TerpenePuffOption option, ClientLevel level, double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ, RandomSource random) {
            return new TerpenePuffParticle(level, x, y, z, option, sprites);
        }
    }
}
