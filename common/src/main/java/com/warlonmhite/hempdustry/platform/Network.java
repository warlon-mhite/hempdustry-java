package com.warlonmhite.hempdustry.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;

/**
 * What the server tells clients that the game itself does not. Each loader registers the mod's
 * payloads its own way, at its entry points; this is only the sending.
 */
public interface Network {
    /**
     * Sends {@code payload} to every player whose client tracks {@code entity}, and to the entity
     * itself when {@code andSelf} and it is a player. A player whose connection never agreed to the
     * mod's channel (a fake player) is passed over.
     */
    void sendToTracking(Entity entity, CustomPacketPayload payload, boolean andSelf);
}
