package com.warlonmhite.hempdustry.neoforge;

import com.warlonmhite.hempdustry.platform.Network;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class NeoForgeNetwork implements Network {
    @Override
    public void sendToTracking(Entity entity, CustomPacketPayload payload, boolean andSelf) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        // NeoForge throws on a payload sent to a connection that never agreed to its channel, optional or not
        // (a game test's player, a fake player): each one is asked first.
        level.getChunkSource().sendToTrackingPlayersFiltered(entity, new ClientboundCustomPayloadPacket(payload),
                player -> hears(player, payload));
        if (andSelf && entity instanceof ServerPlayer self) {
            sendTo(self, payload);
        }
    }

    @Override
    public void sendTo(ServerPlayer player, CustomPacketPayload payload) {
        if (hears(player, payload)) {
            player.connection.send(new ClientboundCustomPayloadPacket(payload));
        }
    }

    private static boolean hears(ServerPlayer player, CustomPacketPayload payload) {
        return player.connection != null && player.connection.hasChannel(payload);
    }
}
