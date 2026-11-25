package com.warlonmhite.hempdustry.fabric;

import com.warlonmhite.hempdustry.platform.Network;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class FabricNetwork implements Network {
    @Override
    public void sendToTracking(Entity entity, CustomPacketPayload payload, boolean andSelf) {
        // Fabric's lookup leaves a player out of the players tracking it.
        for (ServerPlayer player : PlayerLookup.tracking(entity)) {
            sendTo(player, payload);
        }
        if (andSelf && entity instanceof ServerPlayer self) {
            sendTo(self, payload);
        }
    }

    /**
     * Sends to {@code player} if its client listens on the channel. A player with no connection at all (a bare fake
     * player another mod makes) hears nothing: Fabric's own check reads the connection and would throw.
     */
    @Override
    public void sendTo(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && ServerPlayNetworking.canSend(player, payload.type())) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
