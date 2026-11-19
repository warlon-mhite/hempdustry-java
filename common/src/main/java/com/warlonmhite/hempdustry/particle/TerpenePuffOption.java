package com.warlonmhite.hempdustry.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.warlonmhite.hempdustry.block.custom.stash.StashNose;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * {@code hempdustry:terpene_puff}: the puff a Stash Jar lets off when an empty hand opens it, sent by
 * the server to every player near enough to see particles, the opener included. One particle on the
 * wire, an emitter that plays the whole puff on each client: the jar's {@link StashNose} picks the
 * choreography, {@code colour} (RGB) is the smoke colour of what came out.
 */
public record TerpenePuffOption(int colour, StashNose nose) implements ParticleOptions {
    private static final Codec<StashNose> NOSE_CODEC = Codec.STRING.xmap(
            name -> {
                for (StashNose nose : StashNose.values()) {
                    if (nose.name().equalsIgnoreCase(name)) {
                        return nose;
                    }
                }
                return StashNose.STALE;
            },
            nose -> nose.name().toLowerCase(java.util.Locale.ROOT));

    public static final MapCodec<TerpenePuffOption> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("color").forGetter(TerpenePuffOption::colour),
            NOSE_CODEC.fieldOf("nose").forGetter(TerpenePuffOption::nose)
    ).apply(instance, TerpenePuffOption::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerpenePuffOption> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, TerpenePuffOption::colour,
            StashNose.STREAM_CODEC, TerpenePuffOption::nose,
            TerpenePuffOption::new);

    @Override
    public ParticleType<?> getType() {
        return ModParticles.TERPENE_PUFF;
    }
}
