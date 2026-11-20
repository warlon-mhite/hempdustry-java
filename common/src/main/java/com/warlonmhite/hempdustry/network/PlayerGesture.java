package com.warlonmhite.hempdustry.network;

import com.warlonmhite.hempdustry.Hempdustry;
import com.warlonmhite.hempdustry.platform.Services;
import io.netty.buffer.ByteBuf;
import java.util.function.IntFunction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ByIdMap;
import net.minecraft.world.entity.Entity;

/**
 * A moment of a player's body that the game does not send on its own: a cough, a sniff at a jar,
 * a kick at a footbag. A draw needs none of this, since it is a use the game already syncs. Each
 * client that sees the player plays the move on its model; nothing in it changes the world.
 *
 * @param entityId the player (or any humanoid) making it, by its network id
 */
public record PlayerGesture(int entityId, Kind kind) implements CustomPacketPayload {
    public static final Type<PlayerGesture> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "player_gesture"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerGesture> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PlayerGesture::entityId,
            Kind.STREAM_CODEC, PlayerGesture::kind,
            PlayerGesture::new);

    @Override
    public Type<PlayerGesture> type() {
        return TYPE;
    }

    /**
     * Shows {@code kind} on {@code entity} to everyone who sees it. The one making a kick has already
     * started it on their own client, at the click ({@code andSelf} false); a cough and a sniff are
     * the server's to decide, so the one making them hears of it like everyone else.
     */
    public static void send(Entity entity, Kind kind, boolean andSelf) {
        Services.NETWORK.sendToTracking(entity, new PlayerGesture(entity.getId(), kind), andSelf);
    }

    /** The moves, by the order they were added: the wire carries the index, so only add at the end. */
    public enum Kind {
        /** The smoke caught: two short jolts of the head. */
        COUGH,
        /** A jar opened by hand let its puff off: the head dips to it. */
        SNIFF,
        /** A footbag hit: a knee raise in place of the arm's swing. */
        KICK;

        private static final IntFunction<Kind> BY_ID = ByIdMap.continuous(Kind::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        static final StreamCodec<ByteBuf, Kind> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Kind::ordinal);
    }
}
